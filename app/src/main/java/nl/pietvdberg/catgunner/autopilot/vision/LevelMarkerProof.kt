package nl.pietvdberg.catgunner.autopilot.vision

/** Recover only a fixed marker separator; every returned digit must already be in the OCR text. */
object LevelMarkerProof {
    private val candidate =
        Regex("^(Power|Speed|Money)?L[vu][^0-9]?([0-9]{1,6})$", RegexOption.IGNORE_CASE)
    private data class Glyph(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val area: Int
    ) {
        val width get() = right - left + 1
        val height get() = bottom - top + 1
    }

    fun level(
        raw: String,
        width: Int,
        height: Int,
        argb: IntArray,
        kind: UpgradeKind? = null
    ): Int? {
        if (width !in 1..512 || height !in 8..128 || argb.size != width * height) return null
        val match = candidate.matchEntire(raw) ?: return null
        val label = match.groupValues[1]
        if (label.isNotEmpty() && !label.equals(kind?.name, ignoreCase = true)) return null
        val digits = match.groupValues[2]
        val foreground = BooleanArray(argb.size) { i ->
            val rgb = argb[i]
            val r = rgb shr 16 and 255
            val g = rgb shr 8 and 255
            val b = rgb and 255
            minOf(r, g, b) >= 200 && maxOf(r, g, b) - minOf(r, g, b) <= 50
        }
        val queue = IntArray(argb.size)
        val glyphs = mutableListOf<Glyph>()
        for (start in foreground.indices) {
            if (!foreground[start]) continue
            var head = 0
            var tail = 1
            queue[0] = start
            foreground[start] = false
            var left = start % width
            var right = left
            var top = start / width
            var bottom = top
            while (head < tail) {
                val p = queue[head++]
                val x = p % width
                val y = p / width
                left = minOf(left, x)
                right = maxOf(right, x)
                top = minOf(top, y)
                bottom = maxOf(bottom, y)
                for (ny in maxOf(0, y - 1)..minOf(height - 1, y + 1)) {
                    for (nx in maxOf(0, x - 1)..minOf(width - 1, x + 1)) {
                        val next = ny * width + nx
                        if (foreground[next]) {
                            foreground[next] = false
                            queue[tail++] = next
                        }
                    }
                }
            }
            glyphs += Glyph(left, top, right, bottom, tail)
        }
        val tall = glyphs.maxOfOrNull { it.height } ?: return null
        if (tall < 8) return null
        val minimumArea = maxOf(2, tall * tall / 50)
        val filtered = glyphs.filter { it.area >= minimumArea }.toMutableList()
        // Vertical fragments of one glyph may be separated by a JPEG/antialiasing gap.
        var merged = true
        while (merged) {
            merged = false
            outer@ for (i in filtered.indices) {
                for (j in i + 1 until filtered.size) {
                    val a = filtered[i]
                    val b = filtered[j]
                    val overlapsX = maxOf(a.left, b.left) <= minOf(a.right, b.right)
                    val gapY = maxOf(a.top, b.top) - minOf(a.bottom, b.bottom) - 1
                    if (overlapsX && gapY in 0..maxOf(1, tall / 4)) {
                        filtered[i] = Glyph(
                            minOf(a.left, b.left),
                            minOf(a.top, b.top),
                            maxOf(a.right, b.right),
                            maxOf(a.bottom, b.bottom),
                            a.area + b.area
                        )
                        filtered.removeAt(j)
                        merged = true
                        break@outer
                    }
                }
            }
        }
        val ordered = filtered.sortedBy { it.left }
        if (ordered.size != digits.length + 3) return null
        if (ordered.zipWithNext().any { (a, b) -> a.right >= b.left }) return null
        val maxHeight = ordered.maxOf { it.height }
        val dot = ordered[2]
        val numbers = ordered.drop(3)
        if (ordered[0].height < maxHeight * 0.6 || ordered[1].height < maxHeight * 0.4) return null
        if (dot.height > maxHeight * 0.4 || dot.width > maxHeight * 0.5) return null
        if (kotlin.math.abs(dot.bottom - numbers.maxOf { it.bottom }) >
            maxOf(2, maxHeight / 5)
        ) {
            return null
        }
        if (numbers.any {
                it.height < maxHeight * 0.65 ||
                    it.width < maxHeight * 0.16 ||
                    it.width > maxHeight * 0.95 ||
                    it.area < maxHeight * maxHeight * 0.08
            }
        ) {
            return null
        }
        return digits.toIntOrNull()
    }
}
