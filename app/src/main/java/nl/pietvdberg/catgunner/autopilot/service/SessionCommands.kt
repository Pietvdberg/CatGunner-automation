package nl.pietvdberg.catgunner.autopilot.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import nl.pietvdberg.catgunner.autopilot.data.PurchaseRuntime
import nl.pietvdberg.catgunner.autopilot.domain.state.AutoPilotSession

/** Never starts a projection service from a notification or tile. */
object SessionCommands {
    const val PAUSE = "nl.pietvdberg.catgunner.autopilot.PAUSE"
    const val RESUME = "nl.pietvdberg.catgunner.autopilot.RESUME"
    const val STOP = "nl.pietvdberg.catgunner.autopilot.STOP"

    fun pause() {
        AutoPilotSession.controller.pause()
        PurchaseRuntime.engine.cancel("Paused. An already-dispatched gesture may finish.")
    }

    fun resume(): Boolean = AutoPilotSession.controller.resume()

    // stopService matches the Intent's component, not the Intent object's identity.
    @SuppressLint("ImplicitSamInstance")
    fun stop(context: Context) {
        AutoPilotSession.controller.stop()
        PurchaseRuntime.engine.cancel("Stopped. An already-dispatched gesture may finish.")
        context.stopService(Intent(context, CaptureService::class.java))
    }
}
