package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.ime_service.models.CandidateShowFlag

/**
 * Keeps non-user candidate refreshes from changing the visible conversion phase.
 *
 * Dictionary loading can publish an Updating request while the composing input is empty. That
 * request refreshes data, but it must not consume the Idle -> Updating transition used by the
 * candidate-strip UI to show the full-candidate toggle.
 */
internal object CandidateRefreshTransitionPolicy {

    fun shouldEnterActiveCandidatePhase(
        previousFlag: CandidateShowFlag?,
        currentFlag: CandidateShowFlag,
        input: String,
    ): Boolean {
        return previousFlag == CandidateShowFlag.Idle &&
            currentFlag == CandidateShowFlag.Updating &&
            input.isNotEmpty()
    }

    fun nextUiPreviousFlag(
        currentFlag: CandidateShowFlag,
        input: String,
    ): CandidateShowFlag {
        return if (currentFlag == CandidateShowFlag.Updating && input.isEmpty()) {
            CandidateShowFlag.Idle
        } else {
            currentFlag
        }
    }
}
