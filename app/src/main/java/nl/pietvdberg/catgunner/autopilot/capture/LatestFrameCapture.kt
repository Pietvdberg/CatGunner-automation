package nl.pietvdberg.catgunner.autopilot.capture

import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.SystemClock
import nl.pietvdberg.catgunner.autopilot.vision.PixelSource
import nl.pietvdberg.catgunner.autopilot.vision.RgbaPixelSource

/** Samples at 4 Hz idle or 10 Hz while purchasing; never retains or saves frames. */
class LatestFrameCapture(
    projection: MediaProjection,
    width: Int,
    height: Int,
    densityDpi: Int,
    handler: Handler,
    shouldAnalyze: () -> Boolean,
    fastSampling: () -> Boolean,
    analyze: (PixelSource, Long) -> Unit,
    onFailure: () -> Unit
) : AutoCloseable {
    private val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
    private var lastSampleAt = -SAMPLE_INTERVAL_MS
    private var display: VirtualDisplay? = null

    init {
        try {
            reader.setOnImageAvailableListener({ source ->
                try {
                    source.acquireLatestImage()?.use { image ->
                        val now = SystemClock.elapsedRealtime()
                        val interval = if (fastSampling()) 100L else SAMPLE_INTERVAL_MS
                        if (shouldAnalyze() && now - lastSampleAt >= interval) {
                            lastSampleAt = now
                            val capturedAt = FrameFreshness.capturedAtMillis(
                                image.timestamp,
                                System.nanoTime(),
                                now
                            )
                            if (capturedAt == null) {
                                onFailure()
                                return@use
                            }
                            val crop = image.cropRect
                            val plane = image.planes[0]
                            analyze(
                                RgbaPixelSource(
                                    crop.width(),
                                    crop.height(),
                                    plane.buffer,
                                    plane.rowStride,
                                    plane.pixelStride,
                                    crop.left,
                                    crop.top
                                ),
                                capturedAt
                            )
                        }
                    }
                } catch (error: RuntimeException) {
                    onFailure()
                }
            }, handler)
            display = projection.createVirtualDisplay(
                "AutoPilotReadOnly",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler
            )
        } catch (error: RuntimeException) {
            reader.close()
            throw error
        }
    }

    override fun close() {
        display?.release()
        display = null
        reader.setOnImageAvailableListener(null, null)
        reader.close()
    }

    companion object {
        private const val SAMPLE_INTERVAL_MS = 250L
    }
}
