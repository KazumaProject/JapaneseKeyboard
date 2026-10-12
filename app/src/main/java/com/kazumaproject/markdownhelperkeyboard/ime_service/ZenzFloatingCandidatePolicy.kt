package com.kazumaproject.markdownhelperkeyboard.ime_service

/** Only a current request may occupy the dedicated panel, including its loading state. */
internal object ZenzFloatingCandidatePolicy {
    fun shouldShow(
        enabled: Boolean,
        suppressed: Boolean,
        currentRequest: Boolean,
        currentTarget: Boolean,
        loading: Boolean,
        hasCandidate: Boolean,
    ): Boolean = enabled && !suppressed && currentRequest && currentTarget && (loading || hasCandidate)
}
