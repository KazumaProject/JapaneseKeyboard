package com.kazumaproject.markdownhelperkeyboard.ime_service

/** Show only a completed, current result that differs from the regular first candidate. */
internal object ZenzFloatingCandidatePolicy {
    fun shouldShow(
        enabled: Boolean,
        suppressed: Boolean,
        currentRequest: Boolean,
        currentTarget: Boolean,
        loading: Boolean,
        candidateText: String?,
        firstLocalCandidateText: String?,
    ): Boolean = enabled && !suppressed && currentRequest && currentTarget && !loading &&
        !candidateText.isNullOrEmpty() && firstLocalCandidateText != null &&
        candidateText != firstLocalCandidateText
}
