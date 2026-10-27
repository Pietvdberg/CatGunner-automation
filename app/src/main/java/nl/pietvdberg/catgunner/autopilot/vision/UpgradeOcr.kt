package nl.pietvdberg.catgunner.autopilot.vision

import android.graphics.Bitmap
import android.os.SystemClock
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Offline, single-flight OCR of the native strip from the phone-validated 0.4.1 build. */
class UpgradeOcr : AutoCloseable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val cache = ExactTextCache()
    private var busy = false
    private var closed = false

    fun sample(
        pixels: PixelSource,
        evidence: UpgradePanelEvidence,
        context: OcrContext,
        capturedAt: Long,
        completed: (UpgradeOcrResult) -> Unit
    ): Boolean {
        if (closed) return false
        val startedAt = SystemClock.elapsedRealtime()
        val textPixels = TextPixels.capture(pixels)
        val reused = cache.observe(textPixels, context, capturedAt)
        if (reused != null) {
            completed(
                reused.withCurrentAppearance(evidence).copy(
                    workMillis = SystemClock.elapsedRealtime() - startedAt
                )
            )
            return true
        }
        if (busy) return false
        busy = true
        val sourceEpoch = cache.epoch
        val frameWidth = pixels.width
        val frameHeight = pixels.height
        val top = (1130L * frameHeight / 1280).toInt()
        val bottom = (1265L * pixels.height / 1280).toInt()
        val colors = pixels.copyArgb(0, top, pixels.width, bottom - top)
        val bitmap = Bitmap.createBitmap(
            colors,
            pixels.width,
            bottom - top,
            Bitmap.Config.ARGB_8888
        )
        try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).addOnCompleteListener { task ->
                try {
                    if (!closed) {
                        val result = if (task.isSuccessful) {
                            val elements = task.result.textBlocks.flatMap { it.lines }
                                .flatMap { it.elements }
                            val tokens = elements.mapNotNull { element ->
                                val bounds = element.boundingBox ?: return@mapNotNull null
                                BoundedOcrToken(
                                    UpgradeOcrToken(
                                        element.text,
                                        bounds.exactCenterX() * 570 / frameWidth,
                                        (top + bounds.exactCenterY()) * 1280 / frameHeight
                                    ),
                                    TextPixels.Region(
                                        bounds.left,
                                        top + bounds.top,
                                        bounds.right,
                                        top + bounds.bottom
                                    )
                                )
                            }
                            val parsed = UpgradeOcrParser.parse(tokens.map { it.token }, evidence)
                            cache.remember(
                                textPixels,
                                context,
                                capturedAt,
                                parsed,
                                sourceEpoch,
                                textPixels.covers(tokens)
                            )
                            parsed
                        } else {
                            val error = task.exception
                            val code = (error as? MlKitException)?.errorCode
                            UpgradeOcrResult(
                                null,
                                "OCR task: ${error?.javaClass?.simpleName}, code=$code"
                            )
                        }
                        val duration = SystemClock.elapsedRealtime() - startedAt
                        completed(
                            result.copy(
                                detail = "OCR work=$duration ms. ${result.detail}",
                                workMillis = duration
                            )
                        )
                    }
                } finally {
                    bitmap.recycle()
                    busy = false
                    if (closed) recognizer.close()
                }
            }
        } catch (error: RuntimeException) {
            bitmap.recycle()
            busy = false
            completed(
                UpgradeOcrResult(
                    null,
                    "OCR startup: ${error.javaClass.simpleName}",
                    SystemClock.elapsedRealtime() - startedAt
                )
            )
        }
        return true
    }

    fun invalidate() {
        cache.invalidate()
    }

    override fun close() {
        closed = true
        cache.invalidate()
        if (!busy) recognizer.close()
    }
}
