package nl.pietvdberg.catgunner.autopilot.vision

import android.graphics.Bitmap
import android.os.SystemClock
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** One bounded in-flight field atlas. The bundled recognizer operates without network access. */
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
        val startedAt = SystemClock.elapsedRealtime()
        val layout = OcrFieldLayout(pixels.width, pixels.height)
        val bitmap = Bitmap.createBitmap(
            layout.colors(pixels),
            layout.width,
            layout.height,
            Bitmap.Config.ARGB_8888
        )
        try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).addOnCompleteListener { task ->
                try {
                    if (!closed) {
                        val result = if (task.isSuccessful) {
                            val tokens = task.result.textBlocks.flatMap { it.lines }
                                .flatMap { it.elements }.mapNotNull { element ->
                                    val bounds = element.boundingBox ?: return@mapNotNull null
                                    layout.token(
                                        element.text,
                                        bounds.left,
                                        bounds.top,
                                        bounds.right,
                                        bounds.bottom
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
                        val duration = SystemClock.elapsedRealtime() - startedAt
                        completed(result.copy(detail = "OCR work=$duration ms. ${result.detail}"))
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
