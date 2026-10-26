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
    fun acceptsTheObservedPrefixSeparatorWithoutGuessingAnyDigits() {
        assertEquals(166, UpgradeTextParser.level("Lvo166"))
        assertEquals(167, UpgradeTextParser.level("Lvo167"))
        assertEquals(165, UpgradeTextParser.level("LV165"))
        assertEquals(166, UpgradeTextParser.level("LvO166"))
    }

    @Test
    fun ambiguousCharactersInsideTheLevelRemainRejected() {
        for (value in listOf("LvoI66", "Lvo16O", "Lvo", "Lvo166.5", "Lv.o166", "Lvv166")) {
            assertNull(value, UpgradeTextParser.level(value))
        }
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
