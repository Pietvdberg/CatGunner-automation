package nl.pietvdberg.catgunner.autopilot.capture

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.pietvdberg.catgunner.autopilot.accessibility.GameAccessibilityService
import nl.pietvdberg.catgunner.autopilot.data.CalibrationStore
import nl.pietvdberg.catgunner.autopilot.data.PreviewStore
import nl.pietvdberg.catgunner.autopilot.data.PurchaseJournal
import nl.pietvdberg.catgunner.autopilot.data.PurchaseRuntime
import nl.pietvdberg.catgunner.autopilot.data.ReadingStore
import nl.pietvdberg.catgunner.autopilot.domain.purchase.GameReading
import nl.pietvdberg.catgunner.autopilot.domain.purchase.PurchasePhase
import nl.pietvdberg.catgunner.autopilot.domain.state.AutoPilotSession
import nl.pietvdberg.catgunner.autopilot.vision.AvailabilityAppearance
import nl.pietvdberg.catgunner.autopilot.vision.BanknoteMatcher
import nl.pietvdberg.catgunner.autopilot.vision.PixelSource
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeOcr
import nl.pietvdberg.catgunner.autopilot.vision.UpgradePanelDetector

class PurchasePipeline(private val context: Context) : AutoCloseable {
    private val detector = context.assets.open(
        "vision/upgrade-labels-v1.csv"
    ).bufferedReader().use {
        UpgradePanelDetector.fromCsv(it.lineSequence())
    }
    private val banknote = context.assets.open("vision/banknote-v1.csv").bufferedReader().use {
        BanknoteMatcher(it.lineSequence())
    }
    private val ocr = UpgradeOcr()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller = AutoPilotSession.controller
    private val engine = PurchaseRuntime.engine
    private var lastRecognizedAt = -1L
    private var closed = false
    private var trackedRequest = ""
    private var requestSawGame = false
    private var latestBanknoteMatched = false

    fun sample(pixels: PixelSource, generation: Long, capturedAt: Long) {
        if (closed) return
        engine.tick()
        val evidence = detector.detect(pixels)
        controller.publishEvidence(generation, evidence)
        val accessibility = GameAccessibilityService.instance
        val foreground = accessibility?.isGameForeground() == true
        val phase = engine.report.value.phase
        val requestId = engine.report.value.id
        if (trackedRequest != requestId) {
            trackedRequest = requestId
            requestSawGame = false
        }
        if (!foreground) {
            if (phase == PurchasePhase.DISPATCHED ||
                phase == PurchasePhase.VERIFYING ||
                (phase == PurchasePhase.ARMED && requestSawGame)
            ) {
                engine.unsafe("Game is no longer foreground. No retry.")
            }
            return
        }
        if (phase == PurchasePhase.ARMED) requestSawGame = true
        if (!evidence.recognized) {
            engine.unsafe("Unrecognized or obscured upgrade controls. No gesture permitted.")
            return
        }
        lastRecognizedAt = capturedAt
        val version = CalibrationStore.gameVersion(context)
        PreviewStore.update(pixels, evidence, version)
        val actionLease = controller.observationLease() ?: return
        val nativeWidth = pixels.width
        val nativeHeight = pixels.height
        val kind = engine.report.value.kind
        val matchedBanknote = kind != null && banknote.matches(pixels, kind)
        latestBanknoteMatched = matchedBanknote
        ocr.sample(pixels, evidence) { upgrades ->
            if (closed ||
                controller.observationLease() != actionLease ||
                engine.report.value.id != requestId
            ) {
                return@sample
            }
            if (GameAccessibilityService.instance?.isGameForeground() != true) {
                engine.unsafe("Game changed while reading values.")
                return@sample
            }
            if (upgrades == null) {
                engine.unsafe("Upgrade levels or costs could not be read reliably. No retry.")
                return@sample
            }
            val reading = GameReading(
                capturedAt,
                nativeWidth,
                nativeHeight,
                version,
                upgrades,
                matchedBanknote,
                true
            )
            ReadingStore.update(reading)
            engine.onReading(reading) { id, lease, point ->
                val request = engine.report.value
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { PurchaseJournal(context).append(request) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        engine.gestureCompleted(id, false)
                        return@launch
                    }
                    val accepted = controller.dispatchIfCurrent(lease) {
                        val liveProfile = CalibrationStore(context).load()
                        val current = controller.state.value.visualEvidence
                        val fresh = SystemClock.elapsedRealtime() - lastRecognizedAt in 0..500
                        if (closed ||
                            engine.report.value.id != id ||
                            engine.report.value.phase != PurchasePhase.DISPATCHED ||
                            SystemClock.elapsedRealtime() - capturedAt !in 0..750 ||
                            !fresh ||
                            !latestBanknoteMatched ||
                            current?.recognized != true ||
                            current.upgrades.firstOrNull { it.kind == kind }?.availability !=
                            AvailabilityAppearance.AVAILABLE ||
                            liveProfile?.matches(
                                nativeWidth,
                                nativeHeight,
                                CalibrationStore.gameVersion(context)
                            ) !=
                            true ||
                            liveProfile.targets[kind] != point ||
                            GameAccessibilityService.instance?.isGameForeground() != true
                        ) {
                            false
                        } else {
                            GameAccessibilityService.instance?.tapOnce(
                                point.x * nativeWidth,
                                point.y * nativeHeight
                            ) { succeeded -> engine.gestureCompleted(id, succeeded) } == true
                        }
                    }
                    if (!accepted) engine.gestureCompleted(id, false)
                }
                true
            }
        }
    }

    override fun close() {
        closed = true
        scope.cancel()
        ocr.close()
        engine.cancel("Capture ended. Any dispatched gesture may finish; inspect the game.")
    }
}
