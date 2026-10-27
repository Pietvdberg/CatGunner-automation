package nl.pietvdberg.catgunner.autopilot

import java.io.File
import nl.pietvdberg.catgunner.autopilot.domain.purchase.CalibrationProfile
import nl.pietvdberg.catgunner.autopilot.domain.purchase.GameReading
import nl.pietvdberg.catgunner.autopilot.domain.purchase.NormalizedPoint
import nl.pietvdberg.catgunner.autopilot.domain.purchase.PurchasePhase
import nl.pietvdberg.catgunner.autopilot.domain.purchase.SinglePurchaseEngine
import nl.pietvdberg.catgunner.autopilot.domain.purchase.UpgradeReading
import nl.pietvdberg.catgunner.autopilot.domain.state.ActionLease
import nl.pietvdberg.catgunner.autopilot.vision.AvailabilityAppearance
import nl.pietvdberg.catgunner.autopilot.vision.BoundedOcrToken
import nl.pietvdberg.catgunner.autopilot.vision.ExactTextCache
import nl.pietvdberg.catgunner.autopilot.vision.OcrContext
import nl.pietvdberg.catgunner.autopilot.vision.PixelSource
import nl.pietvdberg.catgunner.autopilot.vision.TextPixels
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeAppearance
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeKind
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeOcrResult
import nl.pietvdberg.catgunner.autopilot.vision.UpgradeOcrToken
import nl.pietvdberg.catgunner.autopilot.vision.UpgradePanelEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactTextCacheTest {
    private val context = OcrContext(ActionLease(1, 0), 42)
    private val cache = ExactTextCache()
    private val result = UpgradeOcrResult(
        UpgradeKind.entries.associateWith { UpgradeReading(280, "68.9m", true) },
        "Native OCR"
    )
    private val source = pixels()
    private val seed = TextPixels.capture(source)

    @Test
    fun identicalNewFrameCanReuseButDoesNotExtendOriginalExpiry() {
        remember()
        val reused = cache.observe(TextPixels.capture(source), context, 1500)!!
        assertTrue(reused.pixelRecheck)
        assertEquals(result.upgrades, reused.upgrades)
        assertNotNull(cache.observe(TextPixels.capture(source), context, 3000))
        assertNull(cache.observe(TextPixels.capture(source), context, 3001))
    }

    @Test
    fun noCacheWithoutCompleteCoveredNativeOcr() {
        cache.observe(seed, context, 1000)
        cache.remember(seed, context, 1000, result, cache.epoch, false)
        assertNull(cache.observe(seed, context, 1100))
        cache.remember(seed, context, 1000, UpgradeOcrResult(null, "Bad level"), cache.epoch, true)
        assertNull(cache.observe(seed, context, 1200))
    }

    @Test
    fun aSingleChangedPixelInEveryLevelAndPriceRegionForcesOcr() {
        for (region in seed.regions) {
            for ((x, y) in listOf(
                region.left to region.top,
                region.right - 1 to region.bottom - 1
            )) {
                cache.invalidate()
                remember()
                val changed = TextPixels.capture(pixels(change = x to y))
                assertFalse(seed.sameAs(changed))
                assertNull(cache.observe(changed, context, 1100))
            }
        }
    }

    @Test
    fun sourceTimeMustAdvanceAndContextChangesInvalidatePendingResults() {
        remember()
        assertNull(cache.observe(seed, context, 1000))
        remember()
        val oldEpoch = cache.epoch
        val other = context.copy(lease = ActionLease(1, 1))
        assertNull(cache.observe(seed, other, 1100))
        cache.remember(seed, context, 1000, result, oldEpoch, true)
        assertNull(cache.observe(seed, other, 1200))
        remember()
        assertNull(cache.observe(seed, context.copy(gameVersion = 43), 1100))
    }

    @Test
    fun gestureOrUnknownLayoutInvalidationRejectsLateNativeCompletion() {
        cache.observe(seed, context, 1000)
        val epoch = cache.epoch
        cache.invalidate()
        cache.observe(seed, context, 1100)
        cache.remember(seed, context, 1000, result, epoch, true)
        assertNull(cache.observe(seed, context, 1200))
    }

    @Test
    fun oldNativeResultCannotSeedCacheIfTextChangedWhileItWasRunning() {
        cache.observe(seed, context, 1000)
        val region = seed.regions.first()
        val changed = TextPixels.capture(pixels(change = region.left to region.top))
        cache.observe(changed, context, 1100)
        cache.remember(seed, context, 1000, result, cache.epoch, true)
        assertNull(cache.observe(changed, context, 1200))
    }

    @Test
    fun dimensionsAndObservedGameCorpusChangesCannotReuseInterpretation() {
        remember()
        assertNull(cache.observe(TextPixels.capture(pixels(1080, 2424)), context, 1100))
        val available = TextPixels.capture(fixture("available"))
        val changed = TextPixels.capture(fixture("power-unavailable"))
        assertFalse(available.sameAs(changed))
    }

    @Test
    fun allSixTokenBoundingBoxesMustFitTheWatchedNativeRegions() {
        val tokens = seed.regions.mapIndexed { index, region ->
            val level = index < 3
            BoundedOcrToken(
                UpgradeOcrToken(
                    if (level) "Lv.280" else "68.9m",
                    150f + index % 3 * 169,
                    if (level) 1164f else 1234f
                ),
                region.copy(
                    left = region.left + 2,
                    top = region.top + 2,
                    right = region.right - 2,
                    bottom = region.bottom - 2
                )
            )
        }
        assertTrue(seed.covers(tokens))
        assertFalse(seed.covers(tokens.dropLast(1)))
        val first = tokens.first()
        assertFalse(
            seed.covers(
                listOf(first.copy(bounds = first.bounds.copy(left = 0))) + tokens.drop(1)
            )
        )
    }

    @Test
    fun reuseRefreshesAvailabilityAndUnknownCurrentAppearanceCannotBecomeAReading() {
        remember()
        val reused = cache.observe(seed, context, 1100)!!
        val evidence = UpgradePanelEvidence(
            UpgradeKind.entries.map {
                UpgradeAppearance(it, AvailabilityAppearance.UNAVAILABLE, 1.0)
            }
        )
        assertTrue(reused.withCurrentAppearance(evidence).upgrades!!.values.all { !it.available })
        assertNull(reused.withCurrentAppearance(UpgradePanelEvidence()).upgrades)
    }

    @Test
    fun incompleteNativeReadingsCannotSeedAnyReuse() {
        cache.observe(seed, context, 1000)
        cache.remember(
            seed,
            context,
            1000,
            result.copy(upgrades = result.upgrades!!.filterKeys { it == UpgradeKind.POWER }),
            cache.epoch,
            true
        )
        assertNull(cache.observe(seed, context, 1100))
    }

    @Test
    fun fullPurchaseUsesNativeReadingsForNewTextAndFreshPixelChecksForSecondFrames() {
        var now = 1000L
        var taps = 0
        val engine = SinglePurchaseEngine { now }
        val profile = CalibrationProfile(
            570,
            1280,
            42,
            UpgradeKind.entries.associateWith {
                NormalizedPoint((115f + it.ordinal * 169) / 570, 1198f / 1280)
            }
        )
        fun reading(result: UpgradeOcrResult, capturedAt: Long) = GameReading(
            capturedAt,
            570,
            1280,
            42,
            result.upgrades!!,
            true,
            true
        )
        assertTrue(engine.arm(UpgradeKind.POWER, profile, context.lease))
        remember()
        now = 1600
        engine.onReading(reading(result, 1000)) { _, _, _ -> error("Only one frame") }
        now = 1700
        val beforeRecheck = cache.observe(TextPixels.capture(source), context, now)!!
        engine.onReading(reading(beforeRecheck, now)) { id, _, _ ->
            engine.executeGesture(id) {
                cache.invalidate()
                taps++
                true
            }
        }
        assertEquals(1, taps)
        now = 1760
        engine.gestureCompleted(engine.report.value.id, true)
        val region = seed.regions.first()
        val after = TextPixels.capture(pixels(change = region.left to region.top))
        assertNull(cache.observe(after, context, 1800))
        val changedResult = result.copy(
            upgrades = result.upgrades!!.mapValues { (kind, value) ->
                if (kind == UpgradeKind.POWER) value.copy(level = 281, cost = "70m") else value
            }
        )
        cache.remember(after, context, 1800, changedResult, cache.epoch, true)
        now = 2400
        engine.onReading(reading(changedResult, 1800)) { _, _, _ -> error("No verification tap") }
        assertEquals(PurchasePhase.VERIFYING, engine.report.value.phase)
        now = 2500
        val afterRecheck = cache.observe(after, context, now)!!
        engine.onReading(reading(afterRecheck, now)) { _, _, _ -> error("No verification tap") }
        assertEquals(PurchasePhase.VERIFIED, engine.report.value.phase)
        assertEquals(2, engine.report.value.verificationSamples)
        assertEquals(1, taps)
        assertTrue(beforeRecheck.pixelRecheck && afterRecheck.pixelRecheck)
    }

    private fun remember() {
        cache.observe(seed, context, 1000)
        cache.remember(seed, context, 1000, result, cache.epoch, true)
    }

    private fun pixels(width: Int = 570, height: Int = 1280, change: Pair<Int, Int>? = null) =
        object : PixelSource {
            override val width = width
            override val height = height
            override fun rgbAt(x: Int, y: Int): Int = if (x to y == change) 0x112234 else 0x112233
        }

    private fun fixture(name: String): PixelSource {
        val bytes = File("src/test/resources/vision/$name.rgb").readBytes()
        return object : PixelSource {
            override val width = 570
            override val height = 1280
            override fun rgbAt(x: Int, y: Int): Int {
                val i = ((y - 1130) * 570 + x) * 3
                return ((bytes[i].toInt() and 255) shl 16) or
                    ((bytes[i + 1].toInt() and 255) shl 8) or (bytes[i + 2].toInt() and 255)
            }
        }
    }
}
