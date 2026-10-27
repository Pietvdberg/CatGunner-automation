package nl.pietvdberg.catgunner.autopilot.probe

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import nl.pietvdberg.catgunner.autopilot.vision.AvailabilityAppearance
import nl.pietvdberg.catgunner.autopilot.vision.NativeOcrToken
import nl.pietvdberg.catgunner.autopilot.vision.PixelSource
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeAppearance
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeKind
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeOcr
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeOcrParser
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeOcrResult
import nl.pietvdberg.catgunner.autopilot.vision.UpgradePanelEvidence
import org.json.JSONArray
import org.json.JSONObject

/** Explicit read-only native SDK probe; packaged only in the test APK, never in the application. */
class OcrProbe : Instrumentation() {
    private var quick = false
    private var quickFixture = "available"

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        quick = arguments?.getString("quick") == "true"
        quickFixture = arguments?.getString("fixture")?.takeIf {
            it in listOf("available", "power-unavailable")
        } ?: "available"
        start()
    }

    override fun onStart() {
        val rows = JSONArray()
        val executor = Executors.newSingleThreadExecutor()
        var recognizer: TextRecognizer? = null
        try {
            sendStatus(1, Bundle().apply { putString("stage", "initializing SDK") })
            val activeRecognizer = TextRecognition.getClient(
                TextRecognizerOptions.Builder().setExecutor(executor).build()
            )
            recognizer = activeRecognizer
            val fixtures = if (quick) {
                listOf(
                    quickFixture
                )
            } else {
                listOf("available", "power-unavailable")
            }
            for (fixture in fixtures) {
                val bytes = context.assets.open("vision/$fixture.rgb").use { it.readBytes() }
                for (width in if (quick) listOf(1080) else listOf(570, 1080)) {
                    val height = if (width == 570) 1280 else 2424
                    val top = (1130L * height / 1280).toInt()
                    val bottom = (1265L * height / 1280).toInt()
                    for (masked in if (quick) listOf(false) else listOf(false, true)) {
                        val colors = IntArray(width * (bottom - top)) { index ->
                            val x = (index % width).toLong() * 570 / width
                            val y = ((top + index / width).toLong() * 1280 / height)
                                .coerceIn(1130, 1279)
                            val localX = x - (x / 169) * 169
                            if (masked && localX in 70..180 && y in 1180..1217) {
                                0xFF89F5DF.toInt()
                            } else {
                                val i = ((y.toInt() - 1130) * 570 + x.toInt()) * 3
                                (0xFF shl 24) or ((bytes[i].toInt() and 255) shl 16) or
                                    ((bytes[i + 1].toInt() and 255) shl 8) or
                                    (bytes[i + 2].toInt() and 255)
                            }
                        }
                        val bitmap = Bitmap.createBitmap(
                            colors,
                            width,
                            bottom - top,
                            Bitmap.Config.ARGB_8888
                        )
                        sendStatus(
                            1,
                            Bundle().apply {
                                putString("stage", "$fixture/$width/$masked")
                            }
                        )
                        val started = SystemClock.elapsedRealtime()
                        val text = Tasks.await(
                            activeRecognizer.process(InputImage.fromBitmap(bitmap, 0)),
                            120,
                            TimeUnit.SECONDS
                        )
                        val elapsed = SystemClock.elapsedRealtime() - started
                        val tokens = text.textBlocks.flatMap { it.lines }.flatMap { it.elements }
                            .mapNotNull { element ->
                                NativeOcrToken.from(element, bitmap, top, height)
                            }
                        val evidence = UpgradePanelEvidence(
                            UpgradeKind.entries.map {
                                UpgradeAppearance(it, AvailabilityAppearance.AVAILABLE, 1.0)
                            }
                        )
                        val parsed = UpgradeOcrParser.parse(tokens, evidence)
                        rows.put(
                            JSONObject().put("fixture", fixture).put("width", width)
                                .put("masked", masked).put("elapsedMillis", elapsed)
                                .put(
                                    "readable",
                                    parsed.upgrades != null
                                ).put("detail", parsed.detail)
                                .put("rawText", text.text)
                        )
                        bitmap.recycle()
                        File(targetContext.filesDir, "ocr-probe.json").writeText(rows.toString(2))
                    }
                }
            }
            recognizer.close()
            recognizer = null
            executor.shutdown()
            rows.put(productionWorker())
            finish(Activity.RESULT_OK, Bundle().apply { putString("results", rows.toString()) })
        } catch (error: Exception) {
            finish(
                Activity.RESULT_CANCELED,
                Bundle().apply {
                    putString("error", "${error.javaClass.simpleName}: ${error.message}")
                    putString("results", rows.toString())
                }
            )
        } finally {
            recognizer?.close()
            executor.shutdown()
        }
    }
    private fun productionWorker(): JSONObject {
        sendStatus(1, Bundle().apply { putString("stage", "production worker") })
        val bytes = context.assets.open("vision/available.rgb").use { it.readBytes() }
        val source = object : PixelSource {
            override val width = 570
            override val height = 1280
            override fun rgbAt(x: Int, y: Int): Int {
                val i = ((y - 1130) * 570 + x) * 3
                return ((bytes[i].toInt() and 255) shl 16) or
                    ((bytes[i + 1].toInt() and 255) shl 8) or (bytes[i + 2].toInt() and 255)
            }
        }
        val evidence = UpgradePanelEvidence(
            UpgradeKind.entries.map {
                UpgradeAppearance(it, AvailabilityAppearance.AVAILABLE, 1.0)
            }
        )
        val latch = CountDownLatch(1)
        val result = AtomicReference<UpgradeOcrResult>()
        val ocr = AtomicReference<UpgradeOcr>()
        runOnMainSync {
            val worker = UpgradeOcr()
            ocr.set(worker)
            check(
                worker.sample(source, evidence) { reading ->
                    result.set(reading)
                    latch.countDown()
                }
            )
        }
        try {
            check(latch.await(120, TimeUnit.SECONDS)) { "Production OCR callback timed out" }
            val reading = checkNotNull(result.get())
            val stages = checkNotNull(reading.stages)
            return JSONObject().put("fixture", "available").put("width", 570)
                .put("productionWorker", true).put("elapsedMillis", reading.workMillis)
                .put("readable", reading.upgrades != null).put("detail", reading.detail)
                .put("copyMillis", stages.prepareMillis).put("sdkMillis", stages.sdkMillis)
                .put(
                    "parseMillis",
                    stages.parseMillis
                ).put("mainQueueMillis", stages.mainQueueMillis)
                .put("workerPriority", stages.workerPriority)
        } finally {
            runOnMainSync { ocr.get().close() }
        }
    }
}
