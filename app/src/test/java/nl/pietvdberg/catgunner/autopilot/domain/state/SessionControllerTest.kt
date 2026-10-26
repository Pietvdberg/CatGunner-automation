package nl.pietvdberg.catgunner.autopilot.domain.state

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import nl.pietvdberg.catgunner.autopilot.vision.UpgradePanelEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionControllerTest {
    private val controller = SessionController()

    @Test
    fun consentMustCompleteBeforeObservation() {
        val generation = controller.begin()!!
        assertEquals(SessionMode.STARTING, controller.state.value.mode)
        assertFalse(controller.resume())
        assertTrue(controller.captureStarted(generation))
        assertEquals(SessionMode.OBSERVING, controller.state.value.mode)
    }

    @Test
    fun duplicateStartDoesNotCreateAnotherSession() {
        controller.begin()
        assertNull(controller.begin())
    }

    @Test
    fun pauseAndResumeRetainTheCaptureSession() {
        observe()
        controller.pause()
        assertEquals(SessionMode.PAUSED, controller.state.value.mode)
        assertTrue(controller.state.value.captureActive)
        assertTrue(controller.resume())
        assertEquals(SessionMode.OBSERVING, controller.state.value.mode)
    }

    @Test
    fun stopInvalidatesPendingConsent() {
        val generation = controller.begin()!!
        controller.stop()
        assertFalse(controller.captureStarted(generation))
        assertEquals(SessionMode.STOPPED, controller.state.value.mode)
    }

    @Test
    fun captureRevocationRequiresNewConsent() {
        val generation = observe()
        controller.pause()
        controller.fail(generation, "Capture revoked")
        assertFalse(controller.resume())
        assertFalse(controller.state.value.captureActive)
        assertEquals(SessionMode.ERROR, controller.state.value.mode)
        assertNotNull(controller.begin())
    }

    @Test
    fun callbackFromOldSessionCannotInvalidateNewSession() {
        val old = observe()
        controller.stop()
        val current = observe()
        controller.fail(old, "Stale callback")
        assertEquals(SessionMode.OBSERVING, controller.state.value.mode)
        assertEquals(current, controller.state.value.generation)
    }

    @Test
    fun destroyedServiceDoesNotTurnExplicitStopIntoError() {
        val generation = observe()
        controller.stop()
        controller.fail(generation, "Service destroyed")
        assertEquals(SessionMode.STOPPED, controller.state.value.mode)
        assertFalse(controller.state.value.captureActive)
    }

    @Test
    fun pauseDoesNotAcceptConsentAsAResumeCommand() {
        val generation = observe()
        controller.pause()
        assertFalse(controller.captureStarted(generation))
        assertEquals(SessionMode.PAUSED, controller.state.value.mode)
    }

    @Test
    fun pauseWhileStoppedDoesNotCreateSession() {
        controller.pause()
        assertFalse(controller.resume())
        assertEquals(SessionMode.STOPPED, controller.state.value.mode)
    }

    @Test
    fun repeatedPauseIsIdempotent() {
        observe()
        controller.pause()
        val paused = controller.state.value
        controller.pause()
        assertEquals(paused, controller.state.value)
    }

    @Test
    fun stopRacingWithConsentAlwaysEndsStopped() {
        val executor = Executors.newFixedThreadPool(2)
        try {
            repeat(100) {
                val generation = controller.begin()!!
                val ready = CountDownLatch(1)
                val consent = executor.submit {
                    ready.await()
                    controller.captureStarted(generation)
                }
                val stop = executor.submit {
                    ready.await()
                    controller.stop()
                }
                ready.countDown()
                consent.get(2, TimeUnit.SECONDS)
                stop.get(2, TimeUnit.SECONDS)
                assertEquals(SessionMode.STOPPED, controller.state.value.mode)
                assertFalse(controller.resume())
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun stopRacingWithResumeCannotReviveCapture() {
        val executor = Executors.newFixedThreadPool(2)
        try {
            repeat(100) {
                observe()
                controller.pause()
                val ready = CountDownLatch(1)
                val resume = executor.submit {
                    ready.await()
                    controller.resume()
                }
                val stop = executor.submit {
                    ready.await()
                    controller.stop()
                }
                ready.countDown()
                resume.get(2, TimeUnit.SECONDS)
                stop.get(2, TimeUnit.SECONDS)
                assertEquals(SessionMode.STOPPED, controller.state.value.mode)
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun pauseClearsEvidenceAndRejectsLateFrameResults() {
        val generation = observe()
        val evidence = UpgradePanelEvidence()
        assertTrue(controller.publishEvidence(generation, evidence))
        controller.pause()
        assertNull(controller.state.value.visualEvidence)
        assertFalse(controller.publishEvidence(generation, evidence))
    }

    @Test
    fun oldSessionCannotPublishEvidenceIntoNewSession() {
        val old = observe()
        controller.stop()
        observe()
        assertFalse(controller.publishEvidence(old, UpgradePanelEvidence()))
        assertNull(controller.state.value.visualEvidence)
    }

    @Test
    fun pendingConsentCannotPublishEvidence() {
        val generation = controller.begin()!!
        assertFalse(controller.publishEvidence(generation, UpgradePanelEvidence()))
    }

    @Test
    fun pauseAndResumeInvalidateAnArmedObservationLease() {
        observe()
        val lease = controller.observationLease()!!
        controller.pause()
        controller.resume()
        var dispatched = false
        assertFalse(
            controller.dispatchIfCurrent(lease) {
                dispatched = true
                true
            }
        )
        assertFalse(dispatched)
    }

    @Test
    fun stopAndRestartInvalidateAnArmedObservationLease() {
        observe()
        val lease = controller.observationLease()!!
        controller.stop()
        observe()
        assertFalse(controller.dispatchIfCurrent(lease) { error("Stale dispatch") })
    }

    @Test
    fun currentLeaseDispatchesAtMostUntilPause() {
        observe()
        val lease = controller.observationLease()!!
        var count = 0
        assertTrue(
            controller.dispatchIfCurrent(lease) {
                count++
                true
            }
        )
        controller.pause()
        assertFalse(
            controller.dispatchIfCurrent(lease) {
                count++
                true
            }
        )
        assertEquals(1, count)
    }

    private fun observe(): Long {
        val generation = controller.begin()!!
        assertTrue(controller.captureStarted(generation))
        return generation
    }
}
