package nl.pietvdberg.catgunner.autopilot.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import nl.pietvdberg.catgunner.autopilot.domain.purchase.PurchaseReport
import org.json.JSONArray
import org.json.JSONObject

/** Local bounded receipts contain numeric readings and outcomes, never screenshots. */
class PurchaseJournal(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "purchase-receipts.json"))

    fun append(report: PurchaseReport) = synchronized(lock) {
        val previous = try {
            file.openRead().bufferedReader().use { JSONArray(it.readText()) }
        } catch (error: Exception) {
            JSONArray()
        }
        if ((0 until previous.length()).any {
                val event = previous.getJSONObject(it)
                event.optString("id") == report.id &&
                    event.optString("phase") == report.phase.name &&
                    event.optBoolean("gestureDispatched") == report.gestureDispatched
            }
        ) {
            return@synchronized
        }
        val retained = JSONArray()
        for (index in maxOf(0, previous.length() - 199) until previous.length()) {
            retained.put(previous.getJSONObject(index))
        }
        val point = report.kind?.let { report.calibration?.targets?.get(it) }
        retained.put(
            JSONObject().put("id", report.id).put("phase", report.phase.name)
                .put("autoRunId", report.autoRunId)
                .put("gestureDispatched", report.gestureDispatched)
                .put("verificationSamples", report.verificationSamples)
                .put("upgrade", report.kind?.name).put("detail", report.detail)
                .put("beforeLevel", report.before?.level).put("beforeCost", report.before?.cost)
                .put("afterLevel", report.after?.level).put("afterCost", report.after?.cost)
                .put("frameWidth", report.calibration?.width)
                .put("frameHeight", report.calibration?.height)
                .put("gameVersion", report.calibration?.gameVersion)
                .put("targetX", point?.x).put("targetY", point?.y)
                .put("recordedAtEpochMillis", System.currentTimeMillis())
        )
        val stream = file.startWrite()
        try {
            stream.write(retained.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    companion object {
        private val lock = Any()
    }
}
