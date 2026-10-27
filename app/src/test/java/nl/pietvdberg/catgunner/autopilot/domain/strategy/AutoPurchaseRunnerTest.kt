package nl.pietvdberg.catgunner.autopilot.domain.strategy

import nl.pietvdberg.catgunner.autopilot.domain.purchase.CalibrationProfile
import nl.pietvdberg.catgunner.autopilot.domain.purchase.GameReading
import nl.pietvdberg.catgunner.autopilot.domain.purchase.NormalizedPoint
import nl.pietvdberg.catgunner.autopilot.domain.purchase.PurchasePhase
import nl.pietvdberg.catgunner.autopilot.domain.purchase.SinglePurchaseEngine
import nl.pietvdberg.catgunner.autopilot.domain.purchase.UpgradeReading
import nl.pietvdberg.catgunner.autopilot.domain.state.ActionLease
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPurchaseRunnerTest {
    private var now = 1000L
    private val engine = SinglePurchaseEngine { now }
    private val runner = AutoPurchaseRunner(engine) { now }
    private val lease = ActionLease(1, 0)
    private val profile = CalibrationProfile(
        570,
        1280,
        42,
        UpgradeKind.entries.associateWith {
            NormalizedPoint((115f + it.ordinal * 169) / 570, 1198f / 1280)
        }
    )
    private val levels = UpgradeKind.entries.associateWith { 100 }.toMutableMap()
    private val costs = mutableMapOf(
        UpgradeKind.POWER to "10h",
        UpgradeKind.SPEED to "20h",
        UpgradeKind.MONEY to "5h"
    )
    private val tapped = mutableListOf<UpgradeKind>()

    @Test
    fun neverStartsFromObservationsWithoutAnExplicitRunCommand() {
        repeat(5) { frame() }
        assertEquals(AutoRunPhase.IDLE, runner.report.value.phase)
        assertTrue(tapped.isEmpty())
    }

    @Test
    fun capsActualPurchasesAtThreeAndCountsEachVerifiedResultOnce() {
        start(3)
        repeat(3) { completeOne() }
        repeat(10) {
            frame()
            runner.tick(lease)
        }
        assertEquals(3, tapped.size)
        assertEquals(3, runner.report.value.verified)
        assertEquals(AutoRunPhase.COMPLETE, runner.report.value.phase)
    }

    @Test
    fun requiresVerificationBeforeArmingTheNextRequest() {
        start(3)
        frame()
        frame()
        assertEquals(1, tapped.size)
        repeat(3) { frame() }
        assertEquals(1, tapped.size)
        assertEquals(0, runner.report.value.verified)
    }

    @Test
    fun rotationVisitsTheThreeCalibratedControlsInOrder() {
        start(3, PurchaseRule.ROTATE)
        repeat(3) { completeOne() }
        assertEquals(UpgradeKind.entries.toList(), tapped)
    }

    @Test
    fun mixedPriceSuffixesStopWithoutGuessingAnOrderOrTapping() {
        costs[UpgradeKind.SPEED] = "1i"
        start(3)
        frame()
        assertEquals(AutoRunPhase.STOPPED, runner.report.value.phase)
        assertTrue(tapped.isEmpty())
    }

    @Test
    fun anUnconfirmedPurchaseStopsTheWholeRunAndCannotRestartWithoutReview() {
        start(3)
        frame()
        frame()
        engine.unsafe("Game interrupted")
        runner.tick(lease)
        assertEquals(AutoRunPhase.STOPPED, runner.report.value.phase)
        assertEquals(PurchasePhase.UNCONFIRMED, engine.report.value.phase)
        assertFalse(runner.start(3, PurchaseRule.ROTATE, profile, lease))
        repeat(3) { frame() }
        assertEquals(1, tapped.size)
    }

    @Test
    fun changedLeaseCancelsPendingRequestsAndNeverResumesAutomatically() {
        start(3)
        frame()
        runner.tick(ActionLease(1, 1))
        repeat(3) { frame() }
        assertEquals(AutoRunPhase.STOPPED, runner.report.value.phase)
        assertEquals(PurchasePhase.CANCELLED, engine.report.value.phase)
        assertTrue(tapped.isEmpty())
    }

    @Test
    fun switchingAppsBetweenPurchasesEndsTheRun() {
        start(3)
        completeOne()
        runner.foregroundLost()
        repeat(3) { frame() }
        assertEquals(AutoRunPhase.STOPPED, runner.report.value.phase)
        assertEquals(1, tapped.size)
        assertEquals(1, runner.report.value.verified)
    }

    @Test
    fun absenceOfFreshReadingsTimesOutWithoutAnyGesture() {
        start(3)
        now += 30_001
        runner.tick(lease)
        assertEquals(AutoRunPhase.STOPPED, runner.report.value.phase)
        assertTrue(tapped.isEmpty())
    }

    @Test
    fun invalidCapsAndAnExistingSinglePurchaseCannotStartARun() {
        assertFalse(runner.start(0, PurchaseRule.ROTATE, profile, lease))
        assertFalse(runner.start(6, PurchaseRule.ROTATE, profile, lease))
        assertTrue(engine.arm(UpgradeKind.POWER, profile, lease))
        assertFalse(runner.start(3, PurchaseRule.ROTATE, profile, lease))
        assertEquals(PurchasePhase.ARMED, engine.report.value.phase)
    }

    @Test
    fun staleFramesCannotArmTheNextPurchase() {
        start(3)
        now += 2000
        runner.prepare(reading().copy(capturedAt = now - 751), lease)
        assertEquals(PurchasePhase.IDLE, engine.report.value.phase)
        assertEquals(AutoRunPhase.WAITING, runner.report.value.phase)
    }

    @Test
    fun runCannotBeReplacedWhileActiveAndItsRequestsCarryTheRunId() {
        start(3)
        val runId = runner.report.value.id
        assertFalse(runner.start(5, PurchaseRule.ROTATE, profile, lease))
        frame()
        assertEquals(runId, engine.report.value.autoRunId)
        assertEquals(3, runner.report.value.limit)
    }

    @Test
    fun stoppingAfterATapDoesNotReportNoPurchaseOrAllowAnotherStep() {
        start(3)
        frame()
        frame()
        runner.stop("Paused")
        repeat(3) { frame() }
        assertEquals(PurchasePhase.UNCONFIRMED, engine.report.value.phase)
        assertEquals(AutoRunPhase.STOPPED, runner.report.value.phase)
        assertEquals(1, tapped.size)
    }

    @Test
    fun anEarlierManualVerifiedPurchaseDoesNotCountTowardTheNewRun() {
        assertTrue(engine.arm(UpgradeKind.POWER, profile, lease))
        frame()
        frame()
        engine.gestureCompleted(engine.report.value.id, true)
        levels[UpgradeKind.POWER] = levels.getValue(UpgradeKind.POWER) + 1
        frame()
        frame()
        assertEquals(PurchasePhase.VERIFIED, engine.report.value.phase)
        start(3)
        assertEquals(0, runner.report.value.verified)
        repeat(3) { completeOne() }
        assertEquals(3, runner.report.value.verified)
        assertEquals(4, tapped.size)
    }

    @Test
    fun failureBeforeDispatchStopsInsteadOfAutomaticallyRetrying() {
        start(3)
        frame()
        engine.unsafe("Banknote icon changed")
        runner.tick(lease)
        repeat(3) { frame() }
        assertEquals(AutoRunPhase.STOPPED, runner.report.value.phase)
        assertEquals(PurchasePhase.FAILED, engine.report.value.phase)
        assertTrue(tapped.isEmpty())
    }

    @Test
    fun profileMismatchCannotArmAnAutomaticPurchase() {
        start(3)
        now += 250
        runner.prepare(reading().copy(gameVersion = 99), lease)
        assertEquals(AutoRunPhase.STOPPED, runner.report.value.phase)
        assertEquals(PurchasePhase.IDLE, engine.report.value.phase)
        assertTrue(tapped.isEmpty())
    }

    @Test
    fun stopAfterVerificationRetainsTheJustCompletedPurchaseCount() {
        start(3)
        frame()
        frame()
        val selected = engine.report.value.kind!!
        engine.gestureCompleted(engine.report.value.id, true)
        levels[selected] = levels.getValue(selected) + 1
        frame()
        now += 250
        engine.onReading(reading()) { _, _, _ -> error("Verification cannot dispatch") }
        runner.stop("User returned to app")
        assertEquals(1, runner.report.value.verified)
        assertEquals(AutoRunPhase.STOPPED, runner.report.value.phase)
        assertEquals(1, tapped.size)
    }

    @Test
    fun keepsDistinctPurchaseDurationsIncludingSelectionWaitWithoutDoubleCounting() {
        start(3)
        now += 1000
        completeOne()
        now += 2000
        completeOne()
        now += 3000
        completeOne()
        val receipts = runner.report.value.purchases
        assertEquals(3, receipts.size)
        assertEquals(listOf(2000L, 3000L, 4000L), receipts.map { it.timing!!.totalMillis })
        assertEquals(
            listOf(1250L, 2250L, 3250L),
            receipts.map {
                it.timing!!.armedAt - it.timing!!.requestedAt
            }
        )
        repeat(5) { runner.reconcile() }
        assertEquals(receipts, runner.report.value.purchases)
        start(1)
        assertTrue(runner.report.value.purchases.isEmpty())
    }

    @Test
    fun interruptedPurchaseTimingIsRetainedAlongsideEarlierVerifiedPurchases() {
        start(3)
        completeOne()
        frame()
        frame()
        now += 100
        runner.stop("User stopped")
        val receipts = runner.report.value.purchases
        assertEquals(2, receipts.size)
        assertEquals(PurchasePhase.VERIFIED, receipts[0].phase)
        assertEquals(PurchasePhase.UNCONFIRMED, receipts[1].phase)
        assertEquals(now, receipts[1].timing!!.finishedAt)
        runner.stop("Again")
        assertEquals(receipts, runner.report.value.purchases)
    }

    @Test
    fun nextPurchaseTimingIncludesAnyDelayBeforeReconciliation() {
        start(2)
        frame()
        frame()
        val kind = engine.report.value.kind!!
        engine.gestureCompleted(engine.report.value.id, true)
        levels[kind] = levels.getValue(kind) + 1
        frame()
        now = 2000
        engine.onReading(reading()) { _, _, _ -> error("No second tap") }
        now = 2500
        runner.reconcile()
        completeOne()
        val timing = runner.report.value.purchases[1].timing!!
        assertEquals(2000L, timing.requestedAt)
        assertEquals(1500L, timing.totalMillis)
    }

    @Test
    fun verifiedHandoffSeedsOnlyOneObservationAndNeedsANewerFrameForTheNextTap() {
        start(2, PurchaseRule.ROTATE)
        completeOne()
        val handoff = runner.prepareVerifiedHandoff(
            lease,
            UpgradeKind.entries.associateWith {
                true
            }
        )!!
        assertEquals(UpgradeKind.SPEED, engine.report.value.kind)
        engine.onReading(handoff) { _, _, _ -> error("Handoff cannot tap") }
        assertEquals(1, tapped.size)
        engine.onReading(handoff) { _, _, _ -> error("Duplicate cannot tap") }
        assertEquals(1, tapped.size)
        frame()
        assertEquals(2, tapped.size)
        assertEquals(1, runner.report.value.verified)
    }

    @Test
    fun expiredVerificationFrameFallsBackToTwoNewCompleteReadings() {
        start(2)
        completeOne()
        now += 751
        assertEquals(
            null,
            runner.prepareVerifiedHandoff(
                lease,
                UpgradeKind.entries.associateWith { true }
            )
        )
        assertEquals(PurchasePhase.VERIFIED, engine.report.value.phase)
        frame()
        assertEquals(1, tapped.size)
        frame()
        assertEquals(2, tapped.size)
    }

    @Test
    fun aCompletedCapOrAnotherRunCannotReuseThePreviousRunSnapshot() {
        start(1)
        completeOne()
        assertEquals(
            null,
            runner.prepareVerifiedHandoff(
                lease,
                UpgradeKind.entries.associateWith { true }
            )
        )
        assertEquals(1, tapped.size)
        start(2)
        assertEquals(
            null,
            runner.prepareVerifiedHandoff(
                lease,
                UpgradeKind.entries.associateWith { true }
            )
        )
        assertEquals(PurchasePhase.VERIFIED, engine.report.value.phase)
        assertEquals(1, tapped.size)
    }

    @Test
    fun nextTargetBanknoteProofIsStillRequiredForTheHandedOffFrame() {
        start(2, PurchaseRule.ROTATE)
        completeOne()
        val handoff = runner.prepareVerifiedHandoff(
            lease,
            UpgradeKind.entries.associateWith { it != UpgradeKind.SPEED }
        )!!
        engine.onReading(handoff) { _, _, _ -> error("Unverified currency cannot tap") }
        runner.reconcile()
        assertEquals(AutoRunPhase.STOPPED, runner.report.value.phase)
        assertEquals(1, tapped.size)
    }

    @Test
    fun anUnknownScreenNewerThanTheVerifiedCaptureInvalidatesHandoff() {
        start(2)
        completeOne()
        assertEquals(
            null,
            runner.prepareVerifiedHandoff(
                lease,
                UpgradeKind.entries.associateWith { true },
                lastUnknownAt = now
            )
        )
        assertEquals(AutoRunPhase.WAITING, runner.report.value.phase)
        assertEquals(PurchasePhase.VERIFIED, engine.report.value.phase)
        assertEquals(1, tapped.size)
    }

    private fun start(limit: Int, rule: PurchaseRule = PurchaseRule.LOWEST_COMPARABLE_COST) {
        assertTrue(runner.start(limit, rule, profile, lease))
    }

    private fun completeOne() {
        frame()
        frame()
        assertEquals(PurchasePhase.DISPATCHED, engine.report.value.phase)
        val selected = engine.report.value.kind!!
        engine.gestureCompleted(engine.report.value.id, true)
        levels[selected] = levels.getValue(selected) + 1
        frame()
        frame()
        assertEquals(PurchasePhase.VERIFIED, engine.report.value.phase)
    }

    private fun frame() {
        now += 250
        val reading = reading()
        runner.prepare(reading, lease)
        engine.onReading(reading) { id, _, _ ->
            engine.executeGesture(id) {
                tapped += engine.report.value.kind!!
                true
            }
        }
        runner.reconcile()
    }

    private fun reading() = GameReading(
        now,
        570,
        1280,
        42,
        UpgradeKind.entries.associateWith {
            UpgradeReading(levels.getValue(it), costs.getValue(it), true)
        },
        true,
        true
    )
}
