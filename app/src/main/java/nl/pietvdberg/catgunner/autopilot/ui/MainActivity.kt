package nl.pietvdberg.catgunner.autopilot.ui

import android.Manifest
import android.app.StatusBarManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.pietvdberg.catgunner.autopilot.R
import nl.pietvdberg.catgunner.autopilot.accessibility.GameAccessibilityService
import nl.pietvdberg.catgunner.autopilot.data.CalibrationStore
import nl.pietvdberg.catgunner.autopilot.data.LiveDiagnostics
import nl.pietvdberg.catgunner.autopilot.data.PreviewStore
import nl.pietvdberg.catgunner.autopilot.data.PurchaseRuntime
import nl.pietvdberg.catgunner.autopilot.data.ReadingStore
import nl.pietvdberg.catgunner.autopilot.domain.purchase.PurchasePhase
import nl.pietvdberg.catgunner.autopilot.domain.state.AutoPilotSession
import nl.pietvdberg.catgunner.autopilot.domain.state.SessionMode
import nl.pietvdberg.catgunner.autopilot.service.CaptureService
import nl.pietvdberg.catgunner.autopilot.service.SessionCommands
import nl.pietvdberg.catgunner.autopilot.tiles.AutoPilotTileService
import nl.pietvdberg.catgunner.autopilot.vision.AvailabilityAppearance
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeKind

class MainActivity : ComponentActivity() {
    private val controller = AutoPilotSession.controller
    private var scope: CoroutineScope? = null
    private var pendingGeneration = -1L
    private lateinit var status: TextView
    private lateinit var vision: TextView
    private lateinit var retained: TextView
    private lateinit var purchaseStatus: TextView
    private lateinit var calibrationStatus: TextView
    private lateinit var reviewOutcome: Button
    private lateinit var diagnostics: TextView
    private val purchaseButtons = mutableMapOf<UpgradeKind, Button>()
    private lateinit var start: Button
    private lateinit var pause: Button
    private lateinit var resume: Button
    private lateinit var stop: Button

