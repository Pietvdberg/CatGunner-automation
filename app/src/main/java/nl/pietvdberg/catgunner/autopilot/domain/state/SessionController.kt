package nl.pietvdberg.catgunner.autopilot.domain.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.pietvdberg.catgunner.autopilot.vision.UpgradePanelEvidence

/** All commands share this controller; gesture dispatch must hold an observation lease. */
class SessionController {
    private val mutableState = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    /** A generation prevents a late consent result or callback from reviving a stopped session. */
    @Synchronized
    fun begin(): Long? {
        if (state.value.mode !in setOf(SessionMode.STOPPED, SessionMode.ERROR)) return null
        val generation = state.value.generation + 1
        mutableState.value = SessionState(SessionMode.STARTING, generation)
        return generation
    }

    @Synchronized
    fun captureStarted(generation: Long): Boolean {
        if (state.value.generation != generation || state.value.mode != SessionMode.STARTING) {
            return false
        }
        mutableState.value = state.value.copy(mode = SessionMode.OBSERVING, captureActive = true)
        return true
    }

    @Synchronized
    fun pause() {
        if (state.value.mode == SessionMode.OBSERVING) {
            mutableState.value = state.value.copy(
                mode = SessionMode.PAUSED,
                visualEvidence = null,
                actionEpoch =
                state.value.actionEpoch + 1
            )
        }
    }

    @Synchronized
    fun resume(): Boolean {
        if (state.value.mode != SessionMode.PAUSED || !state.value.captureActive) return false
        mutableState.value = state.value.copy(mode = SessionMode.OBSERVING)
        return true
    }

    /** Reject samples that completed after pause, stop, or replacement of the session. */
    @Synchronized
    fun publishEvidence(generation: Long, evidence: UpgradePanelEvidence): Boolean {
        if (state.value.generation != generation ||
            state.value.mode != SessionMode.OBSERVING ||
            !state.value.captureActive
        ) {
            return false
        }
        mutableState.value = state.value.copy(visualEvidence = evidence)
        return true
    }

    @Synchronized
    fun observationLease(): ActionLease? = if (
        state.value.mode == SessionMode.OBSERVING && state.value.captureActive
    ) {
        ActionLease(state.value.generation, state.value.actionEpoch)
    } else {
        null
    }

    /** Serializes the final guard and actual dispatch against pause and stop. */
    @Synchronized
    fun dispatchIfCurrent(lease: ActionLease, dispatch: () -> Boolean): Boolean {
        if (observationLease() != lease) return false
        return dispatch()
    }

    @Synchronized
    fun fail(generation: Long, detail: String) {
        if (state.value.generation != generation || state.value.mode == SessionMode.STOPPED) return
        mutableState.value = state.value.copy(
            mode = SessionMode.ERROR,
            actionEpoch = state.value.actionEpoch + 1,
            captureActive = false,
            visualEvidence = null,
            detail = detail
        )
    }

    @Synchronized
    fun stop() {
        mutableState.value = SessionState(generation = state.value.generation + 1)
    }
}

enum class SessionMode { STOPPED, STARTING, OBSERVING, PAUSED, ERROR }

data class SessionState(
    val mode: SessionMode = SessionMode.STOPPED,
    val generation: Long = 0,
    val captureActive: Boolean = false,
    val detail: String = "",
    val visualEvidence: UpgradePanelEvidence? = null,
    val actionEpoch: Long = 0
)

data class ActionLease(val generation: Long, val epoch: Long)

object AutoPilotSession {
    val controller = SessionController()
}
