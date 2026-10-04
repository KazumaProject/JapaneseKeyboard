package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordCandidatePresentationTest {
    @Test
    fun allowedCandidatesUseActiveHeightAndTabOffset() {
        val active = isCandidateStripActive(true, false, suggestionsSuppressed = false)
        assertTrue(active)
        val presentation = presentation(active)
        assertTrue(presentation.showCandidateTab)
        assertEquals(80, resolveCandidateStripHeightDp(active, 80, 60))
        assertEquals(36, resolveCandidateTabOffsetPx(presentation, 36))
    }

    @Test
    fun suppressedCandidatesIgnoreAnUpdatingRequest() {
        val active = isCandidateStripActive(true, false, suggestionsSuppressed = true)
        assertFalse(active)
        val presentation = presentation(active)
        assertFalse(presentation.showCandidateTab)
        assertFalse(presentation.showIntegratedShortcutEntry)
        assertEquals(60, resolveCandidateStripHeightDp(active, 80, 60))
        assertEquals(0, resolveCandidateTabOffsetPx(presentation, 36))
    }

    @Test
    fun deletingAllInputRestoresEmptyHeightWhetherSuggestionsAreAllowedOrSuppressed() {
        for (suppressed in listOf(false, true)) {
            val active = isCandidateStripActive(true, true, suggestionsSuppressed = suppressed)
            assertFalse(active)
            assertEquals(60, resolveCandidateStripHeightDp(active, 80, 60))
        }
    }

    private fun presentation(active: Boolean) = CandidateStripPresentationPolicy.resolve(
        CandidateStripPresentationState(
            candidateTabVisible = true,
            candidatesShown = active,
            resetCandidateTabSelection = false,
            shortcutToolbarVisible = true,
            shortcutToolbarIntegratedInSuggestion = true,
            inputStringEmpty = false,
            tailEmpty = true,
            clipboardPreviewShown = false,
            selectionActionsShown = false,
            suggestionsEmpty = !active,
            customLayoutPickerShown = false,
            shortcutToolbarHiddenForCandidates = active,
        )
    )
}
