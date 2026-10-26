package nl.pietvdberg.catgunner.autopilot

import java.io.File
import nl.pietvdberg.catgunner.autopilot.vision.BanknoteMatcher
import nl.pietvdberg.catgunner.autopilot.vision.PixelSource
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BanknoteMatcherTest {
    private val matcher = File("src/main/assets/vision/banknote-v1.csv")
        .bufferedReader().use { BanknoteMatcher(it.lineSequence()) }

    @Test
    fun matchesActualBanknoteIconsForAllThreeControls() {
        val bytes = javaClass.getResourceAsStream("/vision/available.rgb")!!.use { it.readBytes() }
        val source = object : PixelSource {
            override val width = 570
            override val height = 1280
            override fun rgbAt(x: Int, y: Int): Int {
                val offset = ((y - 1130) * width + x) * 3
                return ((bytes[offset].toInt() and 255) shl 16) or
                    ((bytes[offset + 1].toInt() and 255) shl 8) or
                    (bytes[offset + 2].toInt() and 255)
            }
        }
        for (kind in UpgradeKind.entries) assertTrue(kind.name, matcher.matches(source, kind))
    }

    @Test
    fun greenOrBlueColorAloneIsNotABanknote() {
        for (rgb in listOf(0x88F5DF, 0x229933, 0x33AAEE)) {
            val source = object : PixelSource {
                override val width = 570
                override val height = 1280
                override fun rgbAt(x: Int, y: Int): Int = rgb
            }
            for (kind in UpgradeKind.entries) assertFalse(matcher.matches(source, kind))
        }
    }
}
