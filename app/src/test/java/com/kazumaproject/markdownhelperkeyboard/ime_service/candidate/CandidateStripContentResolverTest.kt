package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateStripContentResolverTest {

    @Test
    fun clipboardPreviewShown_whenInputEmptyAndUndoEnabled() {
        val state = baseState(
            inputStringEmpty = true,
            tailEmpty = true,
            clipboardPreviewEnabled = true,
            clipboardText = "hello",
            undoEnabled = true
        )
        val content = CandidateStripContentResolver.resolve(state)
        assertTrue(content is CandidateStripContent.ClipboardPreview)
    }

    @Test
    fun clipboardPreviewShown_whenInputEmptyAndRedoEnabled() {
        val state = baseState(
            inputStringEmpty = true,
            tailEmpty = true,
            clipboardPreviewEnabled = true,
            clipboardText = "hello",
            redoEnabled = true
        )
        val content = CandidateStripContentResolver.resolve(state)
        assertTrue(content is CandidateStripContent.ClipboardPreview)
    }

    @Test
    fun clipboardPreviewShown_whenInputEmptyAndReconvertEnabled() {
        val state = baseState(
            inputStringEmpty = true,
            tailEmpty = true,
            clipboardPreviewEnabled = true,
            clipboardText = "hello",
            reconvertEnabled = true
        )
        val content = CandidateStripContentResolver.resolve(state)
        assertTrue(content is CandidateStripContent.ClipboardPreview)
    }

    @Test
    fun clipboardPreviewShown_whenInputEmptyAndIncognitoVisible() {
        val state = baseState(
            inputStringEmpty = true,
            tailEmpty = true,
            clipboardPreviewEnabled = true,
            clipboardText = "hello",
            incognitoVisible = true
        )
        val content = CandidateStripContentResolver.resolve(state)
        assertTrue(content is CandidateStripContent.ClipboardPreview)
    }

    @Test
    fun emptyStateActionsShown_whenClipboardUnavailableAndUndoEnabled() {
        val state = baseState(
            inputStringEmpty = true,
            tailEmpty = true,
            clipboardPreviewEnabled = true,
            clipboardText = "",
            clipboardBitmap = null,
            undoEnabled = true
        )
        val content = CandidateStripContentResolver.resolve(state)
        assertTrue(content is CandidateStripContent.EmptyStateActions)
    }

    @Test
    fun emptyStateActionsKeepAllQuickActions_whenClipboardUnavailable() {
        val state = baseState(
            inputStringEmpty = true,
            tailEmpty = true,
            clipboardPreviewEnabled = true,
            clipboardText = "",
            clipboardBitmap = null,
            incognitoVisible = true,
            undoEnabled = true,
            redoEnabled = true,
            reconvertEnabled = true
        )
        val actions = CandidateStripContentResolver.resolve(state).asEmptyStateActions()
        assertTrue(actions.incognitoVisible)
        assertTrue(actions.undoEnabled)
        assertTrue(actions.redoEnabled)
        assertTrue(actions.reconvertEnabled)
    }

    @Test
    fun candidatesWinOverClipboardPreview() {
        val state = baseState(
            candidates = listOf(candidate("候補")),
            inputStringEmpty = true,
            tailEmpty = true,
            clipboardPreviewEnabled = true,
            clipboardText = "hello"
        )
        val content = CandidateStripContentResolver.resolve(state)
        assertTrue(content is CandidateStripContent.Candidates)
    }

    @Test
    fun customLayoutPickerWinsOverClipboardPreview() {
        val state = baseState(
            customLayoutPickerShown = true,
            customLayouts = listOf(customLayout("layout")),
            inputStringEmpty = true,
            tailEmpty = true,
            clipboardPreviewEnabled = true,
            clipboardText = "hello"
        )
        val content = CandidateStripContentResolver.resolve(state)
        assertTrue(content is CandidateStripContent.CustomLayoutPicker)
    }

    @Test
    fun clipboardPreviewHidden_whenClipboardTextIsLastPasted() {
        val state = baseState(
            inputStringEmpty = true,
            tailEmpty = true,
            clipboardPreviewEnabled = true,
            clipboardText = "hello",
            clipboardTextIsLastPasted = true,
            undoEnabled = true
        )
        val content = CandidateStripContentResolver.resolve(state)
        assertTrue(content is CandidateStripContent.EmptyStateActions)
    }

    @Test
    fun clipboardPreviewHasShortcutEntry_whenIntegratedShortcutEnabled() {
        val state = baseState(
            inputStringEmpty = true,
            tailEmpty = true,
            clipboardPreviewEnabled = true,
            clipboardText = "hello",
            shortcutToolbarVisible = true,
            shortcutToolbarIntegratedInSuggestion = true,
            shortcutItems = listOf(ShortcutType.SETTINGS)
        )
        val preview = CandidateStripContentResolver.resolve(state).asClipboardPreview()
        assertTrue(preview.showShortcutEntry)
    }

    @Test
    fun emptyStateActionsIncludeShortcuts_whenIntegratedShortcutEnabled() {
        val state = baseState(
            inputStringEmpty = true,
            tailEmpty = true,
            clipboardPreviewEnabled = true,
            clipboardText = "",
            undoEnabled = true,
            shortcutToolbarVisible = true,
            shortcutToolbarIntegratedInSuggestion = true,
            shortcutItems = listOf(ShortcutType.SETTINGS)
        )
        val actions = CandidateStripContentResolver.resolve(state).asEmptyStateActions()
        assertTrue(actions.undoEnabled)
        assertTrue(actions.showIntegratedShortcuts)
        assertFalse(actions.shortcutItems.isEmpty())
    }

    @Test
    fun clipboardPreviewHidden_whenSymbolKeyboardShown() {
        val state = baseState(
            inputStringEmpty = true,
            tailEmpty = true,
            clipboardPreviewEnabled = true,
            clipboardText = "hello",
            symbolKeyboardShown = true
        )
        val content = CandidateStripContentResolver.resolve(state)
        assertFalse(content is CandidateStripContent.ClipboardPreview)
    }

    private fun baseState(
        candidates: List<Candidate> = emptyList(),
        inputStringEmpty: Boolean = false,
        tailEmpty: Boolean = true,
        candidatesShown: Boolean = false,
        symbolKeyboardShown: Boolean = false,
        customLayoutPickerShown: Boolean = false,
        customLayouts: List<CustomKeyboardLayout> = emptyList(),
        selectedTextGemmaActionsShown: Boolean = false,
        clipboardPreviewEnabled: Boolean = false,
        clipboardPreviewDescriptionShown: Boolean = true,
        clipboardPreviewTapToDelete: Boolean = false,
        clipboardText: String = "",
        clipboardBitmap: android.graphics.Bitmap? = null,
        clipboardTextIsLastPasted: Boolean = false,
        incognitoVisible: Boolean = false,
        undoEnabled: Boolean = false,
        redoEnabled: Boolean = false,
        reconvertEnabled: Boolean = false,
        undoText: String = "元に戻す",
        redoText: String = "やり直す",
        shortcutToolbarVisible: Boolean = false,
        shortcutToolbarIntegratedInSuggestion: Boolean = false,
        integratedShortcutEntryExpanded: Boolean = false,
        shortcutItems: List<ShortcutType> = emptyList(),
    ): CandidateStripInputState =
        CandidateStripInputState(
            candidates = candidates,
            inputStringEmpty = inputStringEmpty,
            tailEmpty = tailEmpty,
            candidatesShown = candidatesShown,
            symbolKeyboardShown = symbolKeyboardShown,
            customLayoutPickerShown = customLayoutPickerShown,
            customLayouts = customLayouts,
            selectedTextGemmaActionsShown = selectedTextGemmaActionsShown,
            clipboardPreviewEnabled = clipboardPreviewEnabled,
            clipboardPreviewDescriptionShown = clipboardPreviewDescriptionShown,
            clipboardPreviewTapToDelete = clipboardPreviewTapToDelete,
            clipboardText = clipboardText,
            clipboardBitmap = clipboardBitmap,
            clipboardTextIsLastPasted = clipboardTextIsLastPasted,
            incognitoVisible = incognitoVisible,
            undoEnabled = undoEnabled,
            redoEnabled = redoEnabled,
            reconvertEnabled = reconvertEnabled,
            undoText = undoText,
            redoText = redoText,
            shortcutToolbarVisible = shortcutToolbarVisible,
            shortcutToolbarIntegratedInSuggestion = shortcutToolbarIntegratedInSuggestion,
            integratedShortcutEntryExpanded = integratedShortcutEntryExpanded,
            shortcutItems = shortcutItems,
        )

    private fun candidate(text: String): Candidate =
        Candidate(
            string = text,
            type = 1.toByte(),
            length = text.length.toUByte(),
            score = 0,
            yomi = text
        )

    private fun customLayout(name: String): CustomKeyboardLayout =
        CustomKeyboardLayout(
            name = name,
            columnCount = 5,
            rowCount = 4
        )

    private fun CandidateStripContent.asClipboardPreview():
        CandidateStripContent.ClipboardPreview =
        this as? CandidateStripContent.ClipboardPreview
            ?: throw AssertionError("Expected ClipboardPreview but was $this")

    private fun CandidateStripContent.asEmptyStateActions():
        CandidateStripContent.EmptyStateActions =
        this as? CandidateStripContent.EmptyStateActions
            ?: throw AssertionError("Expected EmptyStateActions but was $this")
}
