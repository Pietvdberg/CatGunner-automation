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
    private val dispatch: (String, ActionLease, NormalizedPoint) -> Boolean = { id, _, _ ->
        engine.executeGesture(id) {
            dispatches++
            true
        }
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
        now += 8001
        engine.tick()
        assertEquals(PurchasePhase.UNCONFIRMED, engine.report.value.phase)
        repeat(5) { feed() }
        assertEquals(1, dispatches)
    }

    @Test
    fun missingGestureCallbackTimesOut() {
        dispatchOnce()
        now += 2001
        engine.tick()
        assertEquals(PurchasePhase.UNCONFIRMED, engine.report.value.phase)
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
        assertEquals(PurchasePhase.UNCONFIRMED, engine.report.value.phase)
    }

    @Test
    fun multipleLevelIncreaseFailsClosed() {
        dispatchOnce()
        engine.gestureCompleted(engine.report.value.id, true)
        feed(level = 12)
        assertEquals(PurchasePhase.UNCONFIRMED, engine.report.value.phase)
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
        assertEquals(PurchasePhase.UNCONFIRMED, engine.report.value.phase)
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
    fun unknownEvidenceAfterSentTapWaitsWithoutSendingAnotherGesture() {
        dispatchOnce()
        engine.waitForEvidence("Obscured control")
        assertEquals(PurchasePhase.DISPATCHED, engine.report.value.phase)
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

    @Test
    fun timeoutKeepsTheLastReadinessReason() {
        arm()
        engine.waitForEvidence("OCR missing POWER level")
        now += 30_001
        engine.tick()
        assertTrue(engine.report.value.detail.contains("OCR missing POWER level"))
    }

    @Test
    fun badOcrAndLabelAnimationCanRecoverWithinBoundedVerificationWithoutAnotherTap() {
        dispatchOnce()
        engine.gestureCompleted(engine.report.value.id, true)
        engine.waitForEvidence("OCR level Lva68 is ambiguous")
        assertEquals(PurchasePhase.VERIFYING, engine.report.value.phase)
        feed(level = 11)
        engine.waitForEvidence("POWER label mismatch 49%")
        feed(level = 11)
        assertEquals(PurchasePhase.VERIFYING, engine.report.value.phase)
        feed(level = 11)
        assertEquals(PurchasePhase.VERIFIED, engine.report.value.phase)
        assertEquals(1, dispatches)
    }

    @Test
    fun observed866MillisecondOcrDelayCanVerifyButCannotAuthorizeAnotherTap() {
        dispatchOnce()
        engine.gestureCompleted(engine.report.value.id, true)
        val captured = now + 100
        now = captured + 866
        engine.onReading(reading(level = 11).copy(capturedAt = captured), dispatch)
        val secondCapture = now + 100
        now = secondCapture + 866
        engine.onReading(reading(level = 11).copy(capturedAt = secondCapture), dispatch)
        assertEquals(PurchasePhase.VERIFIED, engine.report.value.phase)
        assertEquals(1, dispatches)
        assertEquals(750L, engine.maximumReadingAgeMillis())
    }

    @Test
    fun uncertaintyDoesNotExtendTheEightSecondVerificationDeadline() {
        dispatchOnce()
        engine.gestureCompleted(engine.report.value.id, true)
        repeat(9) {
            now += 1000
            engine.waitForEvidence("Unreadable frame")
        }
        assertEquals(PurchasePhase.UNCONFIRMED, engine.report.value.phase)
        feed(level = 11)
        feed(level = 11)
        assertEquals(PurchasePhase.UNCONFIRMED, engine.report.value.phase)
        assertEquals(1, dispatches)
    }

    @Test
    fun anUnconfirmedTapRequiresReviewBeforeAnyNewTestCanArm() {
        dispatchOnce()
        engine.unsafe("Game left foreground")
        assertEquals(PurchasePhase.UNCONFIRMED, engine.report.value.phase)
        assertTrue(engine.report.value.gestureDispatched)
        assertFalse(engine.arm(UpgradeKind.POWER, profile, lease))
        assertTrue(engine.acknowledgeUnconfirmed())
        assertEquals(PurchasePhase.REVIEWED, engine.report.value.phase)
        assertEquals(1, dispatches)
        assertTrue(engine.arm(UpgradeKind.POWER, profile, lease))
        assertEquals(1, dispatches)
    }

    @Test
    fun cancellationAfterAnAcceptedTapIsNotReportedAsNoPurchase() {
        dispatchOnce()
        engine.cancel("Paused")
        assertEquals(PurchasePhase.UNCONFIRMED, engine.report.value.phase)
        assertTrue(engine.report.value.gestureDispatched)
        assertEquals(1, dispatches)
    }

    @Test
    fun queuedButNotSentRequestsCanBeCancelledWithoutClaimingATap() {
        arm()
        feed()
        now += 250
        engine.onReading(reading()) { _, _, _ -> true }
        engine.cancel("Paused before actual send")
        assertEquals(PurchasePhase.CANCELLED, engine.report.value.phase)
        assertFalse(engine.report.value.gestureDispatched)
        assertFalse(
            engine.executeGesture(engine.report.value.id) {
                dispatches++
                true
            }
        )
        assertEquals(0, dispatches)
    }

    @Test
    fun frameworkRefusalAndFrameworkExceptionHaveDifferentOutcomes() {
        arm()
        feed()
        now += 250
        engine.onReading(reading()) { id, _, _ -> engine.executeGesture(id) { false } }
        assertEquals(PurchasePhase.FAILED, engine.report.value.phase)
        assertFalse(engine.report.value.gestureDispatched)
        arm()
        feed()
        now += 250
        engine.onReading(reading()) { id, _, _ ->
            engine.executeGesture(id) { throw IllegalStateException("Binder failed") }
        }
        assertEquals(PurchasePhase.UNCONFIRMED, engine.report.value.phase)
        assertTrue(engine.report.value.gestureDispatched)
    }

    @Test
    fun onlyOneActualFrameworkCallCanExecuteForARequest() {
        dispatchOnce()
        assertFalse(
            engine.executeGesture(engine.report.value.id) {
                dispatches++
                true
            }
        )
        assertEquals(1, dispatches)
    }

    @Test
    fun onePostObservationIsRetainedButNeverMarkedVerified() {
        dispatchOnce()
        engine.gestureCompleted(engine.report.value.id, true)
        feed(level = 11)
        assertEquals(11, engine.report.value.after?.level)
        assertEquals(1, engine.report.value.verificationSamples)
        assertEquals(PurchasePhase.VERIFYING, engine.report.value.phase)
    }

    @Test
    fun uncertaintyWhileQueuedDefersAndRefusesTheOldAttempt() {
        arm()
        feed()
        now += 250
        engine.onReading(reading()) { _, _, _ -> true }
        engine.waitForEvidence("POWER obscured before send")
        assertEquals(PurchasePhase.ARMED, engine.report.value.phase)
        assertFalse(
            engine.executeGesture(engine.report.value.id) {
                dispatches++
                true
            }
        )
        assertEquals(0, dispatches)
    }

    @Test
    fun preCallbackFrameCannotVerifyEvenWhenItsOcrFinishesLater() {
        dispatchOnce()
        val beforeCompletion = now
        now += 50
        engine.gestureCompleted(engine.report.value.id, true)
        now += 866
        engine.onReading(reading(level = 11).copy(capturedAt = beforeCompletion), dispatch)
        assertEquals(0, engine.report.value.verificationSamples)
        assertEquals(PurchasePhase.VERIFYING, engine.report.value.phase)
        assertEquals(1, dispatches)
    }

    @Test
    fun transientPreSendMissRequiresTwoNewReadingsAndOnlyOneActualGesture() {
        arm()
        feed()
        now += 250
        engine.onReading(reading()) { _, _, _ -> true }
        val id = engine.report.value.id
        val oldAttempt = engine.report.value.dispatchAttempt
        assertTrue(engine.deferQueued(id, oldAttempt, "LAYOUT_UNKNOWN"))
        assertEquals(PurchasePhase.ARMED, engine.report.value.phase)
        feed()
        assertEquals(0, dispatches)
        feed()
        assertEquals(1, dispatches)
        assertTrue(engine.report.value.dispatchAttempt > oldAttempt)
    }

    @Test
    fun lateJobsAndCallbacksFromADeferredAttemptCannotTouchTheNewAttempt() {
        arm()
        feed()
        now += 250
        engine.onReading(reading()) { _, _, _ -> true }
        val id = engine.report.value.id
        val oldAttempt = engine.report.value.dispatchAttempt
        engine.deferQueued(id, oldAttempt, "OCR_TOO_OLD")
        feed()
        now += 250
        engine.onReading(reading()) { _, _, _ -> true }
        val currentAttempt = engine.report.value.dispatchAttempt
        assertFalse(
            engine.executeGesture(id, oldAttempt) {
                dispatches++
                true
            }
        )
        engine.gestureCompleted(id, false, oldAttempt)
        engine.rejectQueued(id, oldAttempt, "Stale task failed")
        assertEquals(PurchasePhase.DISPATCHED, engine.report.value.phase)
        assertTrue(
            engine.executeGesture(id, currentAttempt) {
                dispatches++
                true
            }
        )
        assertEquals(1, dispatches)
    }

    @Test
    fun deferralsCannotExtendTheOriginalReadinessDeadlineOrRepeatAcceptedGestures() {
        arm()
        feed()
        now += 250
        engine.onReading(reading()) { _, _, _ -> true }
        val request = engine.report.value
        engine.deferQueued(request.id, request.dispatchAttempt, "Visual frame stale")
        now = 31_001
        engine.tick()
        assertEquals(PurchasePhase.FAILED, engine.report.value.phase)
        assertEquals(0, dispatches)
        arm()
        feed()
        feed()
        assertFalse(
            engine.deferQueued(
                engine.report.value.id,
                engine.report.value.dispatchAttempt,
                "After send"
            )
        )
        assertEquals(1, dispatches)
    }

    @Test
    fun rejectedPreSendGateRetainsTheSpecificReason() {
        arm()
        feed()
        now += 250
        engine.onReading(reading()) { _, _, _ -> true }
        engine.rejectQueued(
            engine.report.value.id,
            engine.report.value.dispatchAttempt,
            "BANKNOTE_UNVERIFIED"
        )
        assertEquals(PurchasePhase.FAILED, engine.report.value.phase)
        assertTrue(engine.report.value.detail.contains("BANKNOTE_UNVERIFIED"))
        assertFalse(engine.report.value.gestureDispatched)
    }

    @Test
    fun preparationTimeoutDefersOnlyUnsentRequests() {
        arm()
        feed()
        now += 250
        engine.onReading(reading()) { _, _, _ -> true }
        now += 2001
        engine.tick()
        assertEquals(PurchasePhase.ARMED, engine.report.value.phase)
        assertFalse(engine.report.value.gestureDispatched)
        assertEquals(0, dispatches)
    }

    @Test
    fun aQueuedGestureCannotStartBeyondTheOriginalArmDeadline() {
        arm()
        now = 30_500
        engine.onReading(reading()) { _, _, _ -> true }
        now = 30_750
        engine.onReading(reading()) { _, _, _ -> true }
        val request = engine.report.value
        now = 31_001
        assertFalse(
            engine.executeGesture(request.id, request.dispatchAttempt) {
                dispatches++
                true
            }
        )
        assertEquals(PurchasePhase.FAILED, engine.report.value.phase)
        assertEquals(0, dispatches)
    }

    @Test
    fun timingUsesActualCallAndCallbackAndFreezesAtVerification() {
        arm()
        now = 1100
        engine.recordOcr(engine.report.value.id, now, 80, 90, true)
        engine.onReading(reading()) { _, _, _ -> true }
        now = 1300
        engine.onReading(reading()) { _, _, _ -> true }
        now = 1400
        val id = engine.report.value.id
        engine.executeGesture(id) { true }
        now = 1450
        engine.gestureCompleted(id, true)
        now = 1600
        engine.recordOcr(id, now, 120, 130, true)
        feed(level = 11, advance = false)
        now = 1800
        feed(level = 11, advance = false)
        val timing = engine.report.value.timing!!
        assertEquals(1000L, timing.requestedAt)
        assertEquals(1300L, timing.firstQueuedAt)
        assertEquals(1400L, timing.callAt)
        assertEquals(1450L, timing.callbackAt)
        assertEquals(1800L, timing.finishedAt)
        assertEquals(800L, timing.totalMillis)
        assertEquals(80L, timing.beforeOcr.maximumWorkMillis)
        assertEquals(120L, timing.afterOcr.maximumWorkMillis)
        now = 6000
        engine.recordOcr(id, now, 999, 999, false)
        engine.tick()
        assertEquals(timing, engine.report.value.timing)
    }

    @Test
    fun deferralRetainsFirstQueueTimeAndOldCallbacksCannotChangeTiming() {
        arm()
        now = 1100
        engine.onReading(reading()) { _, _, _ -> true }
        now = 1300
        engine.onReading(reading()) { _, _, _ -> true }
        val old = engine.report.value
        now = 1500
        engine.deferQueued(old.id, old.dispatchAttempt, "Stale frame")
        now = 1800
        engine.onReading(reading()) { _, _, _ -> true }
        now = 2100
        engine.onReading(reading()) { _, _, _ -> true }
        engine.gestureCompleted(old.id, true, old.dispatchAttempt)
        assertEquals(null, engine.report.value.timing!!.callbackAt)
        val current = engine.report.value
        now = 2300
        engine.executeGesture(current.id, current.dispatchAttempt) { true }
        assertEquals(1300L, engine.report.value.timing!!.firstQueuedAt)
        assertEquals(2300L, engine.report.value.timing!!.callAt)
        assertEquals(1, engine.report.value.timing!!.deferrals)
    }

    @Test
    fun cancelledAndUnconfirmedTimingHasNoInventedMissingMilestones() {
        arm()
        now = 1500
        engine.cancel("User stopped")
        val noCall = engine.report.value.timing!!
        assertEquals(500L, noCall.totalMillis)
        assertEquals(null, noCall.callAt)
        assertEquals(null, noCall.callbackAt)
        assertTrue(noCall.describe().contains("callback=—"))
        dispatchOnce()
        now += 100
        engine.cancel("Lost foreground after call")
        assertEquals(PurchasePhase.UNCONFIRMED, engine.report.value.phase)
        assertTrue(engine.report.value.timing!!.callAt != null)
        assertEquals(null, engine.report.value.timing!!.callbackAt)
    }

    @Test
    fun unreadableAndStaleOcrAggregatesResetForEachRequest() {
        arm()
        val oldId = engine.report.value.id
        engine.recordOcr(oldId, now - 1, 999, 999, true)
        assertEquals(0L, engine.report.value.timing!!.beforeOcr.samples)
        now += 100
        engine.recordOcr(oldId, now, 800, 850, false)
        engine.recordOcr(oldId, now, 200, 250, true)
        val stats = engine.report.value.timing!!.beforeOcr
        assertEquals(2L, stats.samples)
        assertEquals(1L, stats.readable)
        assertEquals(1L, stats.stale)
        assertEquals(1000L, stats.totalWorkMillis)
        engine.cancel("Stop")
        arm()
        engine.recordOcr(oldId, now, 999, 999, false)
        assertEquals(0L, engine.report.value.timing!!.beforeOcr.samples)
        engine.waitForEvidence("Unrecognized layout")
        assertEquals(1, engine.report.value.timing!!.unknownObservations)
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
