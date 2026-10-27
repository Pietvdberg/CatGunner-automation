package nl.pietvdberg.catgunner.autopilot.vision

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Borrowed RGBA_8888 pixels, valid only while the owning Image is open. */
class RgbaPixelSource(
    override val width: Int,
    override val height: Int,
    buffer: ByteBuffer,
    private val rowStride: Int,
    private val pixelStride: Int,
    private val cropLeft: Int = 0,
    private val cropTop: Int = 0
) : PixelSource {
    private val bytes = buffer.asReadOnlyBuffer()
    private val start = bytes.position()

    init {
        require(width > 0 && height > 0 && cropLeft >= 0 && cropTop >= 0)
        require(pixelStride >= 3)
        require(rowStride.toLong() >= (cropLeft.toLong() + width - 1) * pixelStride + 3)
        val lastByte = start.toLong() + (cropTop.toLong() + height - 1) * rowStride +
            (cropLeft.toLong() + width - 1) * pixelStride + 2
        require(lastByte < bytes.limit())
    }

    override fun copyArgb(left: Int, top: Int, width: Int, height: Int): IntArray {
        require(left >= 0 && top >= 0 && width > 0 && height > 0)
        require(left.toLong() + width <= this.width && top.toLong() + height <= this.height)
        val lastAlpha = start.toLong() + (cropTop.toLong() + top + height - 1) * rowStride +
            (cropLeft.toLong() + left + width - 1) * pixelStride + 3
        if (pixelStride != 4 || rowStride % 4 != 0 || lastAlpha >= bytes.limit()) {
            return super.copyArgb(left, top, width, height)
        }
        val ints = bytes.duplicate().order(ByteOrder.BIG_ENDIAN).asIntBuffer()
        val result = IntArray(width * height)
        for (y in 0 until height) {
            ints.position((cropTop + top + y) * (rowStride / 4) + cropLeft + left)
            ints.get(result, y * width, width)
        }
        for (index in result.indices) result[index] = (0xFF shl 24) or (result[index] ushr 8)
        return result
    }

    override fun rgbAt(x: Int, y: Int): Int {
        require(x in 0 until width && y in 0 until height)
        val offset = start + (cropTop + y) * rowStride + (cropLeft + x) * pixelStride
        return ((bytes.get(offset).toInt() and 255) shl 16) or
            ((bytes.get(offset + 1).toInt() and 255) shl 8) or
            (bytes.get(offset + 2).toInt() and 255)
    }
}
