package nl.pietvdberg.catgunner.autopilot.domain.strategy

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.pietvdberg.catgunner.autopilot.domain.purchase.CalibrationProfile
import nl.pietvdberg.catgunner.autopilot.domain.purchase.GameReading
import nl.pietvdberg.catgunner.autopilot.domain.purchase.PurchasePhase
import nl.pietvdberg.catgunner.autopilot.domain.purchase.PurchaseReport
import nl.pietvdberg.catgunner.autopilot.domain.purchase.SinglePurchaseEngine
import nl.pietvdberg.catgunner.autopilot.domain.state.ActionLease
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeKind

enum class AutoRunPhase { IDLE, WAITING, RUNNING, COMPLETE, STOPPED }

data class AutoRunReport(
    val id: String = "",
    val phase: AutoRunPhase = AutoRunPhase.IDLE,
    val limit: Int = 0,
    val verified: Int = 0,
    val rule: PurchaseRule = PurchaseRule.LOWEST_COMPARABLE_COST,
    val detail: String = "Automatic purchasing is off.",
    val selected: UpgradeKind? = null,
    val purchases: List<PurchaseReport> = emptyList()
) {
    val active: Boolean get() = phase == AutoRunPhase.WAITING || phase == AutoRunPhase.RUNNING
}

/** User-started bounded runs; each action reuses the verified one-purchase safety engine. */
class AutoPurchaseRunner(private val engine: SinglePurchaseEngine, private val clock: () -> Long) {
    private val mutableReport = MutableStateFlow(AutoRunReport())
    val report = mutableReport.asStateFlow()
    private var profile: CalibrationProfile? = null
    private var lease: ActionLease? = null
    private var ownedRequest: String? = null
    private var nextIndex = 0
    private var deadline = 0L
    private var notBefore = 0L
    private var sawGame = false
    private var startedAt = 0L
    private var stepRequestedAt = 0L

    @Synchronized
    fun start(
        limit: Int,
        rule: PurchaseRule,
        calibration: CalibrationProfile,
        actionLease: ActionLease
    ): Boolean {
        reconcile()
        if (report.value.active ||
            limit !in 1..5 ||
            !calibration.isValid() ||
            engine.report.value.phase in setOf(
                PurchasePhase.ARMED,
                PurchasePhase.DISPATCHED,
                PurchasePhase.VERIFYING,
                PurchasePhase.UNCONFIRMED
            )
        ) {
            return false
        }
        profile = calibration.copy(targets = calibration.targets.toMap())
        lease = actionLease
        ownedRequest = null
        nextIndex = 0
        sawGame = false
        startedAt = clock()
        stepRequestedAt = startedAt
        notBefore = startedAt
        deadline = startedAt + 30_000
        mutableReport.value = AutoRunReport(
            UUID.randomUUID().toString(),
            AutoRunPhase.WAITING,
            limit,
            0,
            rule,
            "Run started. Waiting for fresh game readings; maximum $limit purchases."
        )
        return true
    }

    /** Called before the one-purchase engine consumes this frame. It never dispatches gestures. */
    @Synchronized
    fun prepare(reading: GameReading, currentLease: ActionLease?) {
        tick(currentLease)
        if (report.value.phase != AutoRunPhase.WAITING) return
        val calibration = profile ?: return
        if (clock() - reading.capturedAt !in 0..750 || reading.capturedAt < notBefore) return
        if (!reading.foregroundVerified ||
            !calibration.matches(reading.width, reading.height, reading.gameVersion)
        ) {
            stop("Foreground, dimensions, or game version changed.")
            return
        }
        sawGame = true
        val choice = PurchaseSelector.choose(reading.upgrades, report.value.rule, nextIndex)
        val selected = choice.kind
        if (selected == null) {
            stop(choice.reason)
            return
        }
        if (!engine.arm(selected, calibration, lease!!, report.value.id, stepRequestedAt)) {
            stop("Single-purchase engine refused the next request. No retry.")
            return
        }
        ownedRequest = engine.report.value.id
        mutableReport.value = report.value.copy(
            phase = AutoRunPhase.RUNNING,
            selected = selected,
            detail = "${report.value.verified}/${report.value.limit} verified. ${choice.reason}"
        )
    }

    @Synchronized
    fun tick(currentLease: ActionLease?) {
        engine.tick()
        reconcile()
        if (!report.value.active) return
        if (currentLease != lease) {
            stop("Capture paused, stopped, or replaced. Start a new run explicitly.")
        } else if (report.value.phase == AutoRunPhase.WAITING && clock() > deadline) {
            stop("No fresh eligible game readings within 30 seconds. No retry.")
        }
    }

    @Synchronized
    fun reconcile() {
        val owned = ownedRequest ?: return
        val purchase = engine.report.value
        if (purchase.id != owned) {
            stop("Purchase request was replaced unexpectedly.")
            return
        }
        when (purchase.phase) {
            PurchasePhase.VERIFIED -> {
                ownedRequest = null
                val count = report.value.verified + 1
                nextIndex = (purchase.kind!!.ordinal + 1) % UpgradeKind.entries.size
                stepRequestedAt = purchase.timing?.finishedAt ?: clock()
                notBefore = clock()
                deadline = clock() + 30_000
                val complete = count >= report.value.limit
                mutableReport.value = report.value.copy(
                    verified = count,
                    purchases = report.value.purchases + purchase,
                    phase = if (complete) AutoRunPhase.COMPLETE else AutoRunPhase.WAITING,
                    detail = if (complete) {
                        "Run complete: $count/${report.value.limit} purchases verified in " +
                            "${(clock() - startedAt) / 1000.0} s (including game opening)."
                    } else {
                        "$count/${report.value.limit} verified. Waiting for the next fresh reading."
                    }
                )
            }
            PurchasePhase.ARMED, PurchasePhase.DISPATCHED, PurchasePhase.VERIFYING -> Unit
            else -> stop(
                "Purchase ended ${purchase.phase}: ${purchase.detail}. Run stopped; no retry."
            )
        }
    }

    @Synchronized
    fun foregroundLost(reason: String = "Game left the foreground. No automatic resume.") {
        if (report.value.active && sawGame) stop(reason)
    }

    @Synchronized
    fun stop(reason: String) {
        if (!report.value.active) return
        if (ownedRequest != null &&
            engine.report.value.id == ownedRequest &&
            engine.report.value.phase == PurchasePhase.VERIFIED
        ) {
            reconcile()
            if (!report.value.active) return
        }
        val owned = ownedRequest
        ownedRequest = null
        if (owned != null && engine.report.value.id == owned) engine.cancel(reason)
        val purchase = engine.report.value
        val purchases = if (owned != null &&
            purchase.id == owned &&
            report.value.purchases.none { it.id == owned }
        ) {
            report.value.purchases + purchase
        } else {
            report.value.purchases
        }
        mutableReport.value = report.value.copy(
            phase = AutoRunPhase.STOPPED,
            detail = reason,
            purchases = purchases
        )
    }
}
