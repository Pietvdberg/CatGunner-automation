package nl.pietvdberg.catgunner.autopilot.domain.purchase

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.pietvdberg.catgunner.autopilot.domain.safety.PurchaseCurrency
import nl.pietvdberg.catgunner.autopilot.domain.safety.SpendingPolicy
import nl.pietvdberg.catgunner.autopilot.domain.state.ActionLease
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeKind
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeTextParser

enum class PurchasePhase {
    IDLE,
    ARMED,
    DISPATCHED,
    VERIFYING,
    VERIFIED,
    FAILED,
    CANCELLED,
    UNCONFIRMED,
    REVIEWED
}

data class UpgradeReading(val level: Int, val cost: String, val available: Boolean)

data class GameReading(
    val capturedAt: Long,
    val width: Int,
    val height: Int,
    val gameVersion: Long,
    val upgrades: Map<UpgradeKind, UpgradeReading>,
    val banknoteIconsMatched: Boolean,
    val foregroundVerified: Boolean
)

data class PurchaseReport(
    val id: String = "",
    val phase: PurchasePhase = PurchasePhase.IDLE,
    val kind: UpgradeKind? = null,
    val detail: String = "No purchase test armed.",
    val before: UpgradeReading? = null,
    val after: UpgradeReading? = null,
    val calibration: CalibrationProfile? = null,
    val gestureDispatched: Boolean = false,
    val verificationSamples: Int = 0
)

/** A one-use request. No retries, automatic rearming, batch taps, or callback-only success. */
class SinglePurchaseEngine(private val clock: () -> Long) {
    private val mutableReport = MutableStateFlow(PurchaseReport())
    val report = mutableReport.asStateFlow()
    private var profile: CalibrationProfile? = null
    private var lease: ActionLease? = null
    private var deadline = 0L
    private var previous: GameReading? = null
    private var before: GameReading? = null
    private var completedAt = Long.MAX_VALUE
    private var lastVerificationAt = -1L
    private var confirmations = 0

    @Synchronized
    fun arm(kind: UpgradeKind, calibration: CalibrationProfile, actionLease: ActionLease): Boolean {
        if (active() ||
            report.value.phase == PurchasePhase.UNCONFIRMED ||
            !calibration.isValid()
        ) {
            return false
        }
        profile = calibration.copy(targets = calibration.targets.toMap())
        lease = actionLease
        previous = null
        before = null
        confirmations = 0
        completedAt = Long.MAX_VALUE
        lastVerificationAt = -1
        deadline = clock() + 30_000
        mutableReport.value = PurchaseReport(
            UUID.randomUUID().toString(),
            PurchasePhase.ARMED,
            kind,
            "Armed for one tap. Switch to the game within 30 seconds.",
            calibration = profile
        )
        return true
    }

    @Synchronized
    fun cancel(reason: String) {
        if (active()) finish(PurchasePhase.CANCELLED, reason)
    }

    @Synchronized
    fun unsafe(reason: String) {
        if (active()) finish(PurchasePhase.FAILED, reason)
    }

    /** Unknown observations cannot schedule a gesture or extend any deadline. */
    @Synchronized
    fun waitForEvidence(reason: String) {
        tick()
        when {
            report.value.phase == PurchasePhase.ARMED -> {
                previous = null
                mutableReport.value =
                    report.value.copy(detail = "Waiting, no tap attempted: $reason")
            }
            active() && report.value.gestureDispatched -> {
                confirmations = 0
                mutableReport.value = report.value.copy(
                    verificationSamples = 0,
                    detail = "Tap sent; checking its outcome without another tap: $reason"
                )
            }
            else -> unsafe(reason)
        }
    }

    @Synchronized
    fun maximumReadingAgeMillis(): Long =
        if (report.value.phase == PurchasePhase.VERIFYING) 2000 else 750

    /** Must wrap the actual framework call, not the job that queues it. */
    @Synchronized
    fun executeGesture(id: String, send: () -> Boolean): Boolean {
        tick()
        if (id != report.value.id ||
            report.value.phase != PurchasePhase.DISPATCHED ||
            report.value.gestureDispatched
        ) {
            return false
        }
        mutableReport.value = report.value.copy(
            gestureDispatched = true,
            detail = "One tap sent; purchase not yet verified."
        )
        return try {
            val accepted = send()
            if (!accepted) {
                mutableReport.value = report.value.copy(gestureDispatched = false)
                unsafe("Android refused the gesture. No tap accepted and no retry.")
            }
            accepted
        } catch (error: RuntimeException) {
            unsafe("Gesture call interrupted; inspect the game before another test.")
            false
        }
    }

    @Synchronized
    fun acknowledgeUnconfirmed(): Boolean {
        if (report.value.phase != PurchasePhase.UNCONFIRMED) return false
        mutableReport.value = report.value.copy(
            phase = PurchasePhase.REVIEWED,
            detail = "Game reviewed by user. Automatic purchase verification remains unconfirmed."
        )
        return true
    }

