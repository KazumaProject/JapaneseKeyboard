package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import android.graphics.Bitmap
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CandidateStripClipboardPreviewRegressionTest {

    @Test
    fun selectedTextGemmaActionsWinAndClipboardPreviewReturnsAfterSelectionCleared() {
        val selectedContent = CandidateStripContentResolver.resolve(
            baseState(
                candidates = listOf(candidate("Translate")),
                inputStringEmpty = true,
                tailEmpty = true,
                selectedTextGemmaActionsShown = true,
                editorTextSelected = true,
                clipboardPreviewEnabled = true,
                clipboardText = "copied"
            )
        )

        assertTrue(selectedContent is CandidateStripContent.GemmaActions)

        val releasedContent = CandidateStripContentResolver.resolve(
            baseState(
                inputStringEmpty = true,
                tailEmpty = true,
                editorTextSelected = false,
                clipboardPreviewEnabled = true,
                clipboardText = "copied",
                shortcutToolbarVisible = true,
                shortcutToolbarIntegratedInSuggestion = true,
                shortcutItems = listOf(ShortcutType.SETTINGS)
            )
        ).asEmptyState()

        assertNotNull(releasedContent.clipboardPreview)
        assertTrue(releasedContent.showShortcutEntry)
    }

    @Test
    fun primaryClipChangedPreviewCanShowAfterSelectionSuppressionIsReleased() {
        val content = CandidateStripContentResolver.resolve(
            baseState(
                inputStringEmpty = true,
                tailEmpty = true,
                editorTextSelected = false,
                clipboardPreviewEnabled = true,
                clipboardText = "new copy",
                undoEnabled = true
            )
        ).asEmptyState()

        assertNotNull(content.clipboardPreview)
        assertTrue(content.quickActions.undoEnabled)
    }

    @Test
    fun imageClipboardPreviewUsesBitmapWithoutTextFallback() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val content = CandidateStripContentResolver.resolve(
            baseState(
                inputStringEmpty = true,
                tailEmpty = true,
                clipboardPreviewEnabled = true,
                clipboardText = "",
                clipboardBitmap = bitmap
            )
        ).asEmptyState()

        assertSame(bitmap, content.clipboardPreview?.bitmap)
        assertEquals("", content.clipboardPreview?.text)
    }

    @Test
    fun lastPastedTapToDeleteHidesOldTextAndNewTextShows() {
        val oldContent = CandidateStripContentResolver.resolve(
            baseState(
                inputStringEmpty = true,
                tailEmpty = true,
                clipboardPreviewEnabled = true,
                clipboardPreviewTapToDelete = true,
                clipboardText = "old",
                clipboardTextIsLastPasted = true,
                undoEnabled = true
            )
        ).asEmptyState()

        assertNull(oldContent.clipboardPreview)
        assertTrue(oldContent.quickActions.undoEnabled)

        val newContent = CandidateStripContentResolver.resolve(
            baseState(
                inputStringEmpty = true,
                tailEmpty = true,
                clipboardPreviewEnabled = true,
                clipboardPreviewTapToDelete = true,
                clipboardText = "new",
                clipboardTextIsLastPasted = false
            )
        ).asEmptyState()

        assertNotNull(newContent.clipboardPreview)
    }

    @Test
    fun shortcutIntegrationOnKeepsShortcutEntryUndoClipboardPreviewOrder() {
        val content = CandidateStripContentResolver.resolve(
            baseState(
                inputStringEmpty = true,
                tailEmpty = true,
                clipboardPreviewEnabled = true,
                clipboardText = "clip",
                undoEnabled = true,
                shortcutToolbarVisible = true,
                shortcutToolbarIntegratedInSuggestion = true,
                shortcutItems = listOf(ShortcutType.SETTINGS)
            )
        ).asEmptyState()

        val adapter = SuggestionAdapter()
        adapter.submitContent(content)

        assertEquals(
            listOf(
                SuggestionAdapter.SuggestionDisplayItemKind.ShortcutEntryItem,
                SuggestionAdapter.SuggestionDisplayItemKind.QuickActionsItem,
                SuggestionAdapter.SuggestionDisplayItemKind.ClipboardPreviewItem
            ),
            adapter.buildDisplayItemKindsForTesting()
        )
        adapter.release()
    }

    @Test
    fun shortcutIntegrationOffKeepsUndoAndClipboardPreviewWithoutShortcutEntry() {
        val content = CandidateStripContentResolver.resolve(
            baseState(
                inputStringEmpty = true,
                tailEmpty = true,
                clipboardPreviewEnabled = true,
                clipboardText = "clip",
                undoEnabled = true,
                shortcutToolbarVisible = true,
                shortcutToolbarIntegratedInSuggestion = false,
                shortcutItems = listOf(ShortcutType.SETTINGS)
            )
        ).asEmptyState()

        val adapter = SuggestionAdapter()
        adapter.submitContent(content)

        assertEquals(
            listOf(
                SuggestionAdapter.SuggestionDisplayItemKind.QuickActionsItem,
                SuggestionAdapter.SuggestionDisplayItemKind.ClipboardPreviewItem
            ),
            adapter.buildDisplayItemKindsForTesting()
        )
        assertFalse(content.showShortcutEntry)
        adapter.release()
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
        editorTextSelected: Boolean = false,
        clipboardPreviewEnabled: Boolean = false,
        clipboardPreviewDescriptionShown: Boolean = true,
        clipboardPreviewTapToDelete: Boolean = false,
        clipboardText: String = "",
        clipboardBitmap: Bitmap? = null,
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
            editorTextSelected = editorTextSelected,
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

    private fun CandidateStripContent.asEmptyState(): CandidateStripContent.EmptyState =
        this as? CandidateStripContent.EmptyState
            ?: throw AssertionError("Expected EmptyState but was $this")
}
