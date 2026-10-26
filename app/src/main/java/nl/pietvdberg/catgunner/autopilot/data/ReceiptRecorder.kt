package nl.pietvdberg.catgunner.autopilot.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nl.pietvdberg.catgunner.autopilot.domain.purchase.PurchasePhase

/** Process-scoped writer survives capture-service stop; no jobs or gestures resume on restart. */
object ReceiptRecorder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var installed = false

    @Synchronized
    fun start(context: Context) {
        if (installed) return
        installed = true
        val journal = PurchaseJournal(context.applicationContext)
        scope.launch {
            PurchaseRuntime.engine.report.collect { report ->
                if (report.phase !in setOf(PurchasePhase.IDLE, PurchasePhase.ARMED)) {
                    try {
                        journal.append(report)
                    } catch (error: Exception) {
                        PurchaseRuntime.engine.unsafe("Local receipt storage failed. No retry.")
                    }
                }
            }
        }
    }
}
