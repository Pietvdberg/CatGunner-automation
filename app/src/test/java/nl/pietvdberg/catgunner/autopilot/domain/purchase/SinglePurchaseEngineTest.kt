package nl.pietvdberg.catgunner.autopilot.domain.purchase

import nl.pietvdberg.catgunner.autopilot.domain.state.ActionLease
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SinglePurchaseEngineTest {
    private var now = 1000L
    private val engine = SinglePurchaseEngine { now }
    private val profile = CalibrationProfile(
        570,
        1280,
        42,
        UpgradeKind.entries.associateWith {
            NormalizedPoint((115f + it.ordinal * 169) / 570, 1198f / 1280)
        }
    )
    private var dispatches = 0
    private val lease = ActionLease(1, 0)
    private val dispatch: (String, ActionLease, NormalizedPoint) -> Boolean = { _, _, _ ->
        dispatches++
        true
    }

    @Test
    fun requiresTwoSeparateStableFramesBeforeOnlyOneDispatch() {
        arm()
        feed()
        assertEquals(0, dispatches)
        feed(advance = false)
        assertEquals(0, dispatches)
        feed()
        assertEquals(1, dispatches)
        repeat(10) { feed() }
        assertEquals(1, dispatches)
    }

    @Test
    fun gestureCompletionIsNotPurchaseVerification() {
        dispatchOnce()
        engine.gestureCompleted(engine.report.value.id, true)
        assertEquals(PurchasePhase.VERIFYING, engine.report.value.phase)
        feed(level = 11)
        assertEquals(PurchasePhase.VERIFYING, engine.report.value.phase)
        feed(level = 11)
        assertEquals(PurchasePhase.VERIFIED, engine.report.value.phase)
        assertEquals(10, engine.report.value.before?.level)
        assertEquals(11, engine.report.value.after?.level)
    }

    @Test
    fun unchangedLevelTimesOutWithoutRetry() {
        dispatchOnce()
        engine.gestureCompleted(engine.report.value.id, true)
        feed()
        now += 5001
        engine.tick()
        assertEquals(PurchasePhase.FAILED, engine.report.value.phase)
        repeat(5) { feed() }
        assertEquals(1, dispatches)
    }

    @Test
    fun missingGestureCallbackTimesOut() {
        dispatchOnce()
        now += 2001
        engine.tick()
        assertEquals(PurchasePhase.FAILED, engine.report.value.phase)
    }

    @Test
    fun cancelledRequestCannotDispatchOrBeRevivedByLateCallback() {
        arm()
        feed()
        val id = engine.report.value.id
        engine.cancel("Paused")
        feed()
        engine.gestureCompleted(id, true)
        assertEquals(0, dispatches)
        assertEquals(PurchasePhase.CANCELLED, engine.report.value.phase)
    }

    @Test
    fun blockedCurrencyIconFailsBeforeDispatch() {
        arm()
        feed(banknote = false)
        assertEquals(PurchasePhase.FAILED, engine.report.value.phase)
        assertEquals(0, dispatches)
    }

    @Test
    fun unavailableUpgradeFailsBeforeDispatch() {
        arm()
        feed(available = false)
        assertEquals(PurchasePhase.FAILED, engine.report.value.phase)
        assertEquals(0, dispatches)
    }

    @Test
    fun changedCostBetweenFramesFailsBeforeDispatch() {
        arm()
        feed()
        feed(cost = "99i")
        assertEquals(PurchasePhase.FAILED, engine.report.value.phase)
        assertEquals(0, dispatches)
    }

    @Test
    fun staleOrFutureEvidenceFails() {
        for (offset in listOf(-751L, 1L)) {
            arm()
            engine.onReading(reading().copy(capturedAt = now + offset), dispatch)
            assertEquals(PurchasePhase.FAILED, engine.report.value.phase)
        }
        assertEquals(0, dispatches)
    }

    @Test
    fun mismatchedDimensionsAndGameVersionsFail() {
        arm()
        engine.onReading(reading().copy(width = 1080), dispatch)
        assertEquals(PurchasePhase.FAILED, engine.report.value.phase)
        arm()
        engine.onReading(reading().copy(gameVersion = 43), dispatch)
        assertEquals(PurchasePhase.FAILED, engine.report.value.phase)
        assertEquals(0, dispatches)
    }

    @Test
    fun foreignForegroundAndMissingReadingsFail() {
        arm()
        engine.onReading(reading().copy(foregroundVerified = false), dispatch)
        assertEquals(PurchasePhase.FAILED, engine.report.value.phase)
        arm()
        engine.onReading(reading().copy(upgrades = emptyMap()), dispatch)
        assertEquals(PurchasePhase.FAILED, engine.report.value.phase)
        assertEquals(0, dispatches)
    }

    @Test
    fun dispatchRefusalNeverRetries() {
        arm()
        feed()
        now += 250
        engine.onReading(reading()) { _, _, _ ->
            dispatches++
            false
        }
        feed()
        assertEquals(1, dispatches)
        assertEquals(PurchasePhase.FAILED, engine.report.value.phase)
    }

    @Test
    fun unrelatedLevelChangeCannotVerifyPurchase() {
        dispatchOnce()
        engine.gestureCompleted(engine.report.value.id, true)
        now += 250
        val values = reading(level = 11).upgrades.toMutableMap()
        values[UpgradeKind.MONEY] = values.getValue(UpgradeKind.MONEY).copy(level = 500)
        engine.onReading(reading(level = 11).copy(upgrades = values), dispatch)
        assertEquals(PurchasePhase.FAILED, engine.report.value.phase)
    }

    @Test
    fun multipleLevelIncreaseFailsClosed() {
        dispatchOnce()
        engine.gestureCompleted(engine.report.value.id, true)
        feed(level = 12)
        assertEquals(PurchasePhase.FAILED, engine.report.value.phase)
    }

    @Test
    fun repeatedVerificationFrameDoesNotCountTwice() {
        dispatchOnce()
        engine.gestureCompleted(engine.report.value.id, true)
        feed(level = 11)
        feed(level = 11, advance = false)
        assertEquals(PurchasePhase.VERIFYING, engine.report.value.phase)
    }

    @Test
    fun requestExpiryDoesNotDispatch() {
        arm()
        now += 30_001
        feed()
        assertEquals(PurchasePhase.FAILED, engine.report.value.phase)
        assertEquals(0, dispatches)
    }

    @Test
    fun anActiveTestCannotBeReplacedByAnotherArm() {
        arm()
        assertFalse(engine.arm(UpgradeKind.SPEED, profile, lease))
        assertEquals(UpgradeKind.POWER, engine.report.value.kind)
    }

    @Test
    fun invalidOrIncompleteCalibrationCannotArm() {
        assertFalse(engine.arm(UpgradeKind.POWER, profile.copy(targets = emptyMap()), lease))
        assertFalse(engine.arm(UpgradeKind.POWER, profile.copy(gameVersion = -1), lease))
        val bad = profile.targets.toMutableMap()
        bad[UpgradeKind.POWER] = NormalizedPoint(Float.NaN, 0f)
        assertFalse(engine.arm(UpgradeKind.POWER, profile.copy(targets = bad), lease))
        assertFalse(engine.arm(UpgradeKind.POWER, profile.copy(schema = 2), lease))
    }

    @Test
    fun lateCompletionCannotReviveAnExpiredGestureRequest() {
        dispatchOnce()
        now += 2001
        engine.gestureCompleted(engine.report.value.id, true)
        assertEquals(PurchasePhase.FAILED, engine.report.value.phase)
    }

    @Test
    fun invalidCostAndLevelReadingsFailBeforeDispatch() {
        arm()
        feed(cost = "€2.99")
        assertEquals(PurchasePhase.FAILED, engine.report.value.phase)
        arm()
        feed(level = Int.MAX_VALUE)
        assertEquals(PurchasePhase.FAILED, engine.report.value.phase)
        assertEquals(0, dispatches)
    }

    @Test
    fun startupFramesWaitWithoutAttemptingAGesture() {
        arm()
        engine.waitForEvidence("Game is opening")
        assertEquals(PurchasePhase.ARMED, engine.report.value.phase)
        assertEquals(0, dispatches)
        feed()
        feed()
        assertEquals(1, dispatches)
    }

    @Test
    fun anUnknownFrameClearsPreviousPreconditionSoTwoNewFramesAreRequired() {
        arm()
        feed()
        engine.waitForEvidence("Unknown layout")
        feed()
        assertEquals(0, dispatches)
        feed()
        assertEquals(1, dispatches)
    }

    @Test
    fun waitingDoesNotExtendTheOriginalThirtySecondDeadline() {
        arm()
        repeat(31) {
            now += 1000
            engine.waitForEvidence("Waiting for OCR")
        }
        assertEquals(PurchasePhase.FAILED, engine.report.value.phase)
        assertEquals(0, dispatches)
    }

    @Test
    fun unknownEvidenceAfterDispatchFailsInsteadOfWaitingOrRetrying() {
        dispatchOnce()
        engine.waitForEvidence("Obscured control")
        assertEquals(PurchasePhase.FAILED, engine.report.value.phase)
        feed()
        assertEquals(1, dispatches)
    }

    @Test
    fun waitingNeverRevivesACancelledOrFailedRequest() {
        arm()
        engine.cancel("User paused")
        engine.waitForEvidence("New frame")
        assertEquals(PurchasePhase.CANCELLED, engine.report.value.phase)
        arm()
        engine.unsafe("Disconnected")
        engine.waitForEvidence("Reconnected")
        assertEquals(PurchasePhase.FAILED, engine.report.value.phase)
    }

    private fun arm() {
        assertTrue(engine.arm(UpgradeKind.POWER, profile, lease))
    }

    private fun dispatchOnce() {
        arm()
        feed()
        feed()
    }

    private fun feed(
        level: Int = 10,
        cost: String = "2.62i",
        available: Boolean = true,
        banknote: Boolean = true,
        advance: Boolean = true
    ) {
        if (advance) now += 250
        engine.onReading(
            reading(level, cost, available).copy(banknoteIconsMatched = banknote),
            dispatch
        )
    }

    private fun reading(level: Int = 10, cost: String = "2.62i", available: Boolean = true) =
        GameReading(
            now,
            570,
            1280,
            42,
            UpgradeKind.entries.associateWith {
                UpgradeReading(if (it == UpgradeKind.POWER) level else 100, cost, available)
            },
            true,
            true
        )
}
