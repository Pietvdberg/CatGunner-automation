package nl.pietvdberg.catgunner.autopilot.domain.purchase

import nl.pietvdberg.catgunner.autopilot.vision.AvailabilityAppearance

data class DispatchSnapshot(
    val requestCurrent: Boolean,
    val calibrationMatches: Boolean,
    val targetMatches: Boolean,
    val foregroundVerified: Boolean,
    val banknoteVerified: Boolean,
    val observationAgeMillis: Long,
    val visualAgeMillis: Long,
    val layoutRecognized: Boolean,
    val availability: AvailabilityAppearance?
)

enum class DispatchBlock(val canWaitBeforeTap: Boolean = false) {
    NONE,
    REQUEST_CHANGED,
    CALIBRATION_CHANGED,
    TARGET_CHANGED,
    FOREGROUND_LOST,
    INVALID_TIMESTAMP,
    BANKNOTE_UNVERIFIED,
    TARGET_UNAVAILABLE,
    OCR_TOO_OLD(true),
    VISUAL_FRAME_TOO_OLD(true),
    LAYOUT_UNKNOWN(true),
    TARGET_UNKNOWN(true)
}

/** A transient miss can only defer a request that has never sent a gesture. */
object DispatchGuard {
    fun evaluate(snapshot: DispatchSnapshot): DispatchBlock = when {
        !snapshot.requestCurrent -> DispatchBlock.REQUEST_CHANGED
        !snapshot.calibrationMatches -> DispatchBlock.CALIBRATION_CHANGED
        !snapshot.targetMatches -> DispatchBlock.TARGET_CHANGED
        !snapshot.foregroundVerified -> DispatchBlock.FOREGROUND_LOST
        snapshot.observationAgeMillis < 0 || snapshot.visualAgeMillis < 0 ->
            DispatchBlock.INVALID_TIMESTAMP
        !snapshot.banknoteVerified -> DispatchBlock.BANKNOTE_UNVERIFIED
        snapshot.observationAgeMillis > 750 -> DispatchBlock.OCR_TOO_OLD
        snapshot.visualAgeMillis > 500 -> DispatchBlock.VISUAL_FRAME_TOO_OLD
        !snapshot.layoutRecognized -> DispatchBlock.LAYOUT_UNKNOWN
        snapshot.availability == AvailabilityAppearance.UNAVAILABLE ->
            DispatchBlock.TARGET_UNAVAILABLE
        snapshot.availability != AvailabilityAppearance.AVAILABLE -> DispatchBlock.TARGET_UNKNOWN
        else -> DispatchBlock.NONE
    }
}
