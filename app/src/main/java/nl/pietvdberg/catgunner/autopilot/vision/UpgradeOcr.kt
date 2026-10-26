package nl.pietvdberg.catgunner.autopilot.vision

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import nl.pietvdberg.catgunner.autopilot.domain.purchase.UpgradeReading

/** One bounded in-flight strip. The bundled recognizer operates without network access. */
class UpgradeOcr : AutoCloseable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var busy = false
    private var closed = false

    fun sample(
        pixels: PixelSource,
        evidence: UpgradePanelEvidence,
        completed: (Map<UpgradeKind, UpgradeReading>?) -> Unit
    ): Boolean {
        if (busy || closed) return false
        busy = true
        val top = (1130L * pixels.height / 1280).toInt()
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
                                parse(
                                    task.result,
                                    pixels.width,
                                    pixels.height,
                                    top,
                                    evidence
                                )
                            } else {
                                null
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
            completed(null)
        }
        return true
    }

    private fun parse(
        text: Text,
        width: Int,
        height: Int,
        top: Int,
        evidence: UpgradePanelEvidence
    ): Map<UpgradeKind, UpgradeReading>? {
        val elements = text.textBlocks.flatMap { it.lines }.flatMap { it.elements }
        val readings = mutableMapOf<UpgradeKind, UpgradeReading>()
        for (kind in UpgradeKind.entries) {
            val offset = kind.ordinal * 169
            fun region(left: Int, right: Int, low: Int, high: Int): String = elements.filter {
                val bounds = it.boundingBox ?: return@filter false
                val x = bounds.exactCenterX() * 570 / width
                val y = (top + bounds.exactCenterY()) * 1280 / height
                x in (left + offset).toFloat()..(right + offset).toFloat() &&
                    y in low.toFloat()..high.toFloat()
            }.sortedBy { it.boundingBox?.left }.joinToString("") { it.text }
            val level = UpgradeTextParser.level(region(118, 192, 1150, 1178)) ?: return null
            val cost = UpgradeTextParser.cost(region(95, 174, 1220, 1248)) ?: return null
            val available = evidence.upgrades.firstOrNull { it.kind == kind }?.availability ==
                AvailabilityAppearance.AVAILABLE
            readings[kind] = UpgradeReading(level, cost, available)
        }
        return readings
    }

    override fun close() {
        closed = true
        if (!busy) recognizer.close()
    }
}
