package nl.pietvdberg.catgunner.autopilot

import java.nio.ByteBuffer
import nl.pietvdberg.catgunner.autopilot.vision.RgbaPixelSource
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RgbaPixelSourceTest {
    @Test
    fun readsRgbaChannelsWithRowPaddingAndBufferOffsetWithoutMovingPosition() {
        val bytes = ByteBuffer.allocate(28)
        bytes.position(3)
        bytes.put(3, 0x11).put(4, 0x22).put(5, 0x33).put(6, 0xFF.toByte())
        bytes.put(19, 0x55).put(20, 0x66).put(21, 0x77).put(22, 0xFF.toByte())
        val source = RgbaPixelSource(2, 2, bytes, rowStride = 12, pixelStride = 4)
        assertEquals(0x112233, source.rgbAt(0, 0))
        assertEquals(0x556677, source.rgbAt(1, 1))
        assertEquals(3, bytes.position())
    }

    @Test
    fun cropCoordinatesIncludeTopAndLeftOffsets() {
        val bytes = ByteBuffer.allocate(48)
        bytes.put(20, 0x12).put(21, 0x34).put(22, 0x56)
        val source = RgbaPixelSource(2, 2, bytes, 16, 4, cropLeft = 1, cropTop = 1)
        assertEquals(0x123456, source.rgbAt(0, 0))
    }

    @Test
    fun bulkCopyExactlyMatchesCheckedPixelsAcrossStridesOffsetsCropsAndAlpha() {
        for (stride in listOf(3, 4, 8)) {
            for (padding in listOf(0, 2, 4)) {
                val row = 5 * stride + padding
                val bytes = ByteBuffer.allocate(3 + row * 5)
                for (i in 0 until bytes.limit()) bytes.put(i, (i * 37).toByte())
                bytes.position(3)
                val source = RgbaPixelSource(3, 3, bytes, row, stride, 1, 1)
                val expected = IntArray(4) { i ->
                    (0xFF shl 24) or source.rgbAt(1 + i % 2, 1 + i / 2)
                }
                assertArrayEquals(expected, source.copyArgb(1, 1, 2, 2))
                assertEquals(3, bytes.position())
            }
        }
    }

    @Test
    fun bulkCopyHandlesMissingLastAlpha() {
        val bytes = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4, 5, 6, 7))
        val source = RgbaPixelSource(2, 1, bytes, 8, 4)
        assertArrayEquals(
            intArrayOf(0xFF010203.toInt(), 0xFF050607.toInt()),
            source.copyArgb(0, 0, 2, 1)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun bulkCopyRejectsARegionOutsideTheBorrowedImage() {
        RgbaPixelSource(2, 2, ByteBuffer.allocate(16), 8, 4).copyArgb(1, 0, 2, 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun truncatedBufferIsRejectedBeforeReading() {
        RgbaPixelSource(2, 2, ByteBuffer.allocate(7), 8, 4)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidRowStrideIsRejected() {
        RgbaPixelSource(2, 2, ByteBuffer.allocate(20), 4, 4)
    }

    @Test(expected = IllegalArgumentException::class)
    fun pixelsOutsideTheCropAreRejected() {
        RgbaPixelSource(1, 1, ByteBuffer.allocate(4), 4, 4).rgbAt(1, 0)
    }
}
