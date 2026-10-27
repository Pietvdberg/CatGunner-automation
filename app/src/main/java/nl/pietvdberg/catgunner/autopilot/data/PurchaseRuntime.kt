package nl.pietvdberg.catgunner.autopilot.data

import android.os.SystemClock
import nl.pietvdberg.catgunner.autopilot.domain.purchase.PurchasePhase
import nl.pietvdberg.catgunner.autopilot.domain.purchase.SinglePurchaseEngine
import nl.pietvdberg.catgunner.autopilot.domain.strategy.AutoPurchaseRunner

object PurchaseRuntime {
    val engine = SinglePurchaseEngine(SystemClock::elapsedRealtime)
    val autoRunner = AutoPurchaseRunner(engine, SystemClock::elapsedRealtime)
    fun purchasing(): Boolean = autoRunner.report.value.active ||
        engine.report.value.phase in setOf(
            PurchasePhase.ARMED,
            PurchasePhase.DISPATCHED,
            PurchasePhase.VERIFYING
        )
}
