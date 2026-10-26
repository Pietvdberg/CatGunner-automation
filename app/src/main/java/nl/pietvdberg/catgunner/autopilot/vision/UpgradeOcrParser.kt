package nl.pietvdberg.catgunner.autopilot.vision

import nl.pietvdberg.catgunner.autopilot.domain.purchase.UpgradeReading

/** Token centers are expressed in the observed 570x1280 reference layout. */
data class UpgradeOcrToken(val text: String, val x: Float, val y: Float)

data class UpgradeOcrResult(val upgrades: Map<UpgradeKind, UpgradeReading>?, val detail: String)

object UpgradeOcrParser {
    fun parse(tokens: List<UpgradeOcrToken>, evidence: UpgradePanelEvidence): UpgradeOcrResult {
        val readings = mutableMapOf<UpgradeKind, UpgradeReading>()
        val details = mutableListOf<String>()
        val missing = mutableListOf<String>()
        for (kind in UpgradeKind.entries) {
            val offset = kind.ordinal * 169
            fun region(left: Int, right: Int, low: Int, high: Int): String = tokens.filter {
                it.x in (left + offset).toFloat()..(right + offset).toFloat() &&
                    it.y in low.toFloat()..high.toFloat()
            }.sortedBy { it.x }.joinToString("") { it.text }
            val rawLevel = region(118, 192, 1150, 1178)
            val rawCost = region(95, 174, 1220, 1248)
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
