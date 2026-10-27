package nl.pietvdberg.catgunner.autopilot.vision

import nl.pietvdberg.catgunner.autopilot.domain.purchase.OcrStages
import nl.pietvdberg.catgunner.autopilot.domain.purchase.UpgradeReading

/** Token centers are expressed in the observed 570x1280 reference layout. */
data class UpgradeOcrToken(
    val text: String,
    val x: Float,
    val y: Float,
    val provenLevel: Int? = null
)

data class UpgradeOcrResult(
    val upgrades: Map<UpgradeKind, UpgradeReading>?,
    val detail: String,
    val workMillis: Long = 0,
    val stages: OcrStages? = null
)

enum class OcrField { LEVEL, COST }

object UpgradeOcrParser {
    fun belongsTo(token: UpgradeOcrToken, kind: UpgradeKind, field: OcrField): Boolean {
        val offset = kind.ordinal * 169
        val left = if (field == OcrField.LEVEL) 118 else 95
        val right = if (field == OcrField.LEVEL) 192 else 174
        val top = if (field == OcrField.LEVEL) 1150 else 1220
        val bottom = if (field == OcrField.LEVEL) 1178 else 1248
        return token.x in (left + offset).toFloat()..(right + offset).toFloat() &&
            token.y in top.toFloat()..bottom.toFloat()
    }

    fun parse(tokens: List<UpgradeOcrToken>, evidence: UpgradePanelEvidence): UpgradeOcrResult {
        val readings = mutableMapOf<UpgradeKind, UpgradeReading>()
        val details = mutableListOf<String>()
        val missing = mutableListOf<String>()
        for (kind in UpgradeKind.entries) {
            fun region(field: OcrField): String = tokens.filter { belongsTo(it, kind, field) }
                .sortedBy { it.x }.joinToString("") { it.text }
            val levelTokens = tokens.filter {
                belongsTo(it, kind, OcrField.LEVEL) && !it.text.equals(kind.name, ignoreCase = true)
            }
            val rawLevel = levelTokens.sortedBy { it.x }.joinToString("") { it.text }
            val rawCost = region(OcrField.COST)
            val markerProof = levelTokens.singleOrNull()?.provenLevel
            val level = UpgradeTextParser.level(rawLevel) ?: markerProof
            val cost = UpgradeTextParser.cost(rawCost)
            details +=
                "${kind.name}: level='${rawLevel.take(
                    32
                )}' parsed=$level markerProof=$markerProof, " +
                "cost='${rawCost.take(32)}'"
            if (level == null) missing += "${kind.name} level"
            if (cost == null) missing += "${kind.name} cost"
            if (level != null && cost != null) {
                readings[kind] = UpgradeReading(
                    level,
                    cost,
                    evidence.upgrades.firstOrNull { it.kind == kind }?.availability ==
                        AvailabilityAppearance.AVAILABLE
                )
            }
        }
        val summary = details.joinToString("; ")
        return if (missing.isEmpty()) {
            UpgradeOcrResult(readings, summary)
        } else {
            UpgradeOcrResult(null, "Missing/ambiguous ${missing.joinToString()}. $summary")
        }
    }
}
