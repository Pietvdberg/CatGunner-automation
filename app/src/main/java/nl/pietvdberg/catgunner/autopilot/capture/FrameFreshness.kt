package nl.pietvdberg.catgunner.autopilot.capture

/** SurfaceFlinger timestamps use monotonic time; action deadlines use elapsed realtime. */
object FrameFreshness {
    fun capturedAtMillis(timestampNanos: Long, monotonicNow: Long, elapsedNowMillis: Long): Long? {
        if (timestampNanos <= 0 || timestampNanos > monotonicNow) return null
        val ageNanos = monotonicNow - timestampNanos
        if (ageNanos > 750_000_000L || elapsedNowMillis < ageNanos / 1_000_000) return null
        return elapsedNowMillis - ageNanos / 1_000_000
    }
}
