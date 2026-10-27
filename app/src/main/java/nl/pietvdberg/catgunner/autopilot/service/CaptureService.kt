package nl.pietvdberg.catgunner.autopilot.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.service.quicksettings.TileService
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import nl.pietvdberg.catgunner.autopilot.R
import nl.pietvdberg.catgunner.autopilot.capture.LatestFrameCapture
import nl.pietvdberg.catgunner.autopilot.capture.PurchasePipeline
import nl.pietvdberg.catgunner.autopilot.data.PurchaseRuntime
import nl.pietvdberg.catgunner.autopilot.data.ReceiptRecorder
import nl.pietvdberg.catgunner.autopilot.domain.purchase.PurchasePhase
import nl.pietvdberg.catgunner.autopilot.domain.state.AutoPilotSession
import nl.pietvdberg.catgunner.autopilot.domain.state.SessionMode
import nl.pietvdberg.catgunner.autopilot.domain.state.SessionState
import nl.pietvdberg.catgunner.autopilot.domain.strategy.AutoRunPhase
import nl.pietvdberg.catgunner.autopilot.tiles.AutoPilotTileService
import nl.pietvdberg.catgunner.autopilot.ui.MainActivity

class CaptureService : Service() {
    private val controller = AutoPilotSession.controller
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var projection: MediaProjection? = null
    private var frames: LatestFrameCapture? = null
    private var pipeline: PurchasePipeline? = null
    private var generation = -1L
    private var foreground = false
    private var width = 0
    private var height = 0

