package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutToolbarPresentationPolicyTest {

    @Test
    fun shortcutToolbarVisibilityFalseDisablesBothPresentations() {
        val presentation = ShortcutToolbarPresentationPolicy.resolve(
            baseState(shortcutToolbarVisible = false)
        )

        assertFalse(presentation.showIndependentToolbar)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun shortcutToolbarVisibleAndIntegrationOffUsesIndependentToolbar() {
        val presentation = ShortcutToolbarPresentationPolicy.resolve(
            baseState(integratedInSuggestion = false)
        )

        assertTrue(presentation.showIndependentToolbar)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun integratedOnShowsShortcutItemsForNormalEmptyState() {
        val presentation = ShortcutToolbarPresentationPolicy.resolve(baseState())

        assertFalse(presentation.showIndependentToolbar)
        assertTrue(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun nonEmptyInputDisablesIntegratedShortcuts() {
        val presentation = ShortcutToolbarPresentationPolicy.resolve(
            baseState(inputStringEmpty = false)
        )

        assertFalse(presentation.showIndependentToolbar)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun nonEmptyTailDisablesIntegratedShortcuts() {
        val presentation = ShortcutToolbarPresentationPolicy.resolve(
            baseState(tailEmpty = false)
        )

        assertFalse(presentation.showIndependentToolbar)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun integratedOnClipboardPreviewShowsShortcutEntryOnly() {
        val presentation = ShortcutToolbarPresentationPolicy.resolve(
            baseState(clipboardPreviewShown = true)
        )

        assertFalse(presentation.showIndependentToolbar)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertTrue(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun integratedOnGemmaActionsShowShortcutEntryOnly() {
        val presentation = ShortcutToolbarPresentationPolicy.resolve(
            baseState(selectedTextGemmaActionsShown = true, suggestionsEmpty = false)
        )

        assertFalse(presentation.showIndependentToolbar)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertTrue(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun integratedOffClipboardPreviewDoesNotShowShortcutEntry() {
        val presentation = ShortcutToolbarPresentationPolicy.resolve(
            baseState(integratedInSuggestion = false, clipboardPreviewShown = true)
        )

        assertTrue(presentation.showIndependentToolbar)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun integratedOffGemmaActionsDoNotShowShortcutEntry() {
        val presentation = ShortcutToolbarPresentationPolicy.resolve(
            baseState(
                integratedInSuggestion = false,
                selectedTextGemmaActionsShown = true,
                suggestionsEmpty = false
            )
        )

        assertTrue(presentation.showIndependentToolbar)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun regularSuggestionsDisableIntegratedShortcuts() {
        val presentation = ShortcutToolbarPresentationPolicy.resolve(
            baseState(suggestionsEmpty = false)
        )

        assertFalse(presentation.showIndependentToolbar)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun customLayoutPickerDisablesIntegratedShortcuts() {
        val presentation = ShortcutToolbarPresentationPolicy.resolve(
            baseState(customLayoutPickerShown = true, clipboardPreviewShown = true)
        )

        assertFalse(presentation.showIndependentToolbar)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun symbolKeyboardDoesNotShowShortcutEntry() {
        val presentation = ShortcutToolbarPresentationPolicy.resolve(
            baseState(symbolKeyboardShown = true, clipboardPreviewShown = true)
        )

        assertFalse(presentation.showIndependentToolbar)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    private fun baseState(
        shortcutToolbarVisible: Boolean = true,
        integratedInSuggestion: Boolean = true,
        inputStringEmpty: Boolean = true,
        tailEmpty: Boolean = true,
        clipboardPreviewShown: Boolean = false,
        selectedTextGemmaActionsShown: Boolean = false,
        suggestionsEmpty: Boolean = true,
        customLayoutPickerShown: Boolean = false,
        symbolKeyboardShown: Boolean = false
    ): ShortcutToolbarPresentationState {
        return ShortcutToolbarPresentationState(
            shortcutToolbarVisible = shortcutToolbarVisible,
            integratedInSuggestion = integratedInSuggestion,
            inputStringEmpty = inputStringEmpty,
            tailEmpty = tailEmpty,
            clipboardPreviewShown = clipboardPreviewShown,
            selectedTextGemmaActionsShown = selectedTextGemmaActionsShown,
            suggestionsEmpty = suggestionsEmpty,
            customLayoutPickerShown = customLayoutPickerShown,
            symbolKeyboardShown = symbolKeyboardShown
        )
    }
}
