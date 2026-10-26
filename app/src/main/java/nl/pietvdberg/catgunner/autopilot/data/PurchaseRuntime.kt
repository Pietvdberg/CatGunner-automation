package nl.pietvdberg.catgunner.autopilot.data

import android.os.SystemClock
import nl.pietvdberg.catgunner.autopilot.domain.purchase.SinglePurchaseEngine
import nl.pietvdberg.catgunner.autopilot.domain.strategy.AutoPurchaseRunner

object PurchaseRuntime {
    val engine = SinglePurchaseEngine(SystemClock::elapsedRealtime)
    val autoRunner = AutoPurchaseRunner(engine, SystemClock::elapsedRealtime)
}
