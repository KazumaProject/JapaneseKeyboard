package com.kazumaproject.markdownhelperkeyboard.ime_service

/** Only a current request may occupy the dedicated panel, after generation starts or a result is available. */
internal object ZenzFloatingCandidatePolicy {
    fun shouldShow(
        enabled: Boolean,
        suppressed: Boolean,
        currentRequest: Boolean,
        currentTarget: Boolean,
        loading: Boolean,
        generationStarted: Boolean,
        hasCandidate: Boolean,
    ): Boolean = enabled && !suppressed && currentRequest && currentTarget && ((loading && generationStarted) || hasCandidate)
}
