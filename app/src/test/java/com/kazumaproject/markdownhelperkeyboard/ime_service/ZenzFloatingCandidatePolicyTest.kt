package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenzFloatingCandidatePolicyTest {
    @Test fun onlyAResultDifferentFromTheFirstLocalCandidateIsVisible() {
        assertTrue(show())
        assertFalse(show(candidate = "垢"))
    }

    @Test fun generationNeverShowsThePanel() {
        assertFalse(show(loading = true, candidate = null))
        assertFalse(show(loading = true))
    }

    @Test fun missingResultOrLocalCandidatesKeepThePanelHidden() {
        assertFalse(show(candidate = null))
        assertFalse(show(candidate = ""))
        assertFalse(show(firstLocal = null))
    }

    @Test fun staleRequestAndChangedInputOrFocusedSegmentAreHidden() {
        assertFalse(show(request = false))
        assertFalse(show(target = false))
    }

    @Test fun disabledAndSuppressedCandidatesAreHidden() {
        assertFalse(show(enabled = false))
        assertFalse(show(suppressed = true))
    }

    private fun show(
        enabled: Boolean = true, suppressed: Boolean = false,
        request: Boolean = true, target: Boolean = true,
        loading: Boolean = false, candidate: String? = "赤", firstLocal: String? = "垢",
    ) = ZenzFloatingCandidatePolicy.shouldShow(
        enabled, suppressed, request, target, loading, candidate, firstLocal)
}
