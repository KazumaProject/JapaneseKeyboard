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
    val selectionActionsShown: Boolean,
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
    val showIntegratedShortcutItems: Boolean,
    val showIntegratedShortcutEntry: Boolean
)

internal fun resolveCandidateTabOffsetPx(
    presentation: CandidateStripPresentation,
    candidateTabHeightPx: Int
): Int = if (presentation.showCandidateTab) candidateTabHeightPx.coerceAtLeast(0) else 0

internal fun resolveCandidateStripHeightDp(
    candidatesShown: Boolean,
    candidateHeightDp: Int,
    emptyHeightDp: Int
): Int = if (candidatesShown) candidateHeightDp else emptyHeightDp

internal fun isCandidateStripActive(
    candidatesShown: Boolean,
    inputStringEmpty: Boolean
): Boolean = candidatesShown && !inputStringEmpty

object CandidateStripPresentationPolicy {

    fun resolve(state: CandidateStripPresentationState): CandidateStripPresentation {
        val shortcutPresentation = ShortcutToolbarPresentationPolicy.resolve(
            ShortcutToolbarPresentationState(
                shortcutToolbarVisible = state.shortcutToolbarVisible,
                integratedInSuggestion = state.shortcutToolbarIntegratedInSuggestion,
                inputStringEmpty = state.inputStringEmpty,
                tailEmpty = state.tailEmpty,
                clipboardPreviewShown = state.clipboardPreviewShown,
                selectionActionsShown = state.selectionActionsShown,
                suggestionsEmpty = state.suggestionsEmpty,
                customLayoutPickerShown = state.customLayoutPickerShown,
                symbolKeyboardShown = state.symbolKeyboardShown
            )
        )
        val candidateStripActive = isCandidateStripActive(
            candidatesShown = state.candidatesShown,
            inputStringEmpty = state.inputStringEmpty
        )
        val hideShortcutForCandidates =
            state.shortcutToolbarHiddenForCandidates &&
                candidateStripActive &&
                !state.symbolKeyboardShown
        return CandidateStripPresentation(
            showCandidateTab = state.candidateTabVisible && candidateStripActive,
            resetCandidateTabSelection = state.resetCandidateTabSelection,
            showIndependentShortcutToolbar =
                shortcutPresentation.showIndependentToolbar && !hideShortcutForCandidates,
            reserveIndependentShortcutToolbarSpace =
                shortcutPresentation.showIndependentToolbar && hideShortcutForCandidates,
            showIntegratedShortcutItems =
                shortcutPresentation.showIntegratedShortcutItems &&
                    !hideShortcutForCandidates,
            showIntegratedShortcutEntry =
                shortcutPresentation.showIntegratedShortcutEntry &&
                    !hideShortcutForCandidates
        )
    }
}
