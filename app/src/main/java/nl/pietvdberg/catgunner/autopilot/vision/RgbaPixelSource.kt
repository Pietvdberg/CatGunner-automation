package nl.pietvdberg.catgunner.autopilot.vision

import java.nio.ByteBuffer

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

    override fun rgbAt(x: Int, y: Int): Int {
        require(x in 0 until width && y in 0 until height)
        val offset = start + (cropTop + y) * rowStride + (cropLeft + x) * pixelStride
        return ((bytes.get(offset).toInt() and 255) shl 16) or
            ((bytes.get(offset + 1).toInt() and 255) shl 8) or
            (bytes.get(offset + 2).toInt() and 255)
    }
}
