package nl.pietvdberg.catgunner.autopilot.domain.purchase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrStagesTest {
    @Test
    fun completeTimingAccountsForAllFourContiguousStages() {
        val stages = OcrStages(2, 200, 3, 45, -1)
        assertEquals(250L, stages.totalMillis)
    }

    @Test
    fun stageAveragesExcludeMissingProfilesWithoutHidingOverallWorkOrFailures() {
        val summary = OcrTimingSummary()
            .add(8, 10, true, 750, OcrStages(2, 4, 1, 1, -1))
            .add(20, 25, true, 750, OcrStages(3, 10, 2, 5, -1))
            .add(50, 800, false, 750)
        assertEquals(3L, summary.samples)
        assertEquals(2L, summary.profiledSamples)
        assertEquals(78L, summary.totalWorkMillis)
        assertEquals(14L, summary.sdkMillis)
        assertEquals(10L, summary.maximumSdkMillis)
        assertEquals(1L, summary.stale)
        assertEquals(2L, summary.readable)
        assertTrue(summary.describe().contains("stages avg copy/SDK/parse/main=2/7/1/3"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeClockIntervalsCannotBecomeAStageMeasurement() {
        OcrStages(0, -1, 0, 0, null)
    }
}
