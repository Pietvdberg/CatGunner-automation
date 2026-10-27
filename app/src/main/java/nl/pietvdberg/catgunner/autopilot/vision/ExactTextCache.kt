package nl.pietvdberg.catgunner.autopilot.vision

import nl.pietvdberg.catgunner.autopilot.domain.state.ActionLease

/** Captured native RGB values. No hashing, resizing, fuzzy matching or reconstructed digits. */
class TextPixels private constructor(
    val width: Int,
    val height: Int,
    val regions: List<Region>,
    private val values: List<IntArray>
) {
    data class Region(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun contains(left: Int, top: Int, right: Int, bottom: Int): Boolean = right > left &&
            bottom > top &&
            left >= this.left &&
            top >= this.top &&
            right <= this.right &&
            bottom <= this.bottom
    }

    fun sameAs(other: TextPixels): Boolean = width == other.width &&
        height == other.height &&
        regions == other.regions &&
        values.indices.all { values[it].contentEquals(other.values[it]) }

    fun covers(tokens: List<BoundedOcrToken>): Boolean = OcrField.entries.all { field ->
        UpgradeKind.entries.all { kind ->
            val used = tokens.filter { UpgradeOcrParser.belongsTo(it.token, kind, field) }
            val region = regions[field.ordinal * 3 + kind.ordinal]
            used.isNotEmpty() &&
                used.all { item ->
                    val bounds = item.bounds
                    region.contains(bounds.left, bounds.top, bounds.right, bounds.bottom)
                }
        }
    }

    companion object {
        fun capture(pixels: PixelSource): TextPixels {
            val regions = OcrField.entries.flatMap { field ->
                UpgradeKind.entries.map { kind ->
                    val offset = kind.ordinal * 169
                    val left = if (field == OcrField.LEVEL) 118 else 94
                    val right = if (field == OcrField.LEVEL) 198 else 179
                    val top = if (field == OcrField.LEVEL) 1154 else 1219
                    val bottom = if (field == OcrField.LEVEL) 1179 else 1250
                    Region(
                        ((left.toLong() + offset) * pixels.width / 570).toInt(),
                        (top.toLong() * pixels.height / 1280).toInt(),
                        ((right.toLong() + offset) * pixels.width / 570).toInt(),
                        (bottom.toLong() * pixels.height / 1280).toInt()
                    )
                }
            }
            return TextPixels(
                pixels.width,
                pixels.height,
                regions,
                regions.map {
                    pixels.copyArgb(
                        it.left,
                        it.top,
                        it.right - it.left,
                        it.bottom - it.top
                    )
                }
            )
        }
    }
}

data class BoundedOcrToken(val token: UpgradeOcrToken, val bounds: TextPixels.Region)

enum class RecheckStatus {
    EMPTY,
    HIT,
    SEEDED,
    SOURCE_EXPIRED,
    PIXELS_CHANGED,
    SOURCE_UNREADABLE,
    TOKEN_BOUNDS,
    INVALIDATED,
    CONTEXT_CHANGED,
    CHANGED_DURING_OCR
}

enum class OcrField { LEVEL, COST }
data class OcrContext(val lease: ActionLease, val gameVersion: Long)

/** A short-lived, source-bound OCR interpretation, rechecked against each distinct live frame. */
class ExactTextCache {
    private data class Entry(
        val pixels: TextPixels,
        val context: OcrContext,
        val capturedAt: Long,
        val result: UpgradeOcrResult
    )
    private var entry: Entry? = null
    private var latestPixels: TextPixels? = null
    private var latestContext: OcrContext? = null
    var lastStatus = RecheckStatus.EMPTY
        private set
    var epoch: Long = 0
        private set

    fun invalidate() {
        entry = null
        latestPixels = null
        latestContext = null
        epoch++
        lastStatus = RecheckStatus.INVALIDATED
    }

    fun observe(pixels: TextPixels, context: OcrContext, capturedAt: Long): UpgradeOcrResult? {
        if (latestContext != null && latestContext != context) invalidate()
        latestPixels = pixels
        latestContext = context
        val cached = entry ?: run {
            lastStatus = RecheckStatus.EMPTY
            return null
        }
        val rejection = when {
            cached.context != context -> RecheckStatus.CONTEXT_CHANGED
            capturedAt - cached.capturedAt !in 1..2000 -> RecheckStatus.SOURCE_EXPIRED
            !cached.pixels.sameAs(pixels) -> RecheckStatus.PIXELS_CHANGED
            else -> null
        }
        if (rejection != null) {
            entry = null
            lastStatus = rejection
            return null
        }
        lastStatus = RecheckStatus.HIT
        return cached.result.copy(
            detail = "Exact live text pixels match full-strip OCR; source age=" +
                "${capturedAt - cached.capturedAt} ms. ${cached.result.detail}",
            pixelRecheck = true,
            recheckStatus = RecheckStatus.HIT
        )
    }

    fun remember(
        pixels: TextPixels,
        context: OcrContext,
        capturedAt: Long,
        result: UpgradeOcrResult,
        sourceEpoch: Long,
        tokenBoundsCovered: Boolean
    ) {
        val upgrades = result.upgrades
        lastStatus = when {
            sourceEpoch != epoch -> RecheckStatus.INVALIDATED
            upgrades?.keys != UpgradeKind.entries.toSet() -> RecheckStatus.SOURCE_UNREADABLE
            !tokenBoundsCovered -> RecheckStatus.TOKEN_BOUNDS
            latestContext != context -> RecheckStatus.CONTEXT_CHANGED
            latestPixels?.sameAs(pixels) != true -> RecheckStatus.CHANGED_DURING_OCR
            else -> RecheckStatus.SEEDED
        }
        if (lastStatus != RecheckStatus.SEEDED) return
        entry =
            Entry(
                pixels,
                context,
                capturedAt,
                result.copy(upgrades = upgrades!!.toMap(), pixelRecheck = false)
            )
    }
}
