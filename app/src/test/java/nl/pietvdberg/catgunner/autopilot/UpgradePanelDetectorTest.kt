package nl.pietvdberg.catgunner.autopilot

import java.io.File
import nl.pietvdberg.catgunner.autopilot.vision.AvailabilityAppearance
import nl.pietvdberg.catgunner.autopilot.vision.PixelSource
import nl.pietvdberg.catgunner.autopilot.vision.UnknownReason
import nl.pietvdberg.catgunner.autopilot.vision.UpgradePanelDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpgradePanelDetectorTest {
    private val detector = File("src/main/assets/vision/upgrade-labels-v1.csv")
        .bufferedReader().use { UpgradePanelDetector.fromCsv(it.lineSequence()) }

    @Test
    fun firstScreenshotShowsAllThreeAvailable() {
        val result = detector.detect(fixture("available"))
        assertTrue(result.toString(), result.recognized)
        assertEquals(
            List(3) {
                AvailabilityAppearance.AVAILABLE
            },
            result.upgrades.map { it.availability }
        )
    }

    @Test
    fun secondScreenshotShowsOnlyPowerUnavailable() {
        val result = detector.detect(fixture("power-unavailable"))
        assertTrue(result.toString(), result.recognized)
        assertEquals(
            listOf(
                AvailabilityAppearance.UNAVAILABLE,
                AvailabilityAppearance.AVAILABLE,
                AvailabilityAppearance.AVAILABLE
            ),
            result.upgrades.map { it.availability }
        )
    }

    @Test
    fun flatCyanFrameDoesNotMatchUpgradeLabels() {
        assertEquals(UnknownReason.LABEL_MISMATCH, detector.detect(solid(0x88F5DF)).unknownReason)
    }

    @Test
    fun blankFrameIsUnknown() {
        assertFalse(detector.detect(solid(0)).recognized)
    }

    @Test
    fun landscapeAndOtherAspectRatiosAreRejected() {
        for ((width, height) in listOf(1280 to 570, 570 to 1200, 0 to 0)) {
            val source = object : PixelSource {
                override val width = width
                override val height = height
                override fun rgbAt(x: Int, y: Int): Int = error("Must reject before reading pixels")
            }
            assertEquals(UnknownReason.ASPECT_RATIO, detector.detect(source).unknownReason)
        }
    }

    @Test
    fun obscuredButtonMakesTheWholeResultUnknown() {
        val original = fixture("available")
        val obscured = transform(original) { x, y ->
            if (x in 35..195 && y >= 1160) 0x777777 else original.rgbAt(x, y)
        }
        assertEquals(UnknownReason.AMBIGUOUS_APPEARANCE, detector.detect(obscured).unknownReason)
    }

    @Test
    fun redCostOnCyanButtonIsAmbiguous() {
        val original = fixture("available")
        val contradictory = transform(original) { x, y ->
            if (x in 100..152 && y in 1228..1240) 0xFF3030 else original.rgbAt(x, y)
        }
        assertFalse(detector.detect(contradictory).recognized)
    }

    @Test
    fun verticallyShiftedLayoutIsUnknown() {
        val original = fixture("available")
        val shifted = transform(original) { x, y -> original.rgbAt(x, (y - 30).coerceAtLeast(0)) }
        assertFalse(detector.detect(shifted).recognized)
    }

    @Test
    fun proportionalScalingRetainsObservedAppearance() {
        val original = fixture("power-unavailable")
        val scaled = object : PixelSource {
            override val width = original.width * 2
            override val height = original.height * 2
            override fun rgbAt(x: Int, y: Int): Int = original.rgbAt(x / 2, y / 2)
        }
        assertEquals(detector.detect(original), detector.detect(scaled))
    }

    private fun fixture(name: String): PixelSource {
        val bytes = javaClass.getResourceAsStream("/vision/$name.rgb")!!.use { it.readBytes() }
        check(bytes.size == 570 * 150 * 3)
        return object : PixelSource {
            override val width = 570
            override val height = 1280
            override fun rgbAt(x: Int, y: Int): Int {
                require(x in 0 until width && y in 0 until height)
                if (y < 1130) return 0
                val offset = ((y - 1130) * width + x) * 3
                return ((bytes[offset].toInt() and 255) shl 16) or
                    ((bytes[offset + 1].toInt() and 255) shl 8) or
                    (bytes[offset + 2].toInt() and 255)
            }
        }
    }

    private fun solid(rgb: Int): PixelSource = object : PixelSource {
        override val width = 570
        override val height = 1280
        override fun rgbAt(x: Int, y: Int): Int = rgb
    }

    private fun transform(original: PixelSource, pixel: (Int, Int) -> Int): PixelSource =
        object : PixelSource {
            override val width = original.width
            override val height = original.height
            override fun rgbAt(x: Int, y: Int): Int = pixel(x, y)
        }
}
