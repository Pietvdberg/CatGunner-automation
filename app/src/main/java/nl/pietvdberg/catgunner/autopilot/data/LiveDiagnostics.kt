package nl.pietvdberg.catgunner.autopilot.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import nl.pietvdberg.catgunner.autopilot.vision.UpgradePanelEvidence

data class DiagnosticState(
    val live: String = "No sample",
    val foreground: String = "Not checked",
    val lastGame: String = "No verified game sample",
    val ocr: String = "No OCR result",
    val service: String = "No service connection observed",
    val dispatch: String = "No dispatch check performed"
)

/** Text-only local diagnostics. No full-screen OCR, image files, or automatic export. */
object LiveDiagnostics {
    private val mutableState = MutableStateFlow(DiagnosticState())
    val state = mutableState.asStateFlow()

    fun dispatch(detail: String) {
        mutableState.update { it.copy(dispatch = detail) }
    }

    fun foreground(reason: String) {
        mutableState.update { it.copy(foreground = reason) }
    }

    fun live(evidence: UpgradePanelEvidence) {
        mutableState.update { it.copy(live = describe(evidence)) }
    }
    fun game(evidence: UpgradePanelEvidence) {
        mutableState.update { it.copy(lastGame = describe(evidence)) }
    }
    fun ocr(detail: String) {
        mutableState.update { it.copy(ocr = detail) }
    }
    fun service(detail: String) {
        mutableState.update { it.copy(service = detail) }
    }

    fun describe(evidence: UpgradePanelEvidence): String {
        val scores = evidence.upgrades.joinToString("; ") {
            "${it.kind}: ${it.availability}, label match ${ (it.labelMatchRatio * 100).toInt() }%"
        }
        return "${evidence.unknownReason ?: "MATCH"} $scores".trim()
    }
}
