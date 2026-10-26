package nl.pietvdberg.catgunner.autopilot.data

import android.graphics.Bitmap
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.pietvdberg.catgunner.autopilot.vision.PixelSource
import nl.pietvdberg.catgunner.autopilot.vision.UpgradePanelEvidence

/** One bounded, in-memory preview; never used as a current action precondition. */
data class CalibrationPreview(
    val bitmap: Bitmap,
    val nativeWidth: Int,
    val nativeHeight: Int,
    val gameVersion: Long,
    val capturedAt: Long,
    val evidence: UpgradePanelEvidence
)

object PreviewStore {
    private val mutablePreview = MutableStateFlow<CalibrationPreview?>(null)
    val preview = mutablePreview.asStateFlow()

    fun update(pixels: PixelSource, evidence: UpgradePanelEvidence, version: Long) {
        val now = SystemClock.elapsedRealtime()
        if (now - (preview.value?.capturedAt ?: -2000L) < 1500) return
        val width = minOf(570, pixels.width)
        val height = (pixels.height.toLong() * width / pixels.width).toInt()
        if (height !in 1..1600) return
        val colors = IntArray(width * height) { index ->
            val x = (index % width).toLong() * pixels.width / width
            val y = (index / width).toLong() * pixels.height / height
            (0xFF shl 24) or pixels.rgbAt(x.toInt(), y.toInt())
        }
        mutablePreview.value = CalibrationPreview(
            Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888),
            pixels.width,
            pixels.height,
            version,
            now,
            evidence
        )
    }

    fun clear() {
        mutablePreview.value = null
    }
}