    private val screenLockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) SessionCommands.pause()
        }
    }

    private val callback = object : MediaProjection.Callback() {
        override fun onStop() {
            controller.fail(generation, getString(R.string.capture_failed))
            stopSelf()
        }

        override fun onCapturedContentResize(newWidth: Int, newHeight: Int) {
            if (newWidth != width || newHeight != height) {
                controller.fail(generation, getString(R.string.capture_failed))
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                OUTCOME_CHANNEL_ID,
                getString(R.string.auto_outcome_channel),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        scope.launch {
            PurchaseRuntime.autoRunner.report.drop(1)
                .filter { it.phase == AutoRunPhase.COMPLETE || it.phase == AutoRunPhase.STOPPED }
                .distinctUntilChangedBy { it.id }.collect { run ->
                    val allowed = Build.VERSION.SDK_INT < 33 ||
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                    if (allowed) {
                        val openApp = PendingIntent.getActivity(
                            this@CaptureService,
                            0,
                            Intent(this@CaptureService, MainActivity::class.java),
                            PendingIntent.FLAG_IMMUTABLE
                        )
                        val notice = Notification.Builder(this@CaptureService, OUTCOME_CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_autopilot)
                            .setContentTitle(
                                getString(
                                    if (run.phase ==
                                        AutoRunPhase.COMPLETE
                                    ) {
                                        R.string.auto_outcome_complete
                                    } else {
                                        R.string.auto_outcome_stopped
                                    }
                                )
                            )
                            .setContentText(
                                getString(
                                    R.string.auto_outcome_body,
                                    run.verified,
                                    run.limit,
                                    run.detail
                                )
                            )
                            .setContentIntent(openApp).setAutoCancel(true).build()
                        getSystemService(
                            NotificationManager::class.java
                        ).notify(OUTCOME_NOTIFICATION_ID, notice)
                    }
                }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                screenLockReceiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(screenLockReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        }
        scope.launch {
            while (true) {
                PurchaseRuntime.engine.tick()
                PurchaseRuntime.autoRunner.tick(controller.observationLease())
                delay(250)
            }
        }
        ReceiptRecorder.start(this)
        scope.launch {
            combine(
                controller.state.map { it.mode }.distinctUntilChanged(),
                PurchaseRuntime.engine.report.distinctUntilChangedBy {
                    Triple(it.id, it.phase, it.detail)
                },
                PurchaseRuntime.autoRunner.report
            ) { mode, report, run -> Triple(mode, report, run) }.collect {
                val state = controller.state.value
                val notificationsAllowed = Build.VERSION.SDK_INT < 33 ||
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
                if (foreground && notificationsAllowed) {
                    getSystemService(
                        NotificationManager::class.java
                    ).notify(NOTIFICATION_ID, notification(state))
                }
                TileService.requestListeningState(
                    this@CaptureService,
                    ComponentName(this@CaptureService, AutoPilotTileService::class.java)
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_CAPTURE && projection == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            SessionCommands.PAUSE -> SessionCommands.pause()
            SessionCommands.RESUME -> SessionCommands.resume()
            SessionCommands.STOP -> {
                controller.stop()
                stopSelf()
            }
            ACTION_CAPTURE -> startCapture(intent)
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startCapture(intent: Intent) {
        val requestedGeneration = intent.getLongExtra(EXTRA_GENERATION, -1)
        if (projection != null ||
            controller.state.value.generation != requestedGeneration ||
            controller.state.value.mode != SessionMode.STARTING
        ) {
            if (projection == null) stopSelf()
            return
        }
        generation = requestedGeneration
        try {
            val notice = notification(controller.state.value)
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    NOTIFICATION_ID,
                    notice,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notice)
            }
            foreground = true
            val data = consentData(intent) ?: error("Missing capture consent")
            val manager = getSystemService(MediaProjectionManager::class.java)
            val activeProjection =
                manager.getMediaProjection(intent.getIntExtra(EXTRA_RESULT, 0), data)
                    ?: error("Capture consent was not accepted")
            projection = activeProjection
            val handler = Handler(Looper.getMainLooper())
            activeProjection.registerCallback(callback, handler)
            val metrics = resources.displayMetrics
            if (Build.VERSION.SDK_INT >= 30) {
                val bounds = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
                width = bounds.width()
                height = bounds.height()
            } else {
                width = metrics.widthPixels
                height = metrics.heightPixels
            }
            pipeline = PurchasePipeline(this)
            frames = LatestFrameCapture(
                activeProjection,
                width,
                height,
                metrics.densityDpi,
                handler,
                shouldAnalyze = { controller.state.value.mode == SessionMode.OBSERVING },
                fastSampling = { PurchaseRuntime.purchasing() },
                analyze = { pixels, capturedAt ->
                    pipeline?.sample(pixels, generation, capturedAt)
                },
                onFailure = {
                    controller.fail(generation, getString(R.string.capture_failed))
                    stopSelf()
                }
            )
            if (!controller.captureStarted(generation)) stopSelf()
        } catch (error: RuntimeException) {
            controller.fail(generation, getString(R.string.capture_failed))
            stopSelf()
        }
    }

    @Suppress("DEPRECATION")
    private fun consentData(intent: Intent): Intent? = if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(EXTRA_CONSENT, Intent::class.java)
    } else {
        intent.getParcelableExtra(EXTRA_CONSENT)
    }

    private fun notification(state: SessionState): Notification {
        val running = state.mode == SessionMode.OBSERVING
        val report = PurchaseRuntime.engine.report.value
        val run = PurchaseRuntime.autoRunner.report.value
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_autopilot)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(
                if (run.active) {
                    "Auto ${run.verified}/${run.limit} · " +
                        if (run.phase ==
                            AutoRunPhase.WAITING
                        ) {
                            run.detail
                        } else {
                            report.detail
                        }
                } else if (report.phase == PurchasePhase.IDLE) {
                    getString(
                        if (running) {
                            R.string.notification_observing
                        } else {
                            R.string.notification_paused
                        }
                    )
                } else {
                    report.detail
                }
            )
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(if (running) R.string.pause else R.string.resume),
                    commandIntent(
                        if (running) SessionCommands.PAUSE else SessionCommands.RESUME,
                        1
                    )
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.stop),
                    commandIntent(SessionCommands.STOP, 2)
                ).build()
            )
            .build()
    }

    private fun commandIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, CaptureService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    override fun onDestroy() {
        unregisterReceiver(screenLockReceiver)
        pipeline?.close()
        pipeline = null
        frames?.close()
        frames = null
        projection?.unregisterCallback(callback)
        projection?.stop()
        projection = null
        controller.fail(generation, getString(R.string.capture_failed))
        TileService.requestListeningState(
            this,
            ComponentName(this, AutoPilotTileService::class.java)
        )
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_CAPTURE = "nl.pietvdberg.catgunner.autopilot.CAPTURE"
        const val EXTRA_GENERATION = "generation"
        const val EXTRA_RESULT = "result"
        const val EXTRA_CONSENT = "consent"
        private const val CHANNEL_ID = "capture_controls"
        private const val NOTIFICATION_ID = 1
        private const val OUTCOME_CHANNEL_ID = "auto_run_outcomes"
        private const val OUTCOME_NOTIFICATION_ID = 2
    }
}
