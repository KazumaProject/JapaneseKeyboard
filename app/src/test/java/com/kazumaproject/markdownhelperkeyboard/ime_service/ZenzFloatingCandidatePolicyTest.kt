package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenzFloatingCandidatePolicyTest {
    @Test fun loadingAndAcceptedResultAreVisible() {
        assertTrue(show(loading = true, candidate = false))
        assertTrue(show(loading = false, candidate = true))
    }
    @Test fun emptyOrFailedResultDoesNotLeaveAnEmptyPanel() {
        assertFalse(show(loading = false, candidate = false))
    }
    @Test fun staleRequestAndChangedInputOrFocusedSegmentAreHidden() {
        assertFalse(show(request = false))
        assertFalse(show(target = false))
    }
    @Test fun disabledAndSuppressedCandidatesAreHiddenEvenWhileLoading() {
        assertFalse(show(enabled = false, loading = true))
        assertFalse(show(suppressed = true, loading = true))
    }
    private fun show(
        enabled: Boolean = true, suppressed: Boolean = false,
        request: Boolean = true, target: Boolean = true,
        loading: Boolean = false, candidate: Boolean = true,
    ) = ZenzFloatingCandidatePolicy.shouldShow(enabled, suppressed, request, target, loading, candidate)
}