    private val captureConsent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handleCaptureConsent(result.resultCode, result.data)
        }
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            render()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingGeneration = savedInstanceState?.getLong("pending_generation", -1) ?: -1
        val padding = (24 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            setOnApplyWindowInsetsListener { view, insets ->
                val top: Int
                val bottom: Int
                if (Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    top = bars.top
                    bottom = bars.bottom
                } else {
                    @Suppress("DEPRECATION")
                    top = insets.systemWindowInsetTop
                    @Suppress("DEPRECATION")
                    bottom = insets.systemWindowInsetBottom
                }
                view.setPadding(padding, padding + top, padding, padding + bottom)
                insets
            }
        }
        content.addView(
            TextView(this).apply {
                setText(R.string.app_name)
                textSize = 26f
            }
        )
        content.addView(
            TextView(this).apply {
                setText(R.string.intro)
                textSize = 16f
            }
        )
        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, padding, 0, padding)
        }
        content.addView(status)
        content.addView(TextView(this).apply { setText(R.string.spending_rule) })
        vision = TextView(this).apply { textSize = 16f }
        content.addView(vision)
        fun button(label: Int, action: () -> Unit): Button = Button(this).also {
            it.setText(label)
            it.setOnClickListener { action() }
            content.addView(it)
        }
        calibrationStatus = TextView(this).apply { textSize = 16f }
        content.addView(calibrationStatus)
        button(R.string.accessibility_setup) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        start = button(R.string.start) { requestCapture() }
        pause = button(R.string.pause) { SessionCommands.pause() }
        resume = button(R.string.resume) { SessionCommands.resume() }
        stop = button(R.string.stop) { SessionCommands.stop(this) }
        retained = TextView(this).apply { textSize = 16f }
        content.addView(retained)
        button(R.string.calibration_open) {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }
        button(R.string.calibration_clear) {
            PurchaseRuntime.engine.cancel("Calibration reset.")
            CalibrationStore(this).clear()
            render()
        }
        diagnostics = TextView(this).apply { textSize = 14f }
        content.addView(diagnostics)
        button(R.string.copy_diagnostics) {
            val text = listOf(
                "AutoPilot 0.3.3",
                calibrationStatus.text,
                purchaseStatus.text,
                diagnostics.text
            ).joinToString("\n")
            getSystemService(ClipboardManager::class.java).setPrimaryClip(
                ClipData.newPlainText(getString(R.string.app_name), text)
            )
            Toast.makeText(this, R.string.diagnostics_copied, Toast.LENGTH_SHORT).show()
        }
        purchaseStatus = TextView(this).apply { textSize = 16f }
        content.addView(purchaseStatus)
        reviewOutcome = button(R.string.review_outcome) {
            PurchaseRuntime.engine.acknowledgeUnconfirmed()
        }
        UpgradeKind.entries.forEach { kind ->
            val label = when (kind) {
                UpgradeKind.POWER -> R.string.test_power
                UpgradeKind.SPEED -> R.string.test_speed
                UpgradeKind.MONEY -> R.string.test_money
            }
            purchaseButtons[kind] = button(label) { armPurchase(kind) }
        }
        button(R.string.notifications) {
            if (Build.VERSION.SDK_INT >= 33) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.visibility = if (Build.VERSION.SDK_INT >= 33) View.VISIBLE else View.GONE
        button(R.string.add_tile) { addTile() }
        content.addView(TextView(this).apply { setText(R.string.tile_help) })
        setContentView(ScrollView(this).apply { addView(content) })
    }

    override fun onStart() {
        super.onStart()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { activeScope ->
            activeScope.launch { controller.state.collect { render() } }
            activeScope.launch { PreviewStore.preview.collect { render() } }
            activeScope.launch { PurchaseRuntime.engine.report.collect { render() } }
            activeScope.launch { ReadingStore.reading.collect { render() } }
            activeScope.launch { LiveDiagnostics.state.collect { render() } }
            activeScope.launch {
                while (true) {
                    delay(1000)
                    render()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        PurchaseRuntime.engine.cancel("Returned to AutoPilot. No further tap may dispatch.")
        render()
    }

    override fun onStop() {
        scope?.cancel()
        scope = null
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("pending_generation", pendingGeneration)
        super.onSaveInstanceState(outState)
    }

    private fun requestCapture() {
        val generation = controller.begin() ?: return
        pendingGeneration = generation
        try {
            val manager = getSystemService(MediaProjectionManager::class.java)
            val intent = if (Build.VERSION.SDK_INT >= 34) {
                manager.createScreenCaptureIntent(
                    MediaProjectionConfig.createConfigForDefaultDisplay()
                )
            } else {
                manager.createScreenCaptureIntent()
            }
            captureConsent.launch(intent)
        } catch (error: RuntimeException) {
            controller.fail(generation, getString(R.string.capture_unavailable))
        }
    }

    private fun handleCaptureConsent(resultCode: Int, data: Intent?) {
        val generation = pendingGeneration
        pendingGeneration = -1
        if (controller.state.value.generation != generation ||
            controller.state.value.mode != SessionMode.STARTING
        ) {
            return
        }
        if (resultCode != RESULT_OK || data == null) {
            controller.fail(generation, getString(R.string.capture_denied))
            return
        }
        try {
            startForegroundService(
                Intent(this, CaptureService::class.java)
                    .setAction(CaptureService.ACTION_CAPTURE)
                    .putExtra(CaptureService.EXTRA_GENERATION, generation)
                    .putExtra(CaptureService.EXTRA_RESULT, resultCode)
                    .putExtra(CaptureService.EXTRA_CONSENT, data)
            )
        } catch (error: RuntimeException) {
            controller.fail(generation, getString(R.string.capture_failed))
        }
    }

    private fun render() {
        val state = controller.state.value
        val label = when (state.mode) {
            SessionMode.STOPPED -> R.string.state_stopped
            SessionMode.STARTING -> R.string.state_starting
            SessionMode.OBSERVING -> R.string.state_observing
            SessionMode.PAUSED -> R.string.state_paused
            SessionMode.ERROR -> R.string.state_error
        }
        val notifications = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        status.text = getString(
            R.string.setup_status,
            getString(label),
            state.detail,
            getString(if (state.captureActive) R.string.yes else R.string.no),
            getString(if (notifications) R.string.yes else R.string.no)
        )
        val evidence = state.visualEvidence
        vision.text = if (evidence == null) {
            getString(R.string.vision_waiting)
        } else if (!evidence.recognized) {
            getString(R.string.vision_unknown)
        } else {
            val rows = evidence.upgrades.joinToString("\n") { upgrade ->
                val name = when (upgrade.kind) {
                    UpgradeKind.POWER -> R.string.upgrade_power
                    UpgradeKind.SPEED -> R.string.upgrade_speed
                    UpgradeKind.MONEY -> R.string.upgrade_money
                }
                val appearance = when (upgrade.availability) {
                    AvailabilityAppearance.AVAILABLE -> R.string.appearance_available
                    AvailabilityAppearance.UNAVAILABLE -> R.string.appearance_unavailable
                    AvailabilityAppearance.UNKNOWN -> R.string.appearance_unknown
                }
                getString(R.string.vision_upgrade, getString(name), getString(appearance))
            }
            getString(R.string.vision_evidence, rows)
        }
        val snapshot = PreviewStore.preview.value
        retained.text = if (snapshot == null) {
            getString(R.string.preview_waiting)
        } else {
            val rows = snapshot.evidence.upgrades.joinToString("\n") {
                "${it.kind}: ${it.availability}"
            }
            getString(
                R.string.preview_retained,
                (SystemClock.elapsedRealtime() - snapshot.capturedAt) / 1000,
                snapshot.nativeWidth,
                snapshot.nativeHeight,
                rows
            )
        }
        val calibration = CalibrationStore(this).load()
        val calibrated = calibration?.isValid() == true &&
            calibration.gameVersion == CalibrationStore.gameVersion(this)
        val accessibility = GameAccessibilityService.instance != null
        calibrationStatus.text = getString(
            R.string.calibration_status,
            getString(if (calibrated) R.string.yes else R.string.no),
            getString(
                when {
                    accessibility -> R.string.accessibility_connected
                    GameAccessibilityService.enabledInAndroid(
                        this
                    ) -> R.string.accessibility_disconnected
                    else -> R.string.accessibility_disabled
                }
            )
        )
        val diagnostic = LiveDiagnostics.state.value
        diagnostics.text = getString(
            R.string.diagnostics_text,
            diagnostic.live,
            diagnostic.lastGame,
            diagnostic.ocr,
            diagnostic.service,
            diagnostic.foreground
        )
        val report = PurchaseRuntime.engine.report.value
        val reading = ReadingStore.reading.value
        val readings = reading?.upgrades?.entries?.joinToString("\n") {
            "${it.key}: Lv.${it.value.level}, ${it.value.cost}"
        } ?: getString(R.string.ocr_waiting)
        purchaseStatus.text = getString(
            R.string.purchase_status,
            report.phase.name,
            report.detail,
            readings,
            getString(if (report.gestureDispatched) R.string.tap_possible else R.string.tap_none),
            report.before?.level?.toString() ?: "—",
            report.after?.level?.toString() ?: "—",
            report.verificationSamples
        )
        reviewOutcome.visibility = if (report.phase == PurchasePhase.UNCONFIRMED) {
            View.VISIBLE
        } else {
            View.GONE
        }
        val active =
            report.phase in
                setOf(PurchasePhase.ARMED, PurchasePhase.DISPATCHED, PurchasePhase.VERIFYING)
        purchaseButtons.values.forEach {
            it.isEnabled = calibrated &&
                accessibility &&
                !active &&
                report.phase != PurchasePhase.UNCONFIRMED &&
                state.mode == SessionMode.OBSERVING &&
                state.captureActive
        }
        start.isEnabled = state.mode == SessionMode.STOPPED || state.mode == SessionMode.ERROR
        pause.isEnabled = state.mode == SessionMode.OBSERVING
        resume.isEnabled = state.mode == SessionMode.PAUSED && state.captureActive
        stop.isEnabled = state.mode != SessionMode.STOPPED
    }

    private fun armPurchase(kind: UpgradeKind) {
        val calibration = CalibrationStore(this).load() ?: return
        val lease = controller.observationLease() ?: return
        if (GameAccessibilityService.instance == null ||
            calibration.gameVersion != CalibrationStore.gameVersion(this)
        ) {
            return
        }
        if (!PurchaseRuntime.engine.arm(kind, calibration, lease)) return
        val game = packageManager.getLaunchIntentForPackage(CalibrationStore.GAME_PACKAGE)
        if (game == null) {
            PurchaseRuntime.engine.cancel("Game could not be opened.")
            Toast.makeText(this, R.string.game_missing, Toast.LENGTH_LONG).show()
        } else {
            try {
                startActivity(game)
            } catch (error: RuntimeException) {
                PurchaseRuntime.engine.cancel("Game could not be opened.")
            }
        }
    }

    private fun addTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(this, AutoPilotTileService::class.java),
                getString(R.string.app_name),
                Icon.createWithResource(this, R.drawable.ic_autopilot),
                mainExecutor
            ) { render() }
        }
    }
}
