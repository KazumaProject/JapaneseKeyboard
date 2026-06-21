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
                candidates = state.candidates
            )
        }
        if (state.customLayoutPickerShown) {
            return CandidateStripContent.CustomLayoutPicker(
                layouts = state.customLayouts
            )
        }
        val clipboardPreview = resolveClipboardPreviewOrNull(state)
        val quickActions = resolveQuickActions(state)
        val showShortcutEntry = shouldShowShortcutEntryForEmptyState(
            state = state,
            clipboardPreview = clipboardPreview
        )
        val showIntegratedShortcuts = shouldShowIntegratedShortcutItems(
            state = state,
            clipboardPreview = clipboardPreview
        )
        if (
            clipboardPreview != null ||
            quickActions.hasAnyAction ||
            showShortcutEntry ||
            showIntegratedShortcuts
        ) {
            return CandidateStripContent.EmptyState(
                showShortcutEntry = showShortcutEntry,
                quickActions = quickActions,
                clipboardPreview = clipboardPreview,
                shortcutItems = state.shortcutItems,
                showIntegratedShortcuts = showIntegratedShortcuts
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
        return hasGemmaActions || resolveClipboardPreviewOrNull(state) != null
    }

    private fun resolveClipboardPreviewOrNull(
        state: CandidateStripInputState
    ): ClipboardPreviewState? {
        if (!state.clipboardPreviewEnabled) return null
        if (!state.inputStringEmpty) return null
        if (!state.tailEmpty) return null
        if (state.candidatesShown) return null
        if (state.symbolKeyboardShown) return null
        if (state.customLayoutPickerShown) return null
        if (state.selectedTextGemmaActionsShown) return null
        val hasContent = state.clipboardBitmap != null || state.clipboardText.isNotBlank()
        if (!hasContent) return null
        if (state.clipboardPreviewTapToDelete && state.clipboardTextIsLastPasted) return null
        return ClipboardPreviewState(
            text = state.clipboardText,
            bitmap = state.clipboardBitmap,
            descriptionShown = state.clipboardPreviewDescriptionShown,
            tapToDelete = state.clipboardPreviewTapToDelete
        )
    }

    private fun resolveQuickActions(state: CandidateStripInputState): QuickActionsState {
        val canShowQuickActions =
            !state.symbolKeyboardShown &&
                state.inputStringEmpty &&
                state.tailEmpty &&
                !state.candidatesShown &&
                !state.customLayoutPickerShown &&
                !state.selectedTextGemmaActionsShown
        return QuickActionsState(
            incognitoVisible = canShowQuickActions && state.incognitoVisible,
            undoEnabled = canShowQuickActions && state.undoEnabled,
            redoEnabled = canShowQuickActions && state.redoEnabled,
            reconvertEnabled = canShowQuickActions && state.reconvertEnabled,
            undoText = state.undoText,
            redoText = state.redoText,
        )
    }

    private fun shouldShowShortcutEntryForEmptyState(
        state: CandidateStripInputState,
        clipboardPreview: ClipboardPreviewState?
    ): Boolean {
        return state.shortcutToolbarVisible &&
            state.shortcutToolbarIntegratedInSuggestion &&
            state.shortcutItems.isNotEmpty() &&
            clipboardPreview != null
    }

    private fun shouldShowIntegratedShortcutItems(
        state: CandidateStripInputState,
        clipboardPreview: ClipboardPreviewState?
    ): Boolean {
        if (!state.shortcutToolbarVisible) return false
        if (!state.shortcutToolbarIntegratedInSuggestion) return false
        if (state.shortcutItems.isEmpty()) return false
        if (state.symbolKeyboardShown) return false
        if (clipboardPreview != null) return false
        if (!state.inputStringEmpty) return false
        if (!state.tailEmpty) return false
        if (state.candidatesShown) return false
        if (state.customLayoutPickerShown) return false
        if (state.selectedTextGemmaActionsShown) return false
        return true
    }
}
