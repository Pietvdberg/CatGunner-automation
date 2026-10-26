package nl.pietvdberg.catgunner.autopilot.vision

import kotlin.math.abs
import kotlin.math.roundToInt

/** Matches the observed banknote icon, never a generic green region. */
class BanknoteMatcher(lines: Sequence<String>) {
    private val anchors = lines.map { it.split(',').map(String::toInt) }.toList()

    init {
        require(anchors.size >= 30)
        require(
            anchors.all {
                it.size == 5 &&
                    it[0] in 0..569 &&
                    it[1] in 0..1279 &&
                    it.drop(2).all { channel -> channel in 0..255 }
            }
        )
    }

    fun matches(pixels: PixelSource, kind: UpgradeKind): Boolean {
        val offset = kind.ordinal * 169
        return anchors.count { anchor ->
            val x = ((anchor[0] + offset + 0.5) * pixels.width / 570 - 0.5).roundToInt()
            val y = ((anchor[1] + 0.5) * pixels.height / 1280 - 0.5).roundToInt()
            if (x !in 0 until pixels.width || y !in 0 until pixels.height) return@count false
            val rgb = pixels.rgbAt(x, y)
            abs((rgb shr 16 and 255) - anchor[2]) <= 55 &&
                abs((rgb shr 8 and 255) - anchor[3]) <= 55 &&
                abs((rgb and 255) - anchor[4]) <= 55
        }.toDouble() / anchors.size >= 0.9
    }
}
