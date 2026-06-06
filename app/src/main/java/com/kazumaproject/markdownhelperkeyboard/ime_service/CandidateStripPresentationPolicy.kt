package com.kazumaproject.markdownhelperkeyboard.ime_service

data class CandidateStripPresentationState(
    val candidateTabVisible: Boolean,
    val candidatesShown: Boolean,
    val resetCandidateTabSelection: Boolean,
    val shortcutToolbarVisible: Boolean,
    val shortcutToolbarIntegratedInSuggestion: Boolean,
    val inputStringEmpty: Boolean,
    val tailEmpty: Boolean,
    val clipboardPreviewShown: Boolean,
    val selectedTextGemmaActionsShown: Boolean,
    val suggestionsEmpty: Boolean,
    val customLayoutPickerShown: Boolean,
    val symbolKeyboardShown: Boolean = false,
    val shortcutToolbarHiddenForCandidates: Boolean = false
)

data class CandidateStripPresentation(
    val showCandidateTab: Boolean,
    val resetCandidateTabSelection: Boolean,
    val showIndependentShortcutToolbar: Boolean,
    val reserveIndependentShortcutToolbarSpace: Boolean,
    val showIntegratedShortcut: Boolean
)

object CandidateStripPresentationPolicy {

    fun resolve(state: CandidateStripPresentationState): CandidateStripPresentation {
        val shortcutPresentation = ShortcutToolbarPresentationPolicy.resolve(
            ShortcutToolbarPresentationState(
                shortcutToolbarVisible = state.shortcutToolbarVisible,
                integratedInSuggestion = state.shortcutToolbarIntegratedInSuggestion,
                inputStringEmpty = state.inputStringEmpty,
                tailEmpty = state.tailEmpty,
                clipboardPreviewShown = state.clipboardPreviewShown,
                selectedTextGemmaActionsShown = state.selectedTextGemmaActionsShown,
                suggestionsEmpty = state.suggestionsEmpty,
                customLayoutPickerShown = state.customLayoutPickerShown,
                symbolKeyboardShown = state.symbolKeyboardShown
            )
        )
        val hideShortcutForCandidates =
            state.shortcutToolbarHiddenForCandidates && !state.symbolKeyboardShown
        return CandidateStripPresentation(
            showCandidateTab = state.candidateTabVisible && state.candidatesShown,
            resetCandidateTabSelection = state.resetCandidateTabSelection,
            showIndependentShortcutToolbar =
                shortcutPresentation.showIndependentToolbar && !hideShortcutForCandidates,
            reserveIndependentShortcutToolbarSpace =
                shortcutPresentation.showIndependentToolbar && hideShortcutForCandidates,
            showIntegratedShortcut =
                shortcutPresentation.showIntegratedShortcuts && !hideShortcutForCandidates
        )
    }
}
