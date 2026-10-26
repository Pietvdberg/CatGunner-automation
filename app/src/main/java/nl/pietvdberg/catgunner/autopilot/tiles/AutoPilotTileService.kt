package nl.pietvdberg.catgunner.autopilot.tiles

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import nl.pietvdberg.catgunner.autopilot.R
import nl.pietvdberg.catgunner.autopilot.domain.state.AutoPilotSession
import nl.pietvdberg.catgunner.autopilot.domain.state.SessionMode
import nl.pietvdberg.catgunner.autopilot.service.SessionCommands
import nl.pietvdberg.catgunner.autopilot.ui.MainActivity

class AutoPilotTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listening: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = scope.launch {
            AutoPilotSession.controller.state.map { it.mode }.distinctUntilChanged().collect {
                updateTile()
            }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun { toggle() }
        } else {
            toggle()
        }
    }

    private fun toggle() {
        when (AutoPilotSession.controller.state.value.mode) {
            SessionMode.OBSERVING -> SessionCommands.pause()
            SessionMode.PAUSED -> if (!SessionCommands.resume()) openSetup()
            else -> openSetup()
        }
        updateTile()
    }

    private fun updateTile() {
        qsTile?.apply {
            state = if (AutoPilotSession.controller.state.value.mode == SessionMode.OBSERVING) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }
            label = getString(R.string.app_name)
            updateTile()
        }
    }

    // The PendingIntent overload exists only on API 34+; older releases need Intent.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun openSetup() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
