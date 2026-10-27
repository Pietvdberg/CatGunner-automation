package nl.pietvdberg.catgunner.autopilot

import nl.pietvdberg.catgunner.autopilot.vision.AvailabilityAppearance
import nl.pietvdberg.catgunner.autopilot.vision.OcrFieldLayout
import nl.pietvdberg.catgunner.autopilot.vision.PixelSource
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeAppearance
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeKind
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeOcrParser
import nl.pietvdberg.catgunner.autopilot.vision.UpgradePanelEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrFieldLayoutTest {
    @Test
    fun nativePixelCopiesAndTokenCoordinatesRemainCorrectAtEachSupportedScale() {
        for ((width, height) in listOf(570 to 1280, 1080 to 2424, 1440 to 3232)) {
            val source = object : PixelSource {
                override val width = width
                override val height = height
                override fun rgbAt(x: Int, y: Int): Int {
                    assertTrue(x in 0 until width && y in 0 until height)
                    return (x shl 12) or y
                }
            }
            val layout = OcrFieldLayout(width, height)
            val colors = layout.colors(source)
            for (field in layout.fields) {
                for ((x, y) in listOf(0 to 0, field.width - 1 to field.height - 1)) {
                    assertEquals(
                        (0xFF shl 24) or source.rgbAt(field.sourceX + x, field.sourceY + y),
                        colors[(field.atlasY + y) * layout.width + field.atlasX + x]
                    )
                }
                val token = layout.token(
                    "Lv.278",
                    field.atlasX,
                    field.atlasY,
                    field.atlasX + field.width,
                    field.atlasY + field.height
                )!!
                assertEquals((field.sourceX + field.width / 2f) * 570 / width, token.x, 0.001f)
                assertEquals((field.sourceY + field.height / 2f) * 1280 / height, token.y, 0.001f)
            }
        }
    }

    @Test
    fun paddingAndTokensSpanningFieldsAreRejected() {
        val layout = OcrFieldLayout(1080, 2424)
        assertNull(layout.token("123", 0, 0, 10, 10))
        val first = layout.fields.first()
        val second = layout.fields[1]
        assertNull(
            layout.token(
                "Lv.278Lv.278",
                first.atlasX,
                first.atlasY,
                second.atlasX + second.width,
                second.atlasY + second.height
            )
        )
        assertNull(layout.token("", first.atlasX, first.atlasY, first.atlasX, first.atlasY))
    }

    @Test
    fun allSixFieldsReachTheirOriginalStrictParserRegions() {
        val layout = OcrFieldLayout(1080, 2424)
        val tokens = layout.fields.mapIndexed { index, field ->
            layout.token(
                if (index < 3) "Lv.278" else "36.2m",
                field.atlasX + 10,
                field.atlasY + 10,
                field.atlasX + field.width - 10,
                field.atlasY + field.height - 10
            )!!
        }
        val evidence = UpgradePanelEvidence(
            UpgradeKind.entries.map { UpgradeAppearance(it, AvailabilityAppearance.AVAILABLE, 1.0) }
        )
        val parsed = UpgradeOcrParser.parse(tokens, evidence)
        assertNotNull(parsed.detail, parsed.upgrades)
        assertTrue(parsed.upgrades!!.values.all { it.level == 278 && it.cost == "36.2m" })
    }

    @Test
    fun pixelBudgetIsLessThanHalfThePreviousNativeStripOnPixel10() {
        val layout = OcrFieldLayout(1080, 2424)
        val previousPixels = 1080 * ((1265L * 2424 / 1280) - (1130L * 2424 / 1280))
        assertTrue(layout.width * layout.height < previousPixels / 2)
    }
}
