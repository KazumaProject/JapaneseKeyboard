package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        val emptyState = CandidateStripContentResolver.resolve(state).asEmptyState()

        assertNotNull(emptyState.clipboardPreview)
        assertTrue(emptyState.quickActions.undoEnabled)
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
        val emptyState = CandidateStripContentResolver.resolve(state).asEmptyState()

        assertNotNull(emptyState.clipboardPreview)
        assertTrue(emptyState.quickActions.redoEnabled)
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
        val emptyState = CandidateStripContentResolver.resolve(state).asEmptyState()

        assertNotNull(emptyState.clipboardPreview)
        assertTrue(emptyState.quickActions.reconvertEnabled)
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
        val emptyState = CandidateStripContentResolver.resolve(state).asEmptyState()

        assertNotNull(emptyState.clipboardPreview)
        assertTrue(emptyState.quickActions.incognitoVisible)
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
        val emptyState = CandidateStripContentResolver.resolve(state).asEmptyState()

        assertNull(emptyState.clipboardPreview)
        assertTrue(emptyState.quickActions.undoEnabled)
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
        val emptyState = CandidateStripContentResolver.resolve(state).asEmptyState()

        assertNull(emptyState.clipboardPreview)
        assertTrue(emptyState.quickActions.incognitoVisible)
        assertTrue(emptyState.quickActions.undoEnabled)
        assertTrue(emptyState.quickActions.redoEnabled)
        assertTrue(emptyState.quickActions.reconvertEnabled)
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
    fun editorTextSelectedSuppressesClipboardPreview() {
        val state = baseState(
            inputStringEmpty = true,
            tailEmpty = true,
            candidatesShown = false,
            symbolKeyboardShown = false,
            customLayoutPickerShown = false,
            selectedTextGemmaActionsShown = false,
            editorTextSelected = true,
            clipboardPreviewEnabled = true,
            clipboardText = "hello"
        )
        val content = CandidateStripContentResolver.resolve(state)

        assertFalse(
            content is CandidateStripContent.EmptyState &&
                content.clipboardPreview != null
        )
    }

    @Test
    fun clipboardPreviewShown_whenNoEditorTextSelected() {
        val state = baseState(
            inputStringEmpty = true,
            tailEmpty = true,
            candidatesShown = false,
            symbolKeyboardShown = false,
            customLayoutPickerShown = false,
            selectedTextGemmaActionsShown = false,
            editorTextSelected = false,
            clipboardPreviewEnabled = true,
            clipboardText = "hello"
        )
        val emptyState = CandidateStripContentResolver.resolve(state).asEmptyState()

        assertNotNull(emptyState.clipboardPreview)
    }

    @Test
    fun gemmaActionsWinOverClipboardPreview_whenEditorTextSelected() {
        val actions = listOf(candidate("Translate"))
        val state = baseState(
            candidates = actions,
            inputStringEmpty = true,
            tailEmpty = true,
            selectedTextGemmaActionsShown = true,
            editorTextSelected = true,
            clipboardPreviewEnabled = true,
            clipboardText = "hello"
        )
        val content = CandidateStripContentResolver.resolve(state)

        assertTrue(content is CandidateStripContent.GemmaActions)
        assertTrue((content as CandidateStripContent.GemmaActions).actions == actions)
    }

    @Test
    fun clipboardPreviewShown_whenClipboardTextIsLastPastedAndTapToDeleteDisabled() {
        val state = baseState(
            inputStringEmpty = true,
            tailEmpty = true,
            clipboardPreviewEnabled = true,
            clipboardText = "hello",
            clipboardTextIsLastPasted = true,
            clipboardPreviewTapToDelete = false,
            undoEnabled = true
        )
        val emptyState = CandidateStripContentResolver.resolve(state).asEmptyState()

        assertNotNull(emptyState.clipboardPreview)
        assertTrue(emptyState.quickActions.undoEnabled)
    }

    @Test
    fun clipboardPreviewHidden_whenClipboardTextIsLastPastedAndTapToDeleteEnabled() {
        val state = baseState(
            inputStringEmpty = true,
            tailEmpty = true,
            clipboardPreviewEnabled = true,
            clipboardText = "hello",
            clipboardTextIsLastPasted = true,
            clipboardPreviewTapToDelete = true,
            undoEnabled = true
        )
        val emptyState = CandidateStripContentResolver.resolve(state).asEmptyState()

        assertNull(emptyState.clipboardPreview)
        assertTrue(emptyState.quickActions.undoEnabled)
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
        val emptyState = CandidateStripContentResolver.resolve(state).asEmptyState()

        assertTrue(emptyState.showShortcutEntry)
        assertNotNull(emptyState.clipboardPreview)
        assertFalse(emptyState.showIntegratedShortcuts)
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
        val emptyState = CandidateStripContentResolver.resolve(state).asEmptyState()

        assertFalse(emptyState.showShortcutEntry)
        assertNull(emptyState.clipboardPreview)
        assertTrue(emptyState.quickActions.undoEnabled)
        assertTrue(emptyState.showIntegratedShortcuts)
        assertFalse(emptyState.shortcutItems.isEmpty())
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

        assertFalse(
            content is CandidateStripContent.EmptyState &&
                content.clipboardPreview != null
        )
    }

    @Test
    fun emptyStateOrdersShortcutEntryUndoAndClipboardPreview_whenIntegratedShortcutAndUndoAndClipboardAvailable() {
        val state = baseState(
            inputStringEmpty = true,
            tailEmpty = true,
            clipboardPreviewEnabled = true,
            clipboardText = "hello",
            undoEnabled = true,
            shortcutToolbarVisible = true,
            shortcutToolbarIntegratedInSuggestion = true,
            shortcutItems = listOf(ShortcutType.SETTINGS)
        )
        val emptyState = CandidateStripContentResolver.resolve(state).asEmptyState()

        assertTrue(emptyState.showShortcutEntry)
        assertTrue(emptyState.quickActions.undoEnabled)
        assertNotNull(emptyState.clipboardPreview)
        assertFalse(emptyState.showIntegratedShortcuts)
    }

    @Test
    fun emptyStateKeepsUndoAndClipboardPreview_whenShortcutIntegrationOff() {
        val state = baseState(
            inputStringEmpty = true,
            tailEmpty = true,
            clipboardPreviewEnabled = true,
            clipboardText = "hello",
            undoEnabled = true,
            shortcutToolbarVisible = true,
            shortcutToolbarIntegratedInSuggestion = false
        )
        val emptyState = CandidateStripContentResolver.resolve(state).asEmptyState()

        assertFalse(emptyState.showShortcutEntry)
        assertTrue(emptyState.quickActions.undoEnabled)
        assertNotNull(emptyState.clipboardPreview)
    }

    @Test
    fun emptyStateKeepsClipboardPreviewAndAllQuickActions() {
        val state = baseState(
            inputStringEmpty = true,
            tailEmpty = true,
            clipboardPreviewEnabled = true,
            clipboardText = "hello",
            incognitoVisible = true,
            undoEnabled = true,
            redoEnabled = true,
            reconvertEnabled = true
        )
        val emptyState = CandidateStripContentResolver.resolve(state).asEmptyState()

        assertNotNull(emptyState.clipboardPreview)
        assertTrue(emptyState.quickActions.incognitoVisible)
        assertTrue(emptyState.quickActions.undoEnabled)
        assertTrue(emptyState.quickActions.redoEnabled)
        assertTrue(emptyState.quickActions.reconvertEnabled)
    }

    @Test
    fun emptyStateShowsQuickActionsThenShortcutItems_whenClipboardPreviewUnavailable() {
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
        val emptyState = CandidateStripContentResolver.resolve(state).asEmptyState()

        assertFalse(emptyState.showShortcutEntry)
        assertNull(emptyState.clipboardPreview)
        assertTrue(emptyState.quickActions.undoEnabled)
        assertTrue(emptyState.showIntegratedShortcuts)
    }

    @Test
    fun zeroQuerySuggestionsShown_whenAllConditionsMatch() {
        val zeroQueryCandidates = listOf(candidate("おめでとうございます"))
        val state = baseState(
            zeroQueryVisible = true,
            zeroQueryCandidates = zeroQueryCandidates,
            includeZeroQuery = true,
            inputStringEmpty = true,
            tailEmpty = true
        )

        val content = CandidateStripContentResolver.resolve(state)

        assertTrue(content is CandidateStripContent.ZeroQuerySuggestions)
        assertEquals(
            zeroQueryCandidates,
            (content as CandidateStripContent.ZeroQuerySuggestions).candidates
        )
    }

    @Test
    fun zeroQueryCollapsedShown_whenCandidatesAreRetainedButHidden() {
        val state = baseState(
            zeroQueryVisible = false,
            zeroQueryCandidates = listOf(candidate("おめでとうございます")),
            includeZeroQuery = true,
            inputStringEmpty = true,
            tailEmpty = true
        )

        val content = CandidateStripContentResolver.resolve(state)

        assertTrue(content is CandidateStripContent.ZeroQueryCollapsed)
    }

    @Test
    fun zeroQueryCollapsedWinsOverEmptyStateContent() {
        val state = baseState(
            zeroQueryVisible = false,
            zeroQueryCandidates = listOf(candidate("おめでとうございます")),
            includeZeroQuery = true,
            inputStringEmpty = true,
            tailEmpty = true,
            clipboardPreviewEnabled = true,
            clipboardText = "clip",
            undoEnabled = true,
            shortcutToolbarVisible = true,
            shortcutToolbarIntegratedInSuggestion = true,
            shortcutItems = listOf(ShortcutType.SETTINGS)
        )

        val content = CandidateStripContentResolver.resolve(state)

        assertTrue(content is CandidateStripContent.ZeroQueryCollapsed)
    }

    @Test
    fun candidatesWinOverZeroQuerySuggestions() {
        val state = baseState(
            candidates = listOf(candidate("通常候補")),
            zeroQueryVisible = true,
            zeroQueryCandidates = listOf(candidate("後続候補")),
            includeZeroQuery = true,
            inputStringEmpty = true,
            tailEmpty = true
        )

        val content = CandidateStripContentResolver.resolve(state)

        assertTrue(content is CandidateStripContent.Candidates)
    }

    @Test
    fun expandedShortcutEntryWinsOverZeroQuerySuggestions() {
        val state = baseState(
            zeroQueryVisible = true,
            zeroQueryCandidates = listOf(candidate("後続候補")),
            includeZeroQuery = true,
            inputStringEmpty = true,
            tailEmpty = true,
            shortcutToolbarVisible = true,
            shortcutToolbarIntegratedInSuggestion = true,
            integratedShortcutEntryExpanded = true,
            clipboardPreviewEnabled = true,
            clipboardText = "clip",
            shortcutItems = listOf(ShortcutType.SETTINGS)
        )

        val content = CandidateStripContentResolver.resolve(state)

        assertTrue(content is CandidateStripContent.ExpandedShortcutEntry)
    }

    @Test
    fun zeroQuerySuggestionsWinOverEmptyStateContent() {
        val state = baseState(
            zeroQueryVisible = true,
            zeroQueryCandidates = listOf(candidate("後続候補")),
            includeZeroQuery = true,
            inputStringEmpty = true,
            tailEmpty = true,
            clipboardPreviewEnabled = true,
            clipboardText = "clip",
            undoEnabled = true,
            shortcutToolbarVisible = true,
            shortcutToolbarIntegratedInSuggestion = true,
            shortcutItems = listOf(ShortcutType.SETTINGS)
        )

        val content = CandidateStripContentResolver.resolve(state)

        assertTrue(content is CandidateStripContent.ZeroQuerySuggestions)
    }

    @Test
    fun zeroQuerySuggestionsHidden_whenIncludeZeroQueryIsFalse() {
        val state = baseState(
            zeroQueryVisible = true,
            zeroQueryCandidates = listOf(candidate("後続候補")),
            includeZeroQuery = false,
            inputStringEmpty = true,
            tailEmpty = true
        )

        val content = CandidateStripContentResolver.resolve(state)

        assertTrue(content is CandidateStripContent.Empty)
    }

    @Test
    fun zeroQuerySuggestionsHidden_whenInputOrTailBlocksIt() {
        val blockedByInput = baseState(
            zeroQueryVisible = true,
            zeroQueryCandidates = listOf(candidate("後続候補")),
            includeZeroQuery = true,
            inputStringEmpty = false,
            tailEmpty = true
        )
        val blockedByTail = baseState(
            zeroQueryVisible = true,
            zeroQueryCandidates = listOf(candidate("後続候補")),
            includeZeroQuery = true,
            inputStringEmpty = true,
            tailEmpty = false
        )

        assertTrue(CandidateStripContentResolver.resolve(blockedByInput) is CandidateStripContent.Empty)
        assertTrue(CandidateStripContentResolver.resolve(blockedByTail) is CandidateStripContent.Empty)
    }

    @Test
    fun zeroQuerySuggestionsHidden_whenUiStateBlocksIt() {
        val candidates = listOf(candidate("後続候補"))
        val base = baseState(
            zeroQueryVisible = true,
            zeroQueryCandidates = candidates,
            includeZeroQuery = true,
            inputStringEmpty = true,
            tailEmpty = true
        )

        assertTrue(
            CandidateStripContentResolver.resolve(
                base.copy(candidatesShown = true)
            ) is CandidateStripContent.Empty
        )
        assertTrue(
            CandidateStripContentResolver.resolve(
                base.copy(symbolKeyboardShown = true)
            ) is CandidateStripContent.Empty
        )
        assertTrue(
            CandidateStripContentResolver.resolve(
                base.copy(selectedTextGemmaActionsShown = true)
            ) is CandidateStripContent.Empty
        )
        assertTrue(
            CandidateStripContentResolver.resolve(
                base.copy(editorTextSelected = true)
            ) is CandidateStripContent.Empty
        )
    }

    @Test
    fun customLayoutPickerShown_whenZeroQueryBlockedByPickerState() {
        val state = baseState(
            zeroQueryVisible = true,
            zeroQueryCandidates = listOf(candidate("後続候補")),
            includeZeroQuery = true,
            inputStringEmpty = true,
            tailEmpty = true,
            customLayoutPickerShown = true,
            customLayouts = listOf(customLayout("layout"))
        )

        val content = CandidateStripContentResolver.resolve(state)

        assertTrue(content is CandidateStripContent.CustomLayoutPicker)
    }

    private fun baseState(
        candidates: List<Candidate> = emptyList(),
        zeroQueryVisible: Boolean = false,
        zeroQueryCandidates: List<Candidate> = emptyList(),
        includeZeroQuery: Boolean = false,
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
            zeroQueryVisible = zeroQueryVisible,
            zeroQueryCandidates = zeroQueryCandidates,
            includeZeroQuery = includeZeroQuery,
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

    private fun customLayout(name: String): CustomKeyboardLayout =
        CustomKeyboardLayout(
            name = name,
            columnCount = 5,
            rowCount = 4
        )

    private fun CandidateStripContent.asEmptyState(): CandidateStripContent.EmptyState =
        this as? CandidateStripContent.EmptyState
            ?: throw AssertionError("Expected EmptyState but was $this")
}
