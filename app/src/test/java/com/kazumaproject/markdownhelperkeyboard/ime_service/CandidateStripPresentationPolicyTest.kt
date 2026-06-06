package com.kazumaproject.markdownhelperkeyboard.ime_service

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
        assertFalse(presentation.showIntegratedShortcut)
    }

    @Test
    fun shortcutToolbarVisibleAndIntegrationOffUsesIndependentToolbar() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(shortcutToolbarIntegratedInSuggestion = false)
        )

        assertTrue(presentation.showIndependentShortcutToolbar)
        assertFalse(presentation.reserveIndependentShortcutToolbarSpace)
        assertFalse(presentation.showIntegratedShortcut)
    }

    @Test
    fun shortcutToolbarVisibleAndIntegrationOnWithEmptyInputShowsIntegratedShortcut() {
        val presentation = CandidateStripPresentationPolicy.resolve(baseState())

        assertFalse(presentation.showIndependentShortcutToolbar)
        assertTrue(presentation.showIntegratedShortcut)
    }

    @Test
    fun nonEmptyInputDisablesIntegratedShortcut() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(inputStringEmpty = false)
        )

        assertFalse(presentation.showIntegratedShortcut)
    }

    @Test
    fun nonEmptyTailDisablesIntegratedShortcut() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(tailEmpty = false)
        )

        assertFalse(presentation.showIntegratedShortcut)
    }

    @Test
    fun clipboardPreviewDisablesIntegratedShortcut() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(clipboardPreviewShown = true)
        )

        assertFalse(presentation.showIntegratedShortcut)
    }

    @Test
    fun selectedTextGemmaActionsDisableIntegratedShortcut() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(selectedTextGemmaActionsShown = true)
        )

        assertFalse(presentation.showIntegratedShortcut)
    }

    @Test
    fun customLayoutPickerDisablesIntegratedShortcut() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(customLayoutPickerShown = true)
        )

        assertFalse(presentation.showIntegratedShortcut)
    }

    @Test
    fun symbolKeyboardDisablesShortcuts() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(symbolKeyboardShown = true)
        )

        assertFalse(presentation.showIndependentShortcutToolbar)
        assertFalse(presentation.reserveIndependentShortcutToolbarSpace)
        assertFalse(presentation.showIntegratedShortcut)
    }

    @Test
    fun regularSuggestionsDisableIntegratedShortcut() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(suggestionsEmpty = false)
        )

        assertFalse(presentation.showIntegratedShortcut)
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
