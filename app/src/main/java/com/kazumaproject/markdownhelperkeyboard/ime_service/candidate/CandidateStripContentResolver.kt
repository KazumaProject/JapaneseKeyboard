package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

object CandidateStripContentResolver {

    fun resolve(state: CandidateStripInputState): CandidateStripContent {
        if (shouldShowExpandedShortcutEntry(state)) {
            return CandidateStripContent.ExpandedShortcutEntry(
                shortcutItems = state.shortcutItems
            )
        }
        if (state.candidates.isNotEmpty()) {
            if (state.selectedTextGemmaActionsShown) {
                return CandidateStripContent.GemmaActions(
                    actions = state.candidates,
                    showShortcutEntry = shouldShowShortcutEntryWithGemmaActions(state)
                )
            }
            return CandidateStripContent.Candidates(
                candidates = state.candidates,
                showShortcutEntry = false
            )
        }
        if (state.customLayoutPickerShown) {
            return CandidateStripContent.CustomLayoutPicker(
                layouts = state.customLayouts
            )
        }
        if (shouldShowClipboardPreview(state)) {
            return CandidateStripContent.ClipboardPreview(
                text = state.clipboardText,
                bitmap = state.clipboardBitmap,
                descriptionShown = state.clipboardPreviewDescriptionShown,
                tapToDelete = state.clipboardPreviewTapToDelete,
                showShortcutEntry = shouldShowShortcutEntryWithClipboardPreview(state)
            )
        }
        if (shouldShowEmptyStateActions(state)) {
            return CandidateStripContent.EmptyStateActions(
                incognitoVisible = state.incognitoVisible,
                undoEnabled = state.undoEnabled,
                redoEnabled = state.redoEnabled,
                reconvertEnabled = state.reconvertEnabled,
                undoText = state.undoText,
                redoText = state.redoText,
                shortcutItems = state.shortcutItems,
                showIntegratedShortcuts = shouldShowIntegratedShortcutItems(state)
            )
        }
        if (shouldShowIntegratedShortcutItems(state)) {
            return CandidateStripContent.IntegratedShortcuts(
                shortcutItems = state.shortcutItems
            )
        }
        return CandidateStripContent.Empty
    }

    private fun shouldShowExpandedShortcutEntry(state: CandidateStripInputState): Boolean {
        if (!state.integratedShortcutEntryExpanded) return false
        if (!canShowShortcutEntry(state)) return false
        return hasSwitchableShortcutEntryContent(state)
    }

    private fun shouldShowShortcutEntryWithGemmaActions(
        state: CandidateStripInputState
    ): Boolean = canShowShortcutEntry(state)

    private fun shouldShowShortcutEntryWithClipboardPreview(
        state: CandidateStripInputState
    ): Boolean = canShowShortcutEntry(state)

    private fun canShowShortcutEntry(state: CandidateStripInputState): Boolean {
        if (!state.shortcutToolbarVisible) return false
        if (!state.shortcutToolbarIntegratedInSuggestion) return false
        if (state.symbolKeyboardShown) return false
        if (!state.inputStringEmpty) return false
        if (!state.tailEmpty) return false
        if (state.customLayoutPickerShown) return false
        return state.shortcutItems.isNotEmpty()
    }

    private fun hasSwitchableShortcutEntryContent(
        state: CandidateStripInputState
    ): Boolean {
        val hasGemmaActions =
            state.candidates.isNotEmpty() && state.selectedTextGemmaActionsShown
        return hasGemmaActions || shouldShowClipboardPreview(state)
    }

    private fun shouldShowClipboardPreview(state: CandidateStripInputState): Boolean {
        if (!state.clipboardPreviewEnabled) return false
        if (!state.inputStringEmpty) return false
        if (!state.tailEmpty) return false
        if (state.candidatesShown) return false
        if (state.symbolKeyboardShown) return false
        if (state.customLayoutPickerShown) return false
        if (state.selectedTextGemmaActionsShown) return false
        val hasContent = state.clipboardBitmap != null || state.clipboardText.isNotBlank()
        if (!hasContent) return false
        if (state.clipboardTextIsLastPasted) return false
        return true
    }

    private fun shouldShowEmptyStateActions(state: CandidateStripInputState): Boolean {
        if (state.symbolKeyboardShown) return false
        if (!state.inputStringEmpty) return false
        if (!state.tailEmpty) return false
        if (state.candidatesShown) return false
        if (state.customLayoutPickerShown) return false
        return state.incognitoVisible ||
            state.undoEnabled ||
            state.redoEnabled ||
            state.reconvertEnabled
    }

    private fun shouldShowIntegratedShortcutItems(state: CandidateStripInputState): Boolean {
        if (!state.shortcutToolbarVisible) return false
        if (!state.shortcutToolbarIntegratedInSuggestion) return false
        if (state.symbolKeyboardShown) return false
        if (!state.inputStringEmpty) return false
        if (!state.tailEmpty) return false
        if (state.candidatesShown) return false
        if (state.customLayoutPickerShown) return false
        if (state.selectedTextGemmaActionsShown) return false
        return state.shortcutItems.isNotEmpty()
    }
}
