package nl.pietvdberg.catgunner.autopilot.vision

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import nl.pietvdberg.catgunner.autopilot.domain.purchase.OcrStages

/** One native task at a time. Borrowed pixels are copied before the Image is released. */
class UpgradeOcr : AutoCloseable {
    private val worker = Executors.newSingleThreadExecutor { job ->
        Thread({
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE)
            } catch (_: SecurityException) {
                // Scheduling hints are optional; recognition and all purchase guards still apply.
            }
            job.run()
        }, "autopilot-ocr")
    }
    private val recognizer = TextRecognition.getClient(
        TextRecognizerOptions.Builder().setExecutor(worker).build()
    )
    private val main = Handler(Looper.getMainLooper())
    private val direct = Executor { it.run() }
    private var busy = false
    private var closed = false
    private var resourcesClosed = false
    private var lastStartedAt = -1000L

    fun sample(
        pixels: PixelSource,
        evidence: UpgradePanelEvidence,
        active: Boolean = true,
        completed: (UpgradeOcrResult) -> Unit
    ): Boolean {
        if (busy || closed) return false
        val startedAt = SystemClock.elapsedRealtime()
        if (!active && startedAt - lastStartedAt < 1000) return false
        busy = true
        lastStartedAt = startedAt
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
        val sdkStartedAt = SystemClock.elapsedRealtime()
        try {
            recognizer.process(
                InputImage.fromBitmap(bitmap, 0)
            ).addOnCompleteListener(direct) { task ->
                val sdkFinishedAt = SystemClock.elapsedRealtime()
                val priority = Process.getThreadPriority(Process.myTid())
                val result = try {
                    if (task.isSuccessful) {
                        val tokens = task.result.textBlocks.flatMap { it.lines }
                            .flatMap { it.elements }.mapNotNull { element ->
                                NativeOcrToken.from(element, bitmap, top, frameHeight)
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
                } catch (error: RuntimeException) {
                    UpgradeOcrResult(null, "OCR result: ${error.javaClass.simpleName}")
                }
                val parsedAt = SystemClock.elapsedRealtime()
                main.post {
                    val deliveredAt = SystemClock.elapsedRealtime()
                    try {
                        if (!closed) {
                            val stages = OcrStages(
                                sdkStartedAt - startedAt,
                                sdkFinishedAt - sdkStartedAt,
                                parsedAt - sdkFinishedAt,
                                deliveredAt - parsedAt,
                                priority
                            )
                            completed(
                                result.copy(
                                    detail = "OCR work=${stages.totalMillis} ms. ${result.detail}",
                                    workMillis = stages.totalMillis,
                                    stages = stages
                                )
                            )
                        }
                    } finally {
                        bitmap.recycle()
                        busy = false
                        if (closed) closeResources()
                    }
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

    override fun close() {
        closed = true
        if (!busy) closeResources()
    }

    private fun closeResources() {
        if (resourcesClosed) return
        resourcesClosed = true
        recognizer.close()
        worker.shutdown()
    }
}
