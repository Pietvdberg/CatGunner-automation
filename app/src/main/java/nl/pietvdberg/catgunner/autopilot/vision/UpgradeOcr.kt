package nl.pietvdberg.catgunner.autopilot.vision

import android.graphics.Bitmap
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** One bounded in-flight strip. The bundled recognizer operates without network access. */
class UpgradeOcr : AutoCloseable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var busy = false
    private var closed = false

    fun sample(
        pixels: PixelSource,
        evidence: UpgradePanelEvidence,
        completed: (UpgradeOcrResult) -> Unit
    ): Boolean {
        if (busy || closed) return false
        busy = true
        val frameWidth = pixels.width
        val frameHeight = pixels.height
        val top = (1130L * frameHeight / 1280).toInt()
        val bottom = (1265L * pixels.height / 1280).toInt()
        val colors = IntArray(pixels.width * (bottom - top)) { index ->
            (0xFF shl 24) or pixels.rgbAt(index % pixels.width, top + index / pixels.width)
        }
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
                        completed(
                            if (task.isSuccessful) {
                                val tokens = task.result.textBlocks.flatMap { it.lines }
                                    .flatMap { it.elements }.mapNotNull { element ->
                                        val bounds = element.boundingBox ?: return@mapNotNull null
                                        UpgradeOcrToken(
                                            element.text,
                                            bounds.exactCenterX() * 570 / frameWidth,
                                            (top + bounds.exactCenterY()) * 1280 / frameHeight
                                        )
                                    }
                                UpgradeOcrParser.parse(tokens, evidence)
                            } else {
                                val error = task.exception
                                val code = (error as? MlKitException)?.errorCode
                                UpgradeOcrResult(
                                    null,
                                    "OCR task: ${error?.javaClass?.simpleName}, code=$code"
                                )
                            }
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
            completed(UpgradeOcrResult(null, "OCR startup: ${error.javaClass.simpleName}"))
        }
        return true
    }

    override fun close() {
        closed = true
        if (!busy) recognizer.close()
    }
}
