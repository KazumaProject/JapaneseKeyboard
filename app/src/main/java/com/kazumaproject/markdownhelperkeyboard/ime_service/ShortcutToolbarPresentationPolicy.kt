package com.kazumaproject.markdownhelperkeyboard.ime_service

data class ShortcutToolbarPresentationState(
    val shortcutToolbarVisible: Boolean,
    val integratedInSuggestion: Boolean,
    val inputStringEmpty: Boolean,
    val tailEmpty: Boolean,
    val clipboardPreviewShown: Boolean,
    val selectedTextGemmaActionsShown: Boolean,
    val suggestionsEmpty: Boolean,
    val customLayoutPickerShown: Boolean,
    val symbolKeyboardShown: Boolean = false
)

data class ShortcutToolbarPresentation(
    val showIndependentToolbar: Boolean,
    val showIntegratedShortcuts: Boolean
)

object ShortcutToolbarPresentationPolicy {

    fun resolve(state: ShortcutToolbarPresentationState): ShortcutToolbarPresentation {
        if (!state.shortcutToolbarVisible || state.symbolKeyboardShown) {
            return ShortcutToolbarPresentation(
                showIndependentToolbar = false,
                showIntegratedShortcuts = false
            )
        }
        if (!state.integratedInSuggestion) {
            return ShortcutToolbarPresentation(
                showIndependentToolbar = true,
                showIntegratedShortcuts = false
            )
        }
        val showIntegratedShortcuts =
            state.inputStringEmpty &&
                state.tailEmpty &&
                !state.clipboardPreviewShown &&
                !state.selectedTextGemmaActionsShown &&
                state.suggestionsEmpty &&
                !state.customLayoutPickerShown
        return ShortcutToolbarPresentation(
            showIndependentToolbar = false,
            showIntegratedShortcuts = showIntegratedShortcuts
        )
    }
}
