package nl.pietvdberg.catgunner.autopilot

import nl.pietvdberg.catgunner.autopilot.domain.safety.PurchaseCurrency
import nl.pietvdberg.catgunner.autopilot.domain.safety.SpendingPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpendingPolicyTest {
    @Test
    fun greenBanknotesAreTheOnlyEligibleCurrency() {
        assertTrue(SpendingPolicy.allows(PurchaseCurrency.GREEN_BANKNOTES))
        for (currency in PurchaseCurrency.entries.filter {
            it != PurchaseCurrency.GREEN_BANKNOTES
        }) {
            assertFalse(currency.name, SpendingPolicy.allows(currency))
        }
    }

    @Test
    fun realMoneyIsAlwaysForbidden() {
        assertFalse(SpendingPolicy.allows(PurchaseCurrency.REAL_MONEY))
    }
}
