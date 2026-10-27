package nl.pietvdberg.catgunner.autopilot.domain.purchase

/** Bounded aggregates only; no frame history or per-frame disk writes. */
data class OcrTimingSummary(
    val samples: Long = 0,
    val pixelRechecks: Long = 0,
    val readable: Long = 0,
    val stale: Long = 0,
    val totalWorkMillis: Long = 0,
    val maximumWorkMillis: Long = 0,
    val maximumFrameAgeMillis: Long = 0
) {
    fun add(
        workMillis: Long,
        frameAgeMillis: Long,
        isReadable: Boolean,
        ageLimit: Long,
        pixelRecheck: Boolean = false
    ) = copy(
        samples = samples + 1,
        pixelRechecks = pixelRechecks + if (pixelRecheck) 1 else 0,
        readable = readable + if (isReadable) 1 else 0,
        stale = stale + if (frameAgeMillis !in 0..ageLimit) 1 else 0,
        totalWorkMillis = totalWorkMillis + if (pixelRecheck) 0 else workMillis.coerceAtLeast(0),
        maximumWorkMillis = if (pixelRecheck) {
            maximumWorkMillis
        } else {
            maxOf(
                maximumWorkMillis,
                workMillis
            )
        },
        maximumFrameAgeMillis = maxOf(maximumFrameAgeMillis, frameAgeMillis)
    )

    fun describe(): String = "readable=$readable/$samples, stale=$stale, " +
        "pixel rechecks=$pixelRechecks; native OCR avg/max=" +
        "${if (samples == pixelRechecks) "—" else totalWorkMillis / (samples - pixelRechecks)}/" +
        "$maximumWorkMillis ms, max frame age=$maximumFrameAgeMillis ms"
}

/** All milestones use elapsed realtime, never wall time. Missing stages stay unknown. */
data class PurchaseTiming(
    val requestedAt: Long,
    val armedAt: Long,
    val firstQueuedAt: Long? = null,
    val callAt: Long? = null,
    val callbackAt: Long? = null,
    val finishedAt: Long? = null,
    val deferrals: Int = 0,
    val unknownObservations: Int = 0,
    val beforeOcr: OcrTimingSummary = OcrTimingSummary(),
    val afterOcr: OcrTimingSummary = OcrTimingSummary()
) {
    val totalMillis: Long? get() = finishedAt?.minus(requestedAt)

    fun describe(): String {
        fun duration(start: Long?, end: Long?): String =
            if (start == null || end == null) "—" else "${end - start} ms"
        return "total=${duration(requestedAt, finishedAt)}; " +
            "selection=${duration(requestedAt, armedAt)}; " +
            "readiness=${duration(armedAt, firstQueuedAt)}; " +
            "preparation=${duration(firstQueuedAt, callAt)}; " +
            "callback=${duration(callAt, callbackAt)}; " +
            "verification=${duration(callbackAt, finishedAt)}; " +
            "deferrals=$deferrals; unknown observations=$unknownObservations\n" +
            "OCR before call: ${beforeOcr.describe()}\nOCR after call: ${afterOcr.describe()}"
    }
}
