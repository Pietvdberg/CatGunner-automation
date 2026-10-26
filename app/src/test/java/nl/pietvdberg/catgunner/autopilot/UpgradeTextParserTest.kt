package nl.pietvdberg.catgunner.autopilot

import nl.pietvdberg.catgunner.autopilot.vision.UpgradeTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpgradeTextParserTest {
    @Test
    fun parsesObservedLevelNotation() {
        assertEquals(184, UpgradeTextParser.level("Lv.184"))
        assertEquals(197, UpgradeTextParser.level("Lv. 197"))
    }

    @Test
    fun ambiguousLevelsAreNotCorrectedOrGuessed() {
        for (value in listOf("184", "Lv.I84", "Lv.184 185", "Lv.-1", "Lv.9999999", "MAX")) {
            assertNull(value, UpgradeTextParser.level(value))
        }
    }

    @Test
    fun preservesObservedCostSuffixWithoutAssumingItsMagnitude() {
        assertEquals("2.62i", UpgradeTextParser.cost("2.62 i"))
        assertEquals("172i", UpgradeTextParser.cost("172i"))
    }

    @Test
    fun realMoneyAndAmbiguousCostsAreRejected() {
        for (value in listOf("€2.99", "$2.99", "Buy", "2,62i", "2.62|", "", "NaN", "-1")) {
            assertNull(value, UpgradeTextParser.cost(value))
        }
    }
}
