package nl.pietvdberg.catgunner.autopilot.data

import android.os.SystemClock
import nl.pietvdberg.catgunner.autopilot.domain.purchase.SinglePurchaseEngine

object PurchaseRuntime {
    val engine = SinglePurchaseEngine(SystemClock::elapsedRealtime)
}
