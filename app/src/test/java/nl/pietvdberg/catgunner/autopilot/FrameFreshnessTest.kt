package nl.pietvdberg.catgunner.autopilot

import nl.pietvdberg.catgunner.autopilot.capture.FrameFreshness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameFreshnessTest {
    @Test
    fun convertsARecentMonotonicTimestampToElapsedRealtime() {
        assertEquals(4900L, FrameFreshness.capturedAtMillis(900_000_000, 1_000_000_000, 5000))
    }

    @Test
    fun rejectsStaleMissingAndFutureTimestampEvidence() {
        assertNull(FrameFreshness.capturedAtMillis(1, 1_000_000_000, 5000))
        assertNull(FrameFreshness.capturedAtMillis(0, 1_000_000_000, 5000))
        assertNull(FrameFreshness.capturedAtMillis(1_000_000_001, 1_000_000_000, 5000))
    }
}
