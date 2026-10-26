package nl.pietvdberg.catgunner.autopilot.vision

import kotlin.math.abs
import kotlin.math.roundToInt

/** Pixels are packed as 0xRRGGBB. Implementations must respect image crop and row stride. */
interface PixelSource {
    val width: Int
    val height: Int
    fun rgbAt(x: Int, y: Int): Int
}

enum class UpgradeKind { POWER, SPEED, MONEY }
enum class AvailabilityAppearance { AVAILABLE, UNAVAILABLE, UNKNOWN }
enum class UnknownReason { ASPECT_RATIO, LABEL_MISMATCH, AMBIGUOUS_APPEARANCE }

data class UpgradeAppearance(
    val kind: UpgradeKind,
    val availability: AvailabilityAppearance,
    val labelMatchRatio: Double
)

/** Visual evidence only: neither a calibrated screen classification nor spending permission. */
data class UpgradePanelEvidence(
    val upgrades: List<UpgradeAppearance> = emptyList(),
    val unknownReason: UnknownReason? = null
) {
    val recognized: Boolean
        get() = unknownReason == null &&
            upgrades.size == UpgradeKind.entries.size &&
            upgrades.none { it.availability == AvailabilityAppearance.UNKNOWN }
}

/** Experimental 570x1280 portrait profile observed in the owner's two screenshots. */
class UpgradePanelDetector private constructor(private val anchors: List<LabelAnchor>) {
    fun detect(pixels: PixelSource): UpgradePanelEvidence {
        if (pixels.width <= 0 ||
            pixels.height <= 0 ||
            abs(pixels.width.toDouble() / pixels.height / REFERENCE_ASPECT - 1) > 0.005
        ) {
            return UpgradePanelEvidence(unknownReason = UnknownReason.ASPECT_RATIO)
        }
        val result = UpgradeKind.entries.map { kind ->
            val label = anchors.filter { it.kind == kind }
            val labelRatio = label.count { anchor ->
                val actual = sample(pixels, anchor.x, anchor.y)
                abs(red(actual) - red(anchor.rgb)) <= 65 &&
                    abs(green(actual) - green(anchor.rgb)) <= 65 &&
                    abs(blue(actual) - blue(anchor.rgb)) <= 65
            }.toDouble() / label.size
            val offset = kind.ordinal * 169
            val body = (1188..1212 step 4).flatMap { y ->
                listOf(48, 55, 178, 184).map { x -> sample(pixels, x + offset, y) }
            }
            val cyanRatio = body.count(::isCyan).toDouble() / body.size
            val dimRatio = body.count(::isDim).toDouble() / body.size
            val cost = (1228..1240 step 2).flatMap { y ->
                (101..151 step 2).map { x -> sample(pixels, x + offset, y) }
            }
            val whiteCostRatio = cost.count(::isWhite).toDouble() / cost.size
            val redCostRatio = cost.count(::isRed).toDouble() / cost.size
            val greenEdge = (55..175 step 8).map { sample(pixels, it + offset, 1253) }
            val greenRatio = greenEdge.count(::isGreen).toDouble() / greenEdge.size
            val availability = when {
                labelRatio < 0.85 -> AvailabilityAppearance.UNKNOWN
                cyanRatio >= 0.9 && whiteCostRatio >= 0.08 && redCostRatio < 0.02 ->
                    AvailabilityAppearance.AVAILABLE
                dimRatio >= 0.9 && redCostRatio >= 0.04 && greenRatio >= 0.85 ->
                    AvailabilityAppearance.UNAVAILABLE
                else -> AvailabilityAppearance.UNKNOWN
            }
            UpgradeAppearance(kind, availability, labelRatio)
        }
        val reason = when {
            result.any { it.labelMatchRatio < 0.85 } -> UnknownReason.LABEL_MISMATCH
            result.any { it.availability == AvailabilityAppearance.UNKNOWN } ->
                UnknownReason.AMBIGUOUS_APPEARANCE
            else -> null
        }
        return UpgradePanelEvidence(result, reason)
    }

    private fun sample(pixels: PixelSource, x: Int, y: Int): Int {
        val scaledX = ((x + 0.5) * pixels.width / REFERENCE_WIDTH - 0.5).roundToInt()
        val scaledY = ((y + 0.5) * pixels.height / REFERENCE_HEIGHT - 0.5).roundToInt()
        return pixels.rgbAt(scaledX, scaledY)
    }

    private fun isCyan(rgb: Int): Boolean = red(rgb) in 80..185 &&
        green(rgb) > 190 &&
        blue(rgb) > 170 &&
        green(rgb) - red(rgb) > 45

    private fun isDim(rgb: Int): Boolean = red(rgb) in 40..110 &&
        green(rgb) in 50..130 &&
        blue(rgb) in 30..100

    private fun isWhite(rgb: Int): Boolean = minOf(red(rgb), green(rgb), blue(rgb)) > 180 &&
        maxOf(red(rgb), green(rgb), blue(rgb)) - minOf(red(rgb), green(rgb), blue(rgb)) < 90

    private fun isRed(rgb: Int): Boolean = red(rgb) > 150 &&
        green(rgb) < red(rgb) * 0.6 &&
        blue(rgb) < red(rgb) * 0.7

    private fun isGreen(rgb: Int): Boolean = red(rgb) in 80..200 &&
        green(rgb) > 160 &&
        blue(rgb) < 160 &&
        green(rgb) > red(rgb)

    private data class LabelAnchor(val kind: UpgradeKind, val x: Int, val y: Int, val rgb: Int)

    companion object {
        private const val REFERENCE_WIDTH = 570
        private const val REFERENCE_HEIGHT = 1280
        private const val REFERENCE_ASPECT = REFERENCE_WIDTH.toDouble() / REFERENCE_HEIGHT

        fun fromCsv(lines: Sequence<String>): UpgradePanelDetector {
            val anchors = lines.filter { it.isNotBlank() }.map { line ->
                val fields = line.split(',').map(String::toInt)
                require(fields.size == 6)
                require(fields[0] in UpgradeKind.entries.indices)
                require(fields[1] in 0 until REFERENCE_WIDTH)
                require(fields[2] in 0 until REFERENCE_HEIGHT)
                require(fields.drop(3).all { it in 0..255 })
                LabelAnchor(
                    UpgradeKind.entries[fields[0]],
                    fields[1],
                    fields[2],
                    (fields[3] shl 16) or (fields[4] shl 8) or fields[5]
                )
            }.toList()
            require(UpgradeKind.entries.all { kind -> anchors.count { it.kind == kind } >= 20 })
            return UpgradePanelDetector(anchors)
        }

        private fun red(rgb: Int): Int = rgb shr 16 and 255
        private fun green(rgb: Int): Int = rgb shr 8 and 255
        private fun blue(rgb: Int): Int = rgb and 255
    }
}
