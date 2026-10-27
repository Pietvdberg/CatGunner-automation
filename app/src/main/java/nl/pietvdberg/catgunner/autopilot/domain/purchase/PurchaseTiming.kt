package nl.pietvdberg.catgunner.autopilot.domain.purchase

data class OcrStages(
    val prepareMillis: Long,
    val sdkMillis: Long,
    val parseMillis: Long,
    val mainQueueMillis: Long,
    val workerPriority: Int?
) {
    init {
        require(listOf(prepareMillis, sdkMillis, parseMillis, mainQueueMillis).all { it >= 0 })
    }
    val totalMillis: Long get() = prepareMillis + sdkMillis + parseMillis + mainQueueMillis
}

/** Bounded per-request timing aggregates, without frame history or per-frame disk writes. */
data class OcrTimingSummary(
    val samples: Long = 0,
    val readable: Long = 0,
    val stale: Long = 0,
    val totalWorkMillis: Long = 0,
    val maximumWorkMillis: Long = 0,
    val maximumFrameAgeMillis: Long = 0,
    val profiledSamples: Long = 0,
    val prepareMillis: Long = 0,
    val sdkMillis: Long = 0,
    val parseMillis: Long = 0,
    val mainQueueMillis: Long = 0,
    val maximumSdkMillis: Long = 0,
    val workerPriority: Int? = null
) {
    fun add(
        workMillis: Long,
        frameAgeMillis: Long,
        isReadable: Boolean,
        ageLimit: Long,
        stages: OcrStages? = null
    ) = copy(
        samples = samples + 1,
        readable = readable + if (isReadable) 1 else 0,
        stale = stale + if (frameAgeMillis !in 0..ageLimit) 1 else 0,
        totalWorkMillis = totalWorkMillis + workMillis.coerceAtLeast(0),
        maximumWorkMillis = maxOf(maximumWorkMillis, workMillis),
        maximumFrameAgeMillis = maxOf(maximumFrameAgeMillis, frameAgeMillis),
        profiledSamples = profiledSamples + if (stages == null) 0 else 1,
        prepareMillis = prepareMillis + (stages?.prepareMillis ?: 0),
        sdkMillis = sdkMillis + (stages?.sdkMillis ?: 0),
        parseMillis = parseMillis + (stages?.parseMillis ?: 0),
        mainQueueMillis = mainQueueMillis + (stages?.mainQueueMillis ?: 0),
        maximumSdkMillis = maxOf(maximumSdkMillis, stages?.sdkMillis ?: 0),
        workerPriority = stages?.workerPriority ?: workerPriority
    )

    fun describe(): String {
        fun average(value: Long, count: Long): String = if (count == 0L) "—" else "${value / count}"
        return "readable=$readable/$samples, stale=$stale; OCR avg/max=" +
            "${average(totalWorkMillis, samples)}/$maximumWorkMillis ms; " +
            "stages avg copy/SDK/parse/main=" +
            "${average(prepareMillis, profiledSamples)}/${average(sdkMillis, profiledSamples)}/" +
            "${average(
                parseMillis,
                profiledSamples
            )}/${average(mainQueueMillis, profiledSamples)} ms; " +
            "SDK max=$maximumSdkMillis ms; worker priority=$workerPriority; " +
            "max frame age=$maximumFrameAgeMillis ms"
    }
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
    val deferralReasons: List<String> = emptyList(),
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
            "deferrals=$deferrals; reasons=$deferralReasons; " +
            "unknown observations=$unknownObservations\n" +
            "OCR before call: ${beforeOcr.describe()}\nOCR after call: ${afterOcr.describe()}"
    }
}
