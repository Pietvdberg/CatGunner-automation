package nl.pietvdberg.catgunner.autopilot.vision

import nl.pietvdberg.catgunner.autopilot.domain.purchase.UpgradeReading

/** Token centers are expressed in the observed 570x1280 reference layout. */
data class UpgradeOcrToken(val text: String, val x: Float, val y: Float)

data class UpgradeOcrResult(
    val upgrades: Map<UpgradeKind, UpgradeReading>?,
    val detail: String,
    val workMillis: Long = 0,
    val pixelRecheck: Boolean = false,
    val recheckStatus: RecheckStatus? = null
) {
    fun withCurrentAppearance(evidence: UpgradePanelEvidence): UpgradeOcrResult {
        if (!evidence.recognized) {
            return copy(
                upgrades = null,
                detail = "Unknown current appearance"
            )
        }
        return copy(
            upgrades = upgrades?.mapValues { (kind, reading) ->
                reading.copy(
                    available = evidence.upgrades.first { it.kind == kind }.availability ==
                        AvailabilityAppearance.AVAILABLE
                )
            }
        )
    }
}

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
            val rawLevel = region(OcrField.LEVEL)
            val rawCost = region(OcrField.COST)
            val level = UpgradeTextParser.level(rawLevel)
            val cost = UpgradeTextParser.cost(rawCost)
            details += "${kind.name}: level='${rawLevel.take(32)}' parsed=$level, " +
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
