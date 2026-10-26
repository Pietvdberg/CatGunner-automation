package nl.pietvdberg.catgunner.autopilot.domain.strategy

import nl.pietvdberg.catgunner.autopilot.domain.purchase.UpgradeReading
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PurchaseSelectorTest {
    @Test
    fun selectsTheLowestObservedComparablePrice() {
        val result = PurchaseSelector.choose(
            prices("221", "801", "161"),
            PurchaseRule.LOWEST_COMPARABLE_COST,
            0
        )
        assertEquals(UpgradeKind.MONEY, result.kind)
    }

    @Test
    fun comparesDecimalAmountsExactlyWithinOneSuffix() {
        val result = PurchaseSelector.choose(
            prices("0.10h", "0.09h", "5.79h"),
            PurchaseRule.LOWEST_COMPARABLE_COST,
            0
        )
        assertEquals(UpgradeKind.SPEED, result.kind)
    }

    @Test
    fun neverInventsOrderingForDifferentSuffixesOrCase() {
        for (values in listOf(prices("2h", "1i", "3h"), prices("2h", "1H", "3h"))) {
            assertNull(PurchaseSelector.choose(values, PurchaseRule.LOWEST_COMPARABLE_COST, 0).kind)
        }
    }

    @Test
    fun unavailableUpgradesAreNotSelected() {
        val values = prices("221", "801", "161").toMutableMap()
        values[UpgradeKind.MONEY] = values.getValue(UpgradeKind.MONEY).copy(available = false)
        assertEquals(
            UpgradeKind.POWER,
            PurchaseSelector.choose(values, PurchaseRule.LOWEST_COMPARABLE_COST, 0).kind
        )
    }

    @Test
    fun incompleteOrUnreadablePricesCannotBeRanked() {
        assertNull(PurchaseSelector.choose(emptyMap(), PurchaseRule.LOWEST_COMPARABLE_COST, 0).kind)
        assertNull(
            PurchaseSelector.choose(
                prices("€2", "1", "3"),
                PurchaseRule.LOWEST_COMPARABLE_COST,
                0
            ).kind
        )
    }

    @Test
    fun rotationSkipsUnavailableControlsWithoutComparingSuffixes() {
        val values = prices("5i", "1h", "2").toMutableMap()
        values[UpgradeKind.SPEED] = values.getValue(UpgradeKind.SPEED).copy(available = false)
        assertEquals(
            UpgradeKind.MONEY,
            PurchaseSelector.choose(values, PurchaseRule.ROTATE, 1).kind
        )
    }

    @Test
    fun noAffordableUpgradeMeansNoSelectionForEitherRule() {
        val unavailable = prices("1", "2", "3").mapValues { it.value.copy(available = false) }
        for (rule in PurchaseRule.entries) {
            assertNull(PurchaseSelector.choose(unavailable, rule, 0).kind)
        }
    }

    private fun prices(
        power: String,
        speed: String,
        money: String
    ): Map<UpgradeKind, UpgradeReading> = UpgradeKind.entries.associateWith {
        UpgradeReading(100, listOf(power, speed, money)[it.ordinal], true)
    }
}
