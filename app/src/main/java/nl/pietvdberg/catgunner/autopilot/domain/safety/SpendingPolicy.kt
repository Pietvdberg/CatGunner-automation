package nl.pietvdberg.catgunner.autopilot.domain.safety

/** A currency identity must eventually come from verified calibration, not its color alone. */
enum class PurchaseCurrency { GREEN_BANKNOTES, BLUE_GEMS, REAL_MONEY, UNKNOWN }

/** Owner's first-version constraint. Passing this rule alone never authorizes a gesture. */
object SpendingPolicy {
    fun allows(currency: PurchaseCurrency): Boolean = currency == PurchaseCurrency.GREEN_BANKNOTES
}
