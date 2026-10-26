package nl.pietvdberg.catgunner.autopilot.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityWindowInfo
import nl.pietvdberg.catgunner.autopilot.data.CalibrationStore
import nl.pietvdberg.catgunner.autopilot.data.LiveDiagnostics
import nl.pietvdberg.catgunner.autopilot.data.PurchaseRuntime
import nl.pietvdberg.catgunner.autopilot.domain.purchase.PurchasePhase
import nl.pietvdberg.catgunner.autopilot.domain.state.ServiceConnectionRegistry

class GameAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        connections.connect(this)
        LiveDiagnostics.service("Connected to Android accessibility")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (instance !== this) return
        val status = foregroundStatus()
        val foreground = status == ForegroundStatus.VERIFIED
        if (!foreground) {
            PurchaseRuntime.autoRunner.foregroundLost("Accessibility window event: $status")
        }
        if (!foreground &&
            PurchaseRuntime.engine.report.value.phase in setOf(
                PurchasePhase.DISPATCHED,
                PurchasePhase.VERIFYING
            )
        ) {
            PurchaseRuntime.engine.unsafe("Game left the foreground during the test.")
        }
    }

    fun isGameForeground(): Boolean = foregroundStatus() == ForegroundStatus.VERIFIED

    fun foregroundStatus(): ForegroundStatus = try {
        inspectForeground()
    } catch (error: RuntimeException) {
        ForegroundStatus.CONNECTION_ERROR
    }

    private fun inspectForeground(): ForegroundStatus {
        if (instance !== this) return ForegroundStatus.DISCONNECTED
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            return ForegroundStatus.LOCKED
        }
        if (!getSystemService(PowerManager::class.java).isInteractive) {
            return ForegroundStatus.SCREEN_OFF
        }
        val activeRoot = rootInActiveWindow ?: return ForegroundStatus.NO_ACTIVE_ROOT
        if (activeRoot.packageName?.toString() != CalibrationStore.GAME_PACKAGE) {
            return ForegroundStatus.OTHER_APP
        }
        val visible = windows
        if (visible.count { it.type == AccessibilityWindowInfo.TYPE_APPLICATION } != 1) {
            return ForegroundStatus.MULTIPLE_OR_MISSING_APP_WINDOWS
        }
        for (window in visible) {
            if (window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) {
                return ForegroundStatus.ACCESSIBILITY_OVERLAY
            }
            if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                return ForegroundStatus.KEYBOARD
            }
            if ((
                    window.type == AccessibilityWindowInfo.TYPE_APPLICATION ||
                        window.isActive ||
                        window.isFocused
                    ) &&
                window.root?.packageName?.toString() != CalibrationStore.GAME_PACKAGE
            ) {
                return ForegroundStatus.UNVERIFIED_WINDOW
            }
        }
        return ForegroundStatus.VERIFIED
    }

    fun tapOnce(x: Float, y: Float, completed: (Boolean) -> Unit): Boolean {
        if (!isGameForeground() || !x.isFinite() || !y.isFinite()) return false
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        return dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) = completed(true)
                override fun onCancelled(gestureDescription: GestureDescription?) = completed(false)
            },
            Handler(Looper.getMainLooper())
        )
    }

    override fun onInterrupt() {
        if (instance !== this) return
        PurchaseRuntime.autoRunner.stop("Accessibility interrupted. No automatic resume.")
        LiveDiagnostics.service("Android interrupted accessibility feedback")
        PurchaseRuntime.engine.unsafe("Accessibility was interrupted.")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (connections.disconnect(this)) {
            PurchaseRuntime.autoRunner.stop("Accessibility disconnected. No automatic resume.")
            LiveDiagnostics.service("Android unbound the service; check its accessibility setting")
            PurchaseRuntime.engine.unsafe("Accessibility service disconnected.")
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (connections.disconnect(this)) {
            PurchaseRuntime.autoRunner.stop("Accessibility service stopped. No automatic resume.")
            LiveDiagnostics.service("Android destroyed the accessibility service")
            PurchaseRuntime.engine.unsafe("Accessibility service stopped.")
        }
        super.onDestroy()
    }

    companion object {
        fun enabledInAndroid(context: Context): Boolean {
            val component = ComponentName(context, GameAccessibilityService::class.java)
            val manager = context.getSystemService(AccessibilityManager::class.java)
            return manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )
                .any { ComponentName.unflattenFromString(it.id) == component }
        }

        private val connections = ServiceConnectionRegistry<GameAccessibilityService>()
        val instance: GameAccessibilityService?
            get() = connections.current
    }
}

enum class ForegroundStatus {
    VERIFIED,
    DISCONNECTED,
    LOCKED,
    SCREEN_OFF,
    NO_ACTIVE_ROOT,
    OTHER_APP,
    MULTIPLE_OR_MISSING_APP_WINDOWS,
    ACCESSIBILITY_OVERLAY,
    KEYBOARD,
    UNVERIFIED_WINDOW,
    CONNECTION_ERROR
}
