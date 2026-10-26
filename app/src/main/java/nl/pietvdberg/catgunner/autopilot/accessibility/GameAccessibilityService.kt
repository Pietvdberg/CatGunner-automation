package nl.pietvdberg.catgunner.autopilot.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import nl.pietvdberg.catgunner.autopilot.data.CalibrationStore
import nl.pietvdberg.catgunner.autopilot.data.PurchaseRuntime
import nl.pietvdberg.catgunner.autopilot.domain.purchase.PurchasePhase

class GameAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isGameForeground() &&
            PurchaseRuntime.engine.report.value.phase in setOf(
                PurchasePhase.DISPATCHED,
                PurchasePhase.VERIFYING
            )
        ) {
            PurchaseRuntime.engine.unsafe("Game left the foreground during the test.")
        }
    }

    fun isGameForeground(): Boolean {
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
            !getSystemService(PowerManager::class.java).isInteractive
        ) {
            return false
        }
        val activeRoot = rootInActiveWindow ?: return false
        if (activeRoot.packageName?.toString() != CalibrationStore.GAME_PACKAGE) return false
        val visible = windows
        if (visible.count { it.type == AccessibilityWindowInfo.TYPE_APPLICATION } != 1) return false
        return visible.none { window ->
            window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY ||
                window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD ||
                (
                    window.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                        window.root?.packageName?.toString() != CalibrationStore.GAME_PACKAGE
                    ) ||
                (
                    (window.isActive || window.isFocused) &&
                        window.root?.packageName?.toString() != CalibrationStore.GAME_PACKAGE
                    )
        }
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
        PurchaseRuntime.engine.unsafe("Accessibility was interrupted.")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        PurchaseRuntime.engine.unsafe("Accessibility was disabled.")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        PurchaseRuntime.engine.unsafe("Accessibility service stopped.")
        super.onDestroy()
    }

    companion object {
        var instance: GameAccessibilityService? = null
            private set
    }
}
