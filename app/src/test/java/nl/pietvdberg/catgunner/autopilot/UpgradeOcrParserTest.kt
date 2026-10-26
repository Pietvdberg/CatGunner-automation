package nl.pietvdberg.catgunner.autopilot

import nl.pietvdberg.catgunner.autopilot.domain.purchase.CalibrationProfile
import nl.pietvdberg.catgunner.autopilot.domain.purchase.GameReading
import nl.pietvdberg.catgunner.autopilot.domain.purchase.NormalizedPoint
import nl.pietvdberg.catgunner.autopilot.domain.purchase.SinglePurchaseEngine
import nl.pietvdberg.catgunner.autopilot.domain.state.ActionLease
import nl.pietvdberg.catgunner.autopilot.vision.AvailabilityAppearance
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeAppearance
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeKind
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeOcrParser
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeOcrToken
import nl.pietvdberg.catgunner.autopilot.vision.UpgradePanelEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpgradeOcrParserTest {
    private val evidence = UpgradePanelEvidence(
        UpgradeKind.entries.map { UpgradeAppearance(it, AvailabilityAppearance.AVAILABLE, 1.0) }
    )

    @Test
    fun groupsDistinctLevelAndCostTokensByObservedUpgradePositions() {
        val result = UpgradeOcrParser.parse(tokens(), evidence)
        assertNotNull(result.upgrades)
        assertEquals(184, result.upgrades?.get(UpgradeKind.POWER)?.level)
        assertEquals(186, result.upgrades?.get(UpgradeKind.MONEY)?.level)
        assertEquals("3.62i", result.upgrades?.get(UpgradeKind.SPEED)?.cost)
    }

    @Test
    fun missingPrefixDoesNotGuessLevelAndReportsExactField() {
        val tokens = tokens().filterNot { it.text == "Lv." && it.x < 200 }
        val result = UpgradeOcrParser.parse(tokens, evidence)
        assertNull(result.upgrades)
        assertTrue(result.detail.contains("POWER level"))
        assertTrue(result.detail.contains("level='184'"))
    }

    @Test
    fun textOutsideTheUpgradeRegionsIsIgnored() {
        val result = UpgradeOcrParser.parse(
            tokens() + UpgradeOcrToken("€9.99", 122f, 300f),
            evidence
        )
        assertNotNull(result.upgrades)
    }

    @Test
    fun invalidCostsAreNotSilentlyCorrected() {
        val changed = tokens().map { if (it.text == "2.62i") it.copy(text = "2.62|") else it }
        val result = UpgradeOcrParser.parse(changed, evidence)
        assertNull(result.upgrades)
        assertTrue(result.detail.contains("POWER cost"))
        assertTrue(result.detail.contains("2.62|"))
    }

    @Test
    fun handsetTranscriptProducesTwoStablePreconditionsBeforeOneDispatch() {
        val levels = listOf("Lvo166", "Lvo167", "LV165")
        val costs = listOf("8h", "11h", "5.79h")
        val tokens = UpgradeKind.entries.flatMap { kind ->
            val offset = kind.ordinal * 169
            listOf(
                UpgradeOcrToken(levels[kind.ordinal], 160f + offset, 1164f),
                UpgradeOcrToken(costs[kind.ordinal], 125f + offset, 1234f)
            )
        }
        val parsed = UpgradeOcrParser.parse(tokens, evidence)
        val values = requireNotNull(parsed.upgrades)
        assertEquals(166, values.getValue(UpgradeKind.POWER).level)
        assertEquals(167, values.getValue(UpgradeKind.SPEED).level)
        assertEquals(165, values.getValue(UpgradeKind.MONEY).level)
        val calibration = CalibrationProfile(
            1080,
            2424,
            42,
            UpgradeKind.entries.associateWith {
                NormalizedPoint((115f + it.ordinal * 169) / 570, 1198f / 1280)
            }
        )
        var now = 1000L
        val engine = SinglePurchaseEngine { now }
        assertTrue(engine.arm(UpgradeKind.POWER, calibration, ActionLease(1, 0)))
        var dispatches = 0
        val reading = GameReading(950, 1080, 2424, 42, values, true, true)
        engine.onReading(reading) { _, _, _ ->
            dispatches++
            true
        }
        assertEquals(0, dispatches)
        now = 1500
        engine.onReading(reading.copy(capturedAt = 1450)) { _, _, _ ->
            dispatches++
            true
        }
        assertEquals(1, dispatches)
    }

    private fun tokens(): List<UpgradeOcrToken> = UpgradeKind.entries.flatMap { kind ->
        val offset = kind.ordinal * 169
        listOf(
            UpgradeOcrToken("Lv.", 138f + offset, 1164f),
            UpgradeOcrToken("${184 + kind.ordinal}", 170f + offset, 1164f),
            UpgradeOcrToken(listOf("2.62i", "3.62i", "4.99i")[kind.ordinal], 125f + offset, 1234f)
        )
    }
}
