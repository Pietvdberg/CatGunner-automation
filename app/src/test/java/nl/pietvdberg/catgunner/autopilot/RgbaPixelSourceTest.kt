package nl.pietvdberg.catgunner.autopilot

import java.nio.ByteBuffer
import nl.pietvdberg.catgunner.autopilot.vision.RgbaPixelSource
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
