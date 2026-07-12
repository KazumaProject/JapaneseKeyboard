package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateStripPresentationPolicyTest {

    @Test
    fun shortcutToolbarVisibilityFalseDisablesIndependentAndIntegratedShortcuts() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(shortcutToolbarVisible = false)
        )

        assertFalse(presentation.showIndependentShortcutToolbar)
        assertFalse(presentation.reserveIndependentShortcutToolbarSpace)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun shortcutToolbarVisibleAndIntegrationOffUsesIndependentToolbar() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(shortcutToolbarIntegratedInSuggestion = false)
        )

        assertTrue(presentation.showIndependentShortcutToolbar)
        assertFalse(presentation.reserveIndependentShortcutToolbarSpace)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun integratedOnShowsShortcutItemsForNormalEmptyState() {
        val presentation = CandidateStripPresentationPolicy.resolve(baseState())

        assertFalse(presentation.showIndependentShortcutToolbar)
        assertTrue(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun nonEmptyInputDisablesIntegratedShortcut() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(inputStringEmpty = false)
        )

        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun nonEmptyTailDisablesIntegratedShortcut() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(tailEmpty = false)
        )

        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun integratedOnClipboardPreviewShowsShortcutEntryOnly() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(clipboardPreviewShown = true)
        )

        assertFalse(presentation.showIndependentShortcutToolbar)
        assertFalse(presentation.reserveIndependentShortcutToolbarSpace)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertTrue(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun integratedOnGemmaActionsShowShortcutEntryOnly() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(selectedTextGemmaActionsShown = true, suggestionsEmpty = false)
        )

        assertFalse(presentation.showIndependentShortcutToolbar)
        assertFalse(presentation.reserveIndependentShortcutToolbarSpace)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertTrue(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun integratedOffClipboardPreviewDoesNotShowShortcutEntry() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(
                shortcutToolbarIntegratedInSuggestion = false,
                clipboardPreviewShown = true
            )
        )

        assertTrue(presentation.showIndependentShortcutToolbar)
        assertFalse(presentation.reserveIndependentShortcutToolbarSpace)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun integratedOffGemmaActionsDoNotShowShortcutEntry() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(
                shortcutToolbarIntegratedInSuggestion = false,
                selectedTextGemmaActionsShown = true,
                suggestionsEmpty = false
            )
        )

        assertTrue(presentation.showIndependentShortcutToolbar)
        assertFalse(presentation.reserveIndependentShortcutToolbarSpace)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun customLayoutPickerDisablesIntegratedShortcut() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(customLayoutPickerShown = true, clipboardPreviewShown = true)
        )

        assertFalse(presentation.showIndependentShortcutToolbar)
        assertFalse(presentation.reserveIndependentShortcutToolbarSpace)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun symbolKeyboardDisablesShortcuts() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(symbolKeyboardShown = true)
        )

        assertFalse(presentation.showIndependentShortcutToolbar)
        assertFalse(presentation.reserveIndependentShortcutToolbarSpace)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun regularSuggestionsDisableIntegratedShortcut() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(suggestionsEmpty = false)
        )

        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun zeroQuerySuggestionsAreTreatedAsNonEmptySuggestions() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(suggestionsEmpty = false)
        )

        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun candidateTabVisibilityFalseDisablesCandidateTab() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(candidateTabVisible = false, candidatesShown = true)
        )

        assertFalse(presentation.showCandidateTab)
    }

    @Test
    fun candidateTabVisibilityTrueDoesNotShowCandidateTabWhenCandidatesAreHidden() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(candidateTabVisible = true, candidatesShown = false)
        )

        assertFalse(presentation.showCandidateTab)
    }

    @Test
    fun candidateTabVisibilityTrueShowsCandidateTabWhenCandidatesAreShown() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(candidateTabVisible = true, candidatesShown = true)
        )

        assertTrue(presentation.showCandidateTab)
    }

    @Test
    fun visibleCandidateTabAlwaysReservesItsHeight() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(candidateTabVisible = true, candidatesShown = true)
        )

        assertEquals(36, resolveCandidateTabOffsetPx(presentation, 36))
    }

    @Test
    fun hiddenCandidateTabDoesNotReserveHeight() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(candidateTabVisible = true, candidatesShown = false)
        )

        assertEquals(0, resolveCandidateTabOffsetPx(presentation, 36))
    }

    @Test
    fun candidatesUseConfiguredCandidateStripHeight() {
        assertEquals(
            160,
            resolveCandidateStripHeightDp(
                candidatesShown = true,
                candidateHeightDp = 160,
                emptyHeightDp = 110
            )
        )
    }

    @Test
    fun emptyStateUsesConfiguredEmptyStripHeight() {
        assertEquals(
            110,
            resolveCandidateStripHeightDp(
                candidatesShown = false,
                candidateHeightDp = 160,
                emptyHeightDp = 110
            )
        )
    }

    @Test
    fun idleReturnRequestsCandidateTabSelectionReset() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(
                candidatesShown = false,
                resetCandidateTabSelection = true
            )
        )

        assertFalse(presentation.showCandidateTab)
        assertTrue(presentation.resetCandidateTabSelection)
    }

    @Test
    fun candidatesReserveIndependentToolbarSpaceInsteadOfShowingIt() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(
                shortcutToolbarIntegratedInSuggestion = false,
                candidatesShown = true,
                shortcutToolbarHiddenForCandidates = true
            )
        )

        assertFalse(presentation.showIndependentShortcutToolbar)
        assertTrue(presentation.reserveIndependentShortcutToolbarSpace)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    private fun baseState(
        candidateTabVisible: Boolean = true,
        candidatesShown: Boolean = false,
        resetCandidateTabSelection: Boolean = false,
        shortcutToolbarVisible: Boolean = true,
        shortcutToolbarIntegratedInSuggestion: Boolean = true,
        inputStringEmpty: Boolean = true,
        tailEmpty: Boolean = true,
        clipboardPreviewShown: Boolean = false,
        selectedTextGemmaActionsShown: Boolean = false,
        suggestionsEmpty: Boolean = true,
        customLayoutPickerShown: Boolean = false,
        symbolKeyboardShown: Boolean = false,
        shortcutToolbarHiddenForCandidates: Boolean = false
    ): CandidateStripPresentationState {
        return CandidateStripPresentationState(
            candidateTabVisible = candidateTabVisible,
            candidatesShown = candidatesShown,
            resetCandidateTabSelection = resetCandidateTabSelection,
            shortcutToolbarVisible = shortcutToolbarVisible,
            shortcutToolbarIntegratedInSuggestion = shortcutToolbarIntegratedInSuggestion,
            inputStringEmpty = inputStringEmpty,
            tailEmpty = tailEmpty,
            clipboardPreviewShown = clipboardPreviewShown,
            selectedTextGemmaActionsShown = selectedTextGemmaActionsShown,
            suggestionsEmpty = suggestionsEmpty,
            customLayoutPickerShown = customLayoutPickerShown,
            symbolKeyboardShown = symbolKeyboardShown,
            shortcutToolbarHiddenForCandidates = shortcutToolbarHiddenForCandidates
        )
    }
}
