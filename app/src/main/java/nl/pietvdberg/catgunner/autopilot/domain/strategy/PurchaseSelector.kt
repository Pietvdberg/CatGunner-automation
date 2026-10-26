package nl.pietvdberg.catgunner.autopilot.domain.strategy

import java.math.BigDecimal
import nl.pietvdberg.catgunner.autopilot.domain.purchase.UpgradeReading
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeKind
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeTextParser

enum class PurchaseRule { LOWEST_COMPARABLE_COST, ROTATE }

data class PurchaseSelection(val kind: UpgradeKind?, val reason: String)

/** Transparent baseline only; no unobserved suffix scales or expected DPS gains are invented. */
object PurchaseSelector {
    private val price = Regex("^([0-9]{1,12}(?:[.][0-9]{1,6})?)([a-zA-Z]{0,3})$")

    fun choose(
        readings: Map<UpgradeKind, UpgradeReading>,
        rule: PurchaseRule,
        nextIndex: Int
    ): PurchaseSelection {
        if (readings.keys != UpgradeKind.entries.toSet()) {
            return PurchaseSelection(null, "Incomplete upgrade readings.")
        }
        val eligible = UpgradeKind.entries.filter { readings.getValue(it).available }
        if (eligible.isEmpty()) return PurchaseSelection(null, "No affordable calibrated upgrade.")
        if (rule == PurchaseRule.ROTATE) {
            val kind = (0 until UpgradeKind.entries.size)
                .map {
                    UpgradeKind.entries[
                        (Math.floorMod(nextIndex, UpgradeKind.entries.size) + it) %
                            UpgradeKind.entries.size
                    ]
                }
                .first { it in eligible }
            return PurchaseSelection(
                kind,
                "Next affordable upgrade in Power / Speed / Money rotation."
            )
        }
        val prices = eligible.associateWith { kind ->
            val text = UpgradeTextParser.cost(readings.getValue(kind).cost)
            text?.let { price.matchEntire(it) }
        }
        if (prices.values.any { it == null }) {
            return PurchaseSelection(null, "An affordable upgrade has an unreadable price.")
        }
        val suffixes = prices.values.map { it!!.groupValues[2] }.toSet()
        if (suffixes.size != 1) {
            return PurchaseSelection(
                null,
                "Price suffixes differ; their relative scales are unverified."
            )
        }
        val chosen = eligible.minBy { BigDecimal(prices.getValue(it)!!.groupValues[1]) }
        return PurchaseSelection(
            chosen,
            "Lowest affordable displayed cost with an identical price suffix."
        )
    }
}