    @Synchronized
    fun tick() {
        if (active() && clock() > deadline) {
            val lastStage = report.value.detail.take(400)
            finish(
                PurchasePhase.FAILED,
                "Request timed out. No automatic retry. Last stage: $lastStage"
            )
        }
    }

    @Synchronized
    fun onReading(
        reading: GameReading,
        dispatch: (String, ActionLease, NormalizedPoint) -> Boolean
    ) {
        tick()
        val current = report.value
        if (!active()) return
        val calibration = profile ?: return
        val kind = current.kind ?: return
        if (clock() - reading.capturedAt !in 0..maximumReadingAgeMillis() ||
            !reading.foregroundVerified ||
            !calibration.matches(reading.width, reading.height, reading.gameVersion) ||
            reading.upgrades.keys != UpgradeKind.entries.toSet() ||
            reading.upgrades.values.any {
                it.level !in 0..999999 ||
                    UpgradeTextParser.cost(it.cost) == null
            }
        ) {
            unsafe("Stale, incomplete, or mismatched game evidence.")
            return
        }
        when (current.phase) {
            PurchasePhase.ARMED -> {
                val target = reading.upgrades.getValue(kind)
                if (!reading.banknoteIconsMatched ||
                    !target.available ||
                    !SpendingPolicy.allows(PurchaseCurrency.GREEN_BANKNOTES)
                ) {
                    unsafe("Target is unavailable or its banknote icon is unverified.")
                    return
                }
                val prior = previous
                previous = reading
                if (prior == null) return
                if (reading.capturedAt <= prior.capturedAt) return
                if (reading.capturedAt - prior.capturedAt > 1500 ||
                    prior.upgrades != reading.upgrades
                ) {
                    unsafe("Upgrade preconditions changed before dispatch.")
                    return
                }
                before = reading
                mutableReport.value = current.copy(
                    phase = PurchasePhase.DISPATCHED,
                    before = target,
                    detail = "One gesture request pending; purchase is not verified."
                )
                deadline = clock() + 2000
                if (!dispatch(current.id, lease!!, calibration.targets.getValue(kind))) {
                    unsafe("Gesture refused. No retry.")
                }
            }
            PurchasePhase.VERIFYING -> verify(reading)
            else -> Unit
        }
    }

    @Synchronized
    fun gestureCompleted(id: String, succeeded: Boolean) {
        tick()
        if (id != report.value.id || report.value.phase != PurchasePhase.DISPATCHED) return
        if (!succeeded) {
            unsafe("Gesture cancelled or rejected. No retry.")
            return
        }
        completedAt = clock()
        deadline = completedAt + 8000
        mutableReport.value = report.value.copy(
            phase = PurchasePhase.VERIFYING,
            gestureDispatched = true,
            detail = "Gesture finished. Waiting for two matching level-change observations."
        )
    }

    private fun verify(reading: GameReading) {
        if (reading.capturedAt <= completedAt || reading.capturedAt <= lastVerificationAt) return
        lastVerificationAt = reading.capturedAt
        val kind = report.value.kind!!
        val prior = before ?: return
        val old = prior.upgrades.getValue(kind)
        val next = reading.upgrades.getValue(kind)
        if (UpgradeKind.entries.any {
                it != kind &&
                    reading.upgrades.getValue(it).level != prior.upgrades.getValue(it).level
            } ||
            next.level !in old.level..(old.level + 1)
        ) {
            unsafe("Unexpected level changes. Inspect the game before another test.")
            return
        }
        if (next.level == old.level) {
            confirmations = 0
            mutableReport.value = report.value.copy(after = next, verificationSamples = 0)
            return
        }
        val candidate = report.value.after
        confirmations = if (candidate?.level == next.level && candidate.cost == next.cost) {
            confirmations + 1
        } else {
            1
        }
        mutableReport.value = report.value.copy(
            after = next,
            verificationSamples = confirmations,
            detail = "Tap sent; level ${old.level} → ${next.level} observed $confirmations/2."
        )
        if (confirmations >= 2) {
            mutableReport.value = report.value.copy(after = next)
            finish(PurchasePhase.VERIFIED, "One level increase verified twice. Test complete.")
        }
    }

    private fun active(): Boolean = report.value.phase in setOf(
        PurchasePhase.ARMED,
        PurchasePhase.DISPATCHED,
        PurchasePhase.VERIFYING
    )

    private fun finish(phase: PurchasePhase, detail: String) {
        val unconfirmed = report.value.gestureDispatched &&
            phase in setOf(PurchasePhase.FAILED, PurchasePhase.CANCELLED)
        mutableReport.value = report.value.copy(
            phase = if (unconfirmed) PurchasePhase.UNCONFIRMED else phase,
            detail = if (unconfirmed) {
                "Tap sent; purchase outcome UNCONFIRMED. Inspect the game; no retry. $detail"
            } else {
                detail
            }
        )
        previous = null
    }
}
