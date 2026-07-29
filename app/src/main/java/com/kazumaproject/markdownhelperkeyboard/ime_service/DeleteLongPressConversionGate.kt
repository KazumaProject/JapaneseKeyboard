package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.ime_service.models.CandidateShowFlag

/**
 * Coordinates composing-text presentation and candidate conversion while delete is repeating.
 *
 * Candidate results stay suspended after the finger is released until the replacement refresh
 * actually starts. This closes the small window in which an older asynchronous result could
 * otherwise be applied between ACTION_UP and collection of the final refresh request.
 */
internal class DeleteLongPressConversionGate {
    @Volatile
    private var repeating = false

    @Volatile
    private var candidateResultsSuspended = false

    @Synchronized
    fun beginRepeat(): Boolean {
        if (repeating) return false
        repeating = true
        candidateResultsSuspended = true
        return true
    }

    fun shouldRenderRawComposing(input: String): Boolean {
        return repeating && input.isNotEmpty()
    }

    fun mayApplyCandidateResult(): Boolean = !candidateResultsSuspended

    /**
     * Ends a real repeat session and returns the single refresh to issue after ACTION_UP.
     *
     * The result suspension is intentionally retained here. It is released only when the returned
     * refresh starts being collected.
     */
    @Synchronized
    fun finishRepeat(input: String): CandidateShowFlag? {
        if (!repeating) return null
        repeating = false
        return if (input.isEmpty()) {
            CandidateShowFlag.Idle
        } else {
            CandidateShowFlag.Updating
        }
    }

    /**
     * Called at the beginning of candidate refresh collection.
     *
     * Requests observed during the repeat must not reopen the gate. The one issued after release
     * does, after collectLatest has cancelled and joined the previous request.
     */
    @Synchronized
    fun onCandidateRefreshStarted() {
        if (!repeating) {
            candidateResultsSuspended = false
        }
    }
}
