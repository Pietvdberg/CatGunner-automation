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
import nl.pietvdberg.catgunner.autopilot.accessibility.ForegroundStatus
import nl.pietvdberg.catgunner.autopilot.accessibility.GameAccessibilityService
import nl.pietvdberg.catgunner.autopilot.data.CalibrationStore
import nl.pietvdberg.catgunner.autopilot.data.LiveDiagnostics
import nl.pietvdberg.catgunner.autopilot.data.PreviewStore
import nl.pietvdberg.catgunner.autopilot.data.PurchaseJournal
import nl.pietvdberg.catgunner.autopilot.data.PurchaseRuntime
import nl.pietvdberg.catgunner.autopilot.data.ReadingStore
import nl.pietvdberg.catgunner.autopilot.domain.purchase.DispatchBlock
import nl.pietvdberg.catgunner.autopilot.domain.purchase.DispatchGuard
import nl.pietvdberg.catgunner.autopilot.domain.purchase.DispatchSnapshot
import nl.pietvdberg.catgunner.autopilot.domain.purchase.GameReading
import nl.pietvdberg.catgunner.autopilot.domain.purchase.PurchasePhase
import nl.pietvdberg.catgunner.autopilot.domain.state.AutoPilotSession
import nl.pietvdberg.catgunner.autopilot.vision.BanknoteMatcher
import nl.pietvdberg.catgunner.autopilot.vision.OcrContext
import nl.pietvdberg.catgunner.autopilot.vision.PixelSource
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeKind
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
    private var ocrSamples = 0L
    private var readableSamples = 0L
    private var latestBanknoteMatches: Map<UpgradeKind, Boolean> = emptyMap()

    fun sample(pixels: PixelSource, generation: Long, capturedAt: Long) {
        if (closed) return
        engine.tick()
        val evidence = detector.detect(pixels)
        controller.publishEvidence(generation, evidence)
        val accessibility = GameAccessibilityService.instance
        val foregroundStatus = accessibility?.foregroundStatus() ?: ForegroundStatus.DISCONNECTED
        val foreground = foregroundStatus == ForegroundStatus.VERIFIED
        LiveDiagnostics.foreground(foregroundStatus.name)
        val requestId = engine.report.value.id
        LiveDiagnostics.live(evidence)
        if (!foreground) {
            foregroundLost("Foreground check: ${foregroundStatus.name}")
            return
        }
        LiveDiagnostics.game(evidence)
        if (!evidence.recognized) {
            ocr.invalidate()
            engine.waitForEvidence("Upgrade controls: ${LiveDiagnostics.describe(evidence)}")
            return
        }
        lastRecognizedAt = capturedAt
        val version = CalibrationStore.gameVersion(context)
        if (!PurchaseRuntime.purchasing()) PreviewStore.update(pixels, evidence, version)
        val actionLease = controller.observationLease() ?: return
        val nativeWidth = pixels.width
        val nativeHeight = pixels.height
        val banknoteMatches = UpgradeKind.entries
            .associateWith { banknote.matches(pixels, it) }
        latestBanknoteMatches = banknoteMatches
        ocr.sample(pixels, evidence, OcrContext(actionLease, version), capturedAt) { result ->
            val ageMillis = SystemClock.elapsedRealtime() - capturedAt
            ocrSamples++
            if (result.upgrades != null) readableSamples++
            LiveDiagnostics.ocr(
                "Result age (ms): $ageMillis. Readable=$readableSamples/$ocrSamples. ${result.detail}"
            )
            val upgrades = result.upgrades
            if (closed ||
                controller.observationLease() != actionLease ||
                engine.report.value.id != requestId
            ) {
                return@sample
            }
            engine.recordOcr(
                requestId,
                capturedAt,
                result.workMillis,
                ageMillis,
                upgrades != null,
                result.pixelRecheck,
                result.recheckStatus
            )
            val callbackForeground = GameAccessibilityService.instance?.foregroundStatus()
                ?: ForegroundStatus.DISCONNECTED
            if (callbackForeground != ForegroundStatus.VERIFIED) {
                foregroundLost("OCR completion foreground check: $callbackForeground")
                return@sample
            }
            if (upgrades == null) {
                engine.waitForText("OCR is not ready: ${result.detail}")
                return@sample
            }
            if (SystemClock.elapsedRealtime() - capturedAt > engine.maximumReadingAgeMillis()) {
                engine.waitForText("OCR frame is stale; waiting for a fresh reading.")
                return@sample
            }
            val candidate = GameReading(
                capturedAt,
                nativeWidth,
                nativeHeight,
                version,
                upgrades,
                false,
                true
            )
            PurchaseRuntime.autoRunner.prepare(candidate, controller.observationLease())
            val kind = engine.report.value.kind
            val reading = candidate.copy(banknoteIconsMatched = banknoteMatches[kind] == true)
            ReadingStore.update(reading)
            engine.onReading(reading) { id, lease, point ->
                val request = engine.report.value
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { PurchaseJournal(context).append(request) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        LiveDiagnostics.dispatch(
                            "Receipt write failed before send: ${error.javaClass.simpleName}"
                        )
                        engine.rejectQueued(id, request.dispatchAttempt, "Receipt write failed.")
                        return@launch
                    }
                    var checkedCurrentSession = false
                    controller.dispatchIfCurrent(lease) {
                        checkedCurrentSession = true
                        val liveProfile = CalibrationStore(context).load()
                        val current = controller.state.value.visualEvidence
                        val observationAge = SystemClock.elapsedRealtime() - capturedAt
                        val visualAge = SystemClock.elapsedRealtime() - lastRecognizedAt
                        val decision = DispatchGuard.evaluate(
                            DispatchSnapshot(
                                !closed &&
                                    engine.report.value.id == id &&
                                    engine.report.value.phase == PurchasePhase.DISPATCHED &&
                                    engine.report.value.dispatchAttempt == request.dispatchAttempt,
                                liveProfile?.matches(
                                    nativeWidth,
                                    nativeHeight,
                                    CalibrationStore.gameVersion(context)
                                ) ==
                                    true,
                                liveProfile?.targets?.get(kind) == point,
                                GameAccessibilityService.instance?.isGameForeground() == true,
                                latestBanknoteMatches[kind] == true,
                                observationAge, visualAge, current?.recognized == true,
                                current?.upgrades?.firstOrNull { it.kind == kind }?.availability
                            )
                        )
                        LiveDiagnostics.dispatch(
                            listOf(
                                "Attempt ${request.dispatchAttempt}: $decision",
                                "OCR age=$observationAge ms",
                                "visual age=$visualAge ms",
                                "target=$kind",
                                "banknote=${latestBanknoteMatches[kind]}"
                            ).joinToString("; ")
                        )
                        when {
                            decision == DispatchBlock.NONE -> engine.executeGesture(
                                id,
                                request.dispatchAttempt
                            ) {
                                ocr.invalidate()
                                val accepted = GameAccessibilityService.instance?.tapOnce(
                                    point.x * nativeWidth,
                                    point.y * nativeHeight
                                ) { succeeded ->
                                    LiveDiagnostics.dispatch(
                                        "Android gesture callback: completed=$succeeded"
                                    )
                                    engine.gestureCompleted(id, succeeded, request.dispatchAttempt)
                                } == true
                                LiveDiagnostics.dispatch(
                                    "Gesture service accepted=$accepted; target=$kind"
                                )
                                accepted
                            }
                            decision.canWaitBeforeTap -> {
                                engine.deferQueued(id, request.dispatchAttempt, decision.name)
                                false
                            }
                            else -> {
                                engine.rejectQueued(id, request.dispatchAttempt, decision.name)
                                false
                            }
                        }
                    }
                    if (!checkedCurrentSession) {
                        LiveDiagnostics.dispatch("No tap: capture session paused or replaced.")
                        engine.rejectQueued(id, request.dispatchAttempt, "Capture session changed.")
                    }
                }
                true
            }
            PurchaseRuntime.autoRunner.reconcile()
        }
    }

    private fun foregroundLost(reason: String) {
        ocr.invalidate()
        PurchaseRuntime.autoRunner.foregroundLost(reason)
        if (engine.report.value.phase == PurchasePhase.ARMED) {
            engine.waitForEvidence(reason)
        } else {
            engine.unsafe(reason)
        }
    }

    override fun close() {
        closed = true
        PurchaseRuntime.autoRunner.stop("Capture ended. No automatic resume.")
        scope.cancel()
        ocr.close()
        engine.cancel("Capture ended. Any dispatched gesture may finish; inspect the game.")
    }
}
