package nl.pietvdberg.catgunner.autopilot

import java.io.File
import nl.pietvdberg.catgunner.autopilot.vision.LevelMarkerProof
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LevelMarkerProofTest {
    @Test
    fun actualGameGlyphsCanProveADotMisreadAsALetterWithoutChangingDigits() {
        for ((index, raw) in listOf("Lva184", "Lvs185", "Lva186").withIndex()) {
            val (width, height, pixels) = crop(
                "available",
                132 + index * 169,
                1154,
                194 + index * 169,
                1173
            )
            assertEquals(184 + index, LevelMarkerProof.level(raw, width, height, pixels))
        }
    }

    @Test
    fun missingOrAmbiguousDigitsCannotBeReconstructedFromTheMarker() {
        val (width, height, pixels) = crop("available", 132, 1154, 194, 1173)
        for (raw in listOf("Lva84", "Lva68", "LvoI84", "Lva18O", "MoneyLva184")) {
            assertNull(raw, LevelMarkerProof.level(raw, width, height, pixels))
        }
    }

    @Test
    fun aTallGlyphInPlaceOfTheDotCannotBeDiscardedAsPunctuation() {
        val (width, height, pixels) = crop("available", 132, 1154, 194, 1173)
        for (y in 3..13) for (x in 22..24) pixels[y * width + x] = 0xFFFFFFFF.toInt()
        assertNull(LevelMarkerProof.level("Lva184", width, height, pixels))
    }

    @Test
    fun emptyOrInvalidWordPixelsCannotProduceALevel() {
        assertNull(LevelMarkerProof.level("Lva184", 60, 20, IntArray(1200)))
        assertNull(LevelMarkerProof.level("Lva184", 60, 20, IntArray(12)))
    }

    @Test
    fun mergedHeadingRequiresTheMatchingControlAndTheSamePixelProof() {
        val (width, height, pixels) = crop("available", 470, 1154, 532, 1173)
        assertEquals(
            186,
            LevelMarkerProof.level("MoneyLva186", width, height, pixels, UpgradeKind.MONEY)
        )
        assertNull(LevelMarkerProof.level("MoneyLva186", width, height, pixels, UpgradeKind.POWER))
    }

    private fun crop(
        name: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Triple<Int, Int, IntArray> {
        val bytes = File("src/test/resources/vision/$name.rgb").readBytes()
        val width = right - left
        val height = bottom - top
        val colors = IntArray(width * height) { i ->
            val p = ((top + i / width - 1130) * 570 + left + i % width) * 3
            (0xFF shl 24) or ((bytes[p].toInt() and 255) shl 16) or
                ((bytes[p + 1].toInt() and 255) shl 8) or (bytes[p + 2].toInt() and 255)
        }
        return Triple(width, height, colors)
    }
}
