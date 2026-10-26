package nl.pietvdberg.catgunner.autopilot.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.AtomicFile
import java.io.File
import nl.pietvdberg.catgunner.autopilot.domain.purchase.CalibrationProfile
import nl.pietvdberg.catgunner.autopilot.domain.purchase.NormalizedPoint
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeKind
import org.json.JSONObject

class CalibrationStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "calibration-v1.json"))

    fun load(): CalibrationProfile? = try {
        val json = file.openRead().bufferedReader().use { JSONObject(it.readText()) }
        CalibrationProfile(
            json.getInt("width"),
            json.getInt("height"),
            json.getLong("gameVersion"),
            UpgradeKind.entries.associateWith { kind ->
                val point = json.getJSONObject("targets").getJSONObject(kind.name)
                NormalizedPoint(point.getDouble("x").toFloat(), point.getDouble("y").toFloat())
            },
            json.getInt("schema")
        ).takeIf { it.isValid() && json.getString("currency") == "GREEN_BANKNOTES" }
    } catch (error: Exception) {
        null
    }

    fun save(profile: CalibrationProfile) {
        require(profile.isValid())
        val targets = JSONObject()
        profile.targets.forEach { (kind, point) ->
            targets.put(kind.name, JSONObject().put("x", point.x).put("y", point.y))
        }
        val json = JSONObject().put("schema", profile.schema).put("width", profile.width)
            .put("height", profile.height).put("gameVersion", profile.gameVersion)
            .put("currency", "GREEN_BANKNOTES").put("targets", targets)
        val stream = file.startWrite()
        try {
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    fun clear() = file.delete()

    companion object {
        const val GAME_PACKAGE = "com.Chodun.CatGunner"

        @Suppress("DEPRECATION")
        fun gameVersion(context: Context): Long = try {
            val info = context.packageManager.getPackageInfo(GAME_PACKAGE, 0)
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        } catch (error: PackageManager.NameNotFoundException) {
            -1
        }
    }
}
