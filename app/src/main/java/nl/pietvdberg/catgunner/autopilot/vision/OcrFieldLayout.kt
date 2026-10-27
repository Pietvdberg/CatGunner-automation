package nl.pietvdberg.catgunner.autopilot.vision

/** Six native-resolution fields, packed without labels, stat values or currency icons. */
class OcrFieldLayout(frameWidth: Int, frameHeight: Int) {
    data class Field(
        val sourceX: Int,
        val sourceY: Int,
        val width: Int,
        val height: Int,
        val atlasX: Int,
        val atlasY: Int
    )

    private val scaleX = frameWidth / 570f
    private val scaleY = frameHeight / 1280f
    private val padding = 16
    private val cellWidth = (90 * scaleX).toInt() + padding * 2
    private val cellHeight = (35 * scaleY).toInt() + padding * 2
    val width = cellWidth * 3
    val height = cellHeight * 2
    val fields: List<Field> = (0..1).flatMap { row ->
        UpgradeKind.entries.map { kind ->
            val left = (if (row == 0) 112 else 91) + kind.ordinal * 169
            val right = (if (row == 0) 198 else 180) + kind.ordinal * 169
            val top = if (row == 0) 1155 else 1217
            val bottom = if (row == 0) 1180 else 1251
            val sourceX = (left * scaleX).toInt()
            val sourceY = (top * scaleY).toInt()
            Field(
                sourceX,
                sourceY,
                (right * scaleX).toInt() - sourceX,
                (bottom * scaleY).toInt() - sourceY,
                kind.ordinal * cellWidth + padding,
                row * cellHeight + padding
            )
        }
    }

    init {
        require(frameWidth > 0 && frameHeight > 0)
    }

    fun colors(pixels: PixelSource): IntArray {
        val colors = IntArray(width * height) { 0xFF493B27.toInt() }
        for (field in fields) {
            for (y in 0 until field.height) {
                for (x in 0 until field.width) {
                    colors[(field.atlasY + y) * width + field.atlasX + x] =
                        (0xFF shl 24) or pixels.rgbAt(field.sourceX + x, field.sourceY + y)
                }
            }
        }
        return colors
    }

    /** Reject padding/merged-field detections; map full token bounds to the original frame. */
    fun token(text: String, left: Int, top: Int, right: Int, bottom: Int): UpgradeOcrToken? {
        if (right <= left || bottom <= top) return null
        val field = fields.singleOrNull {
            left >= it.atlasX &&
                right <= it.atlasX + it.width &&
                top >= it.atlasY &&
                bottom <= it.atlasY + it.height
        } ?: return null
        return UpgradeOcrToken(
            text,
            (field.sourceX + (left + right) / 2f - field.atlasX) / scaleX,
            (field.sourceY + (top + bottom) / 2f - field.atlasY) / scaleY
        )
    }
}
