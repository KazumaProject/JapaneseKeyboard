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
        assertFalse(presentation.showIntegratedShortcuts)
    }

    @Test
    fun shortcutToolbarVisibleAndIntegrationOffUsesIndependentToolbar() {
        val presentation = ShortcutToolbarPresentationPolicy.resolve(
            baseState(integratedInSuggestion = false)
        )

        assertTrue(presentation.showIndependentToolbar)
        assertFalse(presentation.showIntegratedShortcuts)
    }

    @Test
    fun shortcutToolbarVisibleAndIntegrationOnWithEmptyInputShowsIntegratedShortcuts() {
        val presentation = ShortcutToolbarPresentationPolicy.resolve(baseState())

        assertFalse(presentation.showIndependentToolbar)
        assertTrue(presentation.showIntegratedShortcuts)
    }

    @Test
    fun nonEmptyInputDisablesIntegratedShortcuts() {
        val presentation = ShortcutToolbarPresentationPolicy.resolve(
            baseState(inputStringEmpty = false)
        )

        assertFalse(presentation.showIndependentToolbar)
        assertFalse(presentation.showIntegratedShortcuts)
    }

    @Test
    fun nonEmptyTailDisablesIntegratedShortcuts() {
        val presentation = ShortcutToolbarPresentationPolicy.resolve(
            baseState(tailEmpty = false)
        )

        assertFalse(presentation.showIndependentToolbar)
        assertFalse(presentation.showIntegratedShortcuts)
    }

    @Test
    fun clipboardPreviewDisablesIntegratedShortcuts() {
        val presentation = ShortcutToolbarPresentationPolicy.resolve(
            baseState(clipboardPreviewShown = true)
        )

        assertFalse(presentation.showIndependentToolbar)
        assertFalse(presentation.showIntegratedShortcuts)
    }

    @Test
    fun selectedTextGemmaActionsDisableIntegratedShortcuts() {
        val presentation = ShortcutToolbarPresentationPolicy.resolve(
            baseState(selectedTextGemmaActionsShown = true)
        )

        assertFalse(presentation.showIndependentToolbar)
        assertFalse(presentation.showIntegratedShortcuts)
    }

    @Test
    fun regularSuggestionsDisableIntegratedShortcuts() {
        val presentation = ShortcutToolbarPresentationPolicy.resolve(
            baseState(suggestionsEmpty = false)
        )

        assertFalse(presentation.showIndependentToolbar)
        assertFalse(presentation.showIntegratedShortcuts)
    }

    @Test
    fun customLayoutPickerDisablesIntegratedShortcuts() {
        val presentation = ShortcutToolbarPresentationPolicy.resolve(
            baseState(customLayoutPickerShown = true)
        )

        assertFalse(presentation.showIndependentToolbar)
        assertFalse(presentation.showIntegratedShortcuts)
    }

    private fun baseState(
        shortcutToolbarVisible: Boolean = true,
        integratedInSuggestion: Boolean = true,
        inputStringEmpty: Boolean = true,
        tailEmpty: Boolean = true,
        clipboardPreviewShown: Boolean = false,
        selectedTextGemmaActionsShown: Boolean = false,
        suggestionsEmpty: Boolean = true,
        customLayoutPickerShown: Boolean = false
    ): ShortcutToolbarPresentationState {
        return ShortcutToolbarPresentationState(
            shortcutToolbarVisible = shortcutToolbarVisible,
            integratedInSuggestion = integratedInSuggestion,
            inputStringEmpty = inputStringEmpty,
            tailEmpty = tailEmpty,
            clipboardPreviewShown = clipboardPreviewShown,
            selectedTextGemmaActionsShown = selectedTextGemmaActionsShown,
            suggestionsEmpty = suggestionsEmpty,
            customLayoutPickerShown = customLayoutPickerShown
        )
    }
}
