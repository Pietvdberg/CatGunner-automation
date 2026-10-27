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
    val verificationSamples: Int = 0,
    val autoRunId: String? = null,
    val dispatchAttempt: Long = 0,
    val timing: PurchaseTiming? = null
)

/** A one-use request. No retries, automatic rearming, batch taps, or callback-only success. */
class SinglePurchaseEngine(private val clock: () -> Long) {
    private val mutableReport = MutableStateFlow(PurchaseReport())
    val report = mutableReport.asStateFlow()
    private var profile: CalibrationProfile? = null
    private var lease: ActionLease? = null
    private var deadline = 0L
    private var armDeadline = 0L
    private var previous: GameReading? = null
    private var before: GameReading? = null
    private var completedAt = Long.MAX_VALUE
    private var lastVerificationAt = -1L
    private var confirmations = 0

    @Synchronized
    fun arm(
        kind: UpgradeKind,
        calibration: CalibrationProfile,
        actionLease: ActionLease,
        autoRunId: String? = null,
        requestedAt: Long? = null
    ): Boolean {
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
        armDeadline = clock() + 30_000
        deadline = armDeadline
        mutableReport.value = PurchaseReport(
            UUID.randomUUID().toString(),
            PurchasePhase.ARMED,
            kind,
            "Armed for one tap. Switch to the game within 30 seconds.",
            calibration = profile,
            autoRunId = autoRunId,
            timing = PurchaseTiming(requestedAt ?: clock(), clock())
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
        if (active()) {
            val timing = report.value.timing!!
            mutableReport.value = report.value.copy(
                timing = timing.copy(unknownObservations = timing.unknownObservations + 1)
            )
        }
        when {
            report.value.phase == PurchasePhase.ARMED -> {
                previous = null
                mutableReport.value =
                    report.value.copy(detail = "Waiting, no tap attempted: $reason")
            }
            report.value.phase == PurchasePhase.DISPATCHED && !report.value.gestureDispatched -> {
                deferQueued(report.value.id, report.value.dispatchAttempt, reason)
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

    /** Attribute only current-request frames; old callbacks cannot contaminate the next purchase. */
    @Synchronized
    fun recordOcr(
        id: String,
        capturedAt: Long,
        workMillis: Long,
        frameAgeMillis: Long,
        readable: Boolean,
        pixelRecheck: Boolean = false
    ) {
        val timing = report.value.timing ?: return
        if (!active() || report.value.id != id || capturedAt < timing.armedAt) return
        val afterCall = timing.callAt?.let { capturedAt >= it } == true
        val summary = if (afterCall) timing.afterOcr else timing.beforeOcr
        val updated = summary.add(
            workMillis,
            frameAgeMillis,
            readable,
            maximumReadingAgeMillis(),
            pixelRecheck
        )
        mutableReport.value = report.value.copy(
            timing = if (afterCall) {
                timing.copy(
                    afterOcr = updated
                )
            } else {
                timing.copy(beforeOcr = updated)
            }
        )
    }

    @Synchronized
    fun maximumReadingAgeMillis(): Long =
        if (report.value.phase == PurchasePhase.VERIFYING) 2000 else 750

    /** Must wrap the actual framework call, not the job that queues it. */
    @Synchronized
    fun executeGesture(
        id: String,
        attempt: Long = report.value.dispatchAttempt,
        send: () -> Boolean
    ): Boolean {
        tick()
        if (id != report.value.id ||
            report.value.phase != PurchasePhase.DISPATCHED ||
            report.value.dispatchAttempt != attempt ||
            report.value.gestureDispatched
        ) {
            return false
        }
        deadline = clock() + 2000
        mutableReport.value = report.value.copy(
            gestureDispatched = true,
            timing = report.value.timing?.copy(callAt = clock()),
            detail = "One tap sent; purchase not yet verified."
        )
        return try {
            val accepted = send()
            if (!accepted) {
                mutableReport.value = report.value.copy(gestureDispatched = false)
                unsafe("Gesture service refused acceptance. No tap sent and no retry.")
            }
            accepted
        } catch (error: RuntimeException) {
            unsafe("Gesture call interrupted; inspect the game before another test.")
            false
        }
    }

    /** Only a still-queued, unsent attempt can wait; its original deadline is retained. */
    @Synchronized
    fun deferQueued(id: String, attempt: Long, reason: String): Boolean {
        if (!isQueued(id, attempt)) return false
        if (clock() > armDeadline) {
            unsafe("No tap sent. Original readiness deadline expired: $reason")
            return false
        }
        previous = null
        deadline = armDeadline
        mutableReport.value = report.value.copy(
            phase = PurchasePhase.ARMED,
            timing = report.value.timing?.let { it.copy(deferrals = it.deferrals + 1) },
            detail = "No tap sent; waiting for two new fresh readings: $reason"
        )
        return true
    }

    @Synchronized
    fun rejectQueued(id: String, attempt: Long, reason: String) {
        if (isQueued(id, attempt)) unsafe("No tap sent: $reason")
    }

    private fun isQueued(id: String, attempt: Long): Boolean = report.value.id == id &&
        report.value.dispatchAttempt == attempt &&
        report.value.phase == PurchasePhase.DISPATCHED &&
        !report.value.gestureDispatched

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
            if (report.value.phase == PurchasePhase.DISPATCHED &&
                !report.value.gestureDispatched &&
                clock() <= armDeadline
            ) {
                deferQueued(report.value.id, report.value.dispatchAttempt, "Preparation timed out.")
                return
            }
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
                    timing = current.timing?.let {
                        it.copy(firstQueuedAt = it.firstQueuedAt ?: clock())
                    },
                    dispatchAttempt = current.dispatchAttempt + 1,
                    detail = "One gesture request pending; purchase is not verified."
                )
                deadline = minOf(clock() + 2000, armDeadline)
                if (!dispatch(current.id, lease!!, calibration.targets.getValue(kind))) {
                    unsafe("Gesture refused. No retry.")
                }
            }
            PurchasePhase.VERIFYING -> verify(reading)
            else -> Unit
        }
    }

    @Synchronized
    fun gestureCompleted(
        id: String,
        succeeded: Boolean,
        attempt: Long = report.value.dispatchAttempt
    ) {
        tick()
        if (id != report.value.id ||
            report.value.phase != PurchasePhase.DISPATCHED ||
            attempt != report.value.dispatchAttempt
        ) {
            return
        }
        mutableReport.value = report.value.copy(
            timing = report.value.timing?.copy(callbackAt = clock())
        )
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
            timing = report.value.timing?.copy(finishedAt = clock()),
            detail = if (unconfirmed) {
                "Tap sent; purchase outcome UNCONFIRMED. Inspect the game; no retry. $detail"
            } else {
                detail
            }
        )
        previous = null
    }
}
