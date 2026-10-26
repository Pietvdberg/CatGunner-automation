package nl.pietvdberg.catgunner.autopilot

import nl.pietvdberg.catgunner.autopilot.domain.purchase.DispatchBlock
import nl.pietvdberg.catgunner.autopilot.domain.purchase.DispatchGuard
import nl.pietvdberg.catgunner.autopilot.domain.purchase.DispatchSnapshot
import nl.pietvdberg.catgunner.autopilot.vision.AvailabilityAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DispatchGuardTest {
    private val ready =
        DispatchSnapshot(
            requestCurrent = true, calibrationMatches = true, targetMatches = true,
            foregroundVerified = true, banknoteVerified = true,
            observationAgeMillis = 217, visualAgeMillis = 100,
            layoutRecognized = true, availability = AvailabilityAppearance.AVAILABLE
        )

    @Test
    fun freshMatchedEvidencePasses() {
        assertEquals(DispatchBlock.NONE, DispatchGuard.evaluate(ready))
    }

    @Test
    fun staleOcrAndVisualFramesWaitWithoutLooseningLimits() {
        assertEquals(
            DispatchBlock.OCR_TOO_OLD,
            DispatchGuard.evaluate(ready.copy(observationAgeMillis = 751))
        )
        assertEquals(
            DispatchBlock.VISUAL_FRAME_TOO_OLD,
            DispatchGuard.evaluate(ready.copy(visualAgeMillis = 501))
        )
        assertTrue(DispatchBlock.OCR_TOO_OLD.canWaitBeforeTap)
        assertTrue(DispatchBlock.VISUAL_FRAME_TOO_OLD.canWaitBeforeTap)
    }

    @Test
    fun transientLabelMissWaitsButNeverPasses() {
        assertEquals(
            DispatchBlock.LAYOUT_UNKNOWN,
            DispatchGuard.evaluate(ready.copy(layoutRecognized = false))
        )
        assertTrue(DispatchBlock.LAYOUT_UNKNOWN.canWaitBeforeTap)
    }

    @Test
    fun currencyCalibrationForegroundAndAffordabilityChangesStayHardStops() {
        assertEquals(
            DispatchBlock.BANKNOTE_UNVERIFIED,
            DispatchGuard.evaluate(ready.copy(banknoteVerified = false))
        )
        assertEquals(
            DispatchBlock.CALIBRATION_CHANGED,
            DispatchGuard.evaluate(ready.copy(calibrationMatches = false))
        )
        assertEquals(
            DispatchBlock.TARGET_CHANGED,
            DispatchGuard.evaluate(ready.copy(targetMatches = false))
        )
        assertEquals(
            DispatchBlock.FOREGROUND_LOST,
            DispatchGuard.evaluate(ready.copy(foregroundVerified = false))
        )
        assertEquals(
            DispatchBlock.TARGET_UNAVAILABLE,
            DispatchGuard.evaluate(ready.copy(availability = AvailabilityAppearance.UNAVAILABLE))
        )
        assertFalse(DispatchBlock.BANKNOTE_UNVERIFIED.canWaitBeforeTap)
        assertFalse(DispatchBlock.FOREGROUND_LOST.canWaitBeforeTap)
    }

    @Test
    fun replacedRequestsAndFutureTimestampsNeverPass() {
        assertEquals(
            DispatchBlock.REQUEST_CHANGED,
            DispatchGuard.evaluate(ready.copy(requestCurrent = false))
        )
        assertEquals(
            DispatchBlock.INVALID_TIMESTAMP,
            DispatchGuard.evaluate(ready.copy(observationAgeMillis = -1))
        )
    }
}
