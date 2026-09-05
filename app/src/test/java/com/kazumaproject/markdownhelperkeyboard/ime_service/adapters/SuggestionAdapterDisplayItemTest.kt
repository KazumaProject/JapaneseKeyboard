package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaTranslationManager
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateStripContent
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.ClipboardPreviewState
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.InlineSuggestionToggle
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.QuickActionsState
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SuggestionAdapterDisplayItemTest {

    @Test
    fun integratedOffShowsQuickActionsLeftAligned() {
        val adapter = SuggestionAdapter()
        adapter.submitContent(emptyActions(undoEnabled = true))

        assertEquals(
            listOf(SuggestionAdapter.SuggestionDisplayItemKind.QuickActionsItem),
            adapter.buildDisplayItemKindsForTesting()
        )
        adapter.release()
    }

    @Test
    fun integratedOnShowsQuickActionsAndShortcutItemsInSameLane() {
        val adapter = SuggestionAdapter()
        adapter.submitContent(
            emptyActions(
                undoEnabled = true,
                showIntegratedShortcuts = true
            )
        )

        assertEquals(
            listOf(
                SuggestionAdapter.SuggestionDisplayItemKind.QuickActionsItem,
                SuggestionAdapter.SuggestionDisplayItemKind.ShortcutItem,
                SuggestionAdapter.SuggestionDisplayItemKind.ShortcutItem,
            ),
            adapter.buildDisplayItemKindsForTesting()
        )
        adapter.release()
    }

    @Test
    fun integratedShortcutItemsUseStartAnchorSignature() {
        val adapter = SuggestionAdapter()
        adapter.submitContent(emptyActions(showIntegratedShortcuts = true))

        assertEquals(
            SuggestionAdapter.StartAnchorSignature(
                role = SuggestionAdapter.StartAnchorRole.ShortcutItems
            ),
            adapter.buildStartAnchorSignatureForTesting()
        )
        assertTrue(adapter.isStartAnchoredContentExpected())
        adapter.release()
    }

    @Test
    fun emptyStateBuildsShortcutEntryQuickActionsClipboardPreviewInOrder() {
        val adapter = SuggestionAdapter()
        adapter.submitContent(
            CandidateStripContent.EmptyState(
                showShortcutEntry = true,
                quickActions = QuickActionsState(
                    incognitoVisible = false,
                    undoEnabled = true,
                    redoEnabled = false,
                    reconvertEnabled = false,
                    undoText = "元に戻す",
                    redoText = "",
                ),
                clipboardPreview = ClipboardPreviewState(
                    text = "clip",
                    bitmap = null,
                    descriptionShown = true,
                    tapToDelete = false,
                ),
                shortcutItems = shortcuts(),
                showIntegratedShortcuts = false,
            )
        )

        assertEquals(
            listOf(
                SuggestionAdapter.SuggestionDisplayItemKind.ShortcutEntryItem,
                SuggestionAdapter.SuggestionDisplayItemKind.QuickActionsItem,
                SuggestionAdapter.SuggestionDisplayItemKind.ClipboardPreviewItem,
            ),
            adapter.buildDisplayItemKindsForTesting()
        )
        adapter.release()
    }

    @Test
    fun incognitoQuickActionChangesStartAnchorSignature() {
        val adapter = SuggestionAdapter()
        adapter.submitContent(
            emptyActions(
                undoEnabled = true,
                showIntegratedShortcuts = true
            )
        )

        assertEquals(
            SuggestionAdapter.StartAnchorSignature(
                role = SuggestionAdapter.StartAnchorRole.QuickActions,
                quickActions = SuggestionAdapter.QuickActionsVisibilitySignature(
                    incognitoVisible = false,
                    undoVisible = true,
                    redoVisible = false,
                    reconvertVisible = false
                )
            ),
            adapter.buildStartAnchorSignatureForTesting()
        )

        adapter.setIncognitoIcon(ColorDrawable(Color.BLACK))
        adapter.submitContent(
            emptyActions(
                incognitoVisible = true,
                undoEnabled = true,
                showIntegratedShortcuts = true
            )
        )

        assertEquals(
            SuggestionAdapter.StartAnchorSignature(
                role = SuggestionAdapter.StartAnchorRole.QuickActions,
                quickActions = SuggestionAdapter.QuickActionsVisibilitySignature(
                    incognitoVisible = true,
                    undoVisible = true,
                    redoVisible = false,
                    reconvertVisible = false
                )
            ),
            adapter.buildStartAnchorSignatureForTesting()
        )
        assertTrue(adapter.isStartAnchoredContentExpected())
        adapter.release()
    }

    @Test
    fun integratedOffClipboardPreviewDoesNotShowShortcutEntry() {
        val adapter = SuggestionAdapter()
        adapter.submitContent(clipboardPreview(showShortcutEntry = false))

        assertEquals(
            listOf(SuggestionAdapter.SuggestionDisplayItemKind.ClipboardPreviewItem),
            adapter.buildDisplayItemKindsForTesting()
        )
        assertEquals(
            listOf(true),
            adapter.buildClipboardPreviewCenterInStripFlagsForTesting()
        )
        assertEquals(
            listOf(false),
            adapter.buildClipboardPreviewOffsetForLeadingShortcutEntryFlagsForTesting()
        )
        assertEquals(
            listOf(false),
            adapter.buildClipboardPreviewInlineStartMarginFlagsForTesting()
        )
        adapter.release()
    }

    @Test
    fun integratedOnClipboardPreviewShowsShortcutEntry() {
        val adapter = SuggestionAdapter()
        adapter.submitContent(clipboardPreview(showShortcutEntry = true))

        assertEquals(
            listOf(
                SuggestionAdapter.SuggestionDisplayItemKind.ShortcutEntryItem,
                SuggestionAdapter.SuggestionDisplayItemKind.ClipboardPreviewItem,
            ),
            adapter.buildDisplayItemKindsForTesting()
        )
        assertEquals(
            listOf(true),
            adapter.buildClipboardPreviewCenterInStripFlagsForTesting()
        )
        assertEquals(
            listOf(true),
            adapter.buildClipboardPreviewOffsetForLeadingShortcutEntryFlagsForTesting()
        )
        assertEquals(
            listOf(false),
            adapter.buildClipboardPreviewInlineStartMarginFlagsForTesting()
        )
        adapter.release()
    }

    @Test
    fun clipboardPreviewWithQuickActionsIsNotCentered() {
        val adapter = SuggestionAdapter()
        adapter.submitContent(
            CandidateStripContent.EmptyState(
                showShortcutEntry = true,
                quickActions = QuickActionsState(
                    incognitoVisible = false,
                    undoEnabled = true,
                    redoEnabled = false,
                    reconvertEnabled = false,
                    undoText = "元に戻す",
                    redoText = "",
                ),
                clipboardPreview = ClipboardPreviewState(
                    text = "clip",
                    bitmap = null,
                    descriptionShown = true,
                    tapToDelete = false,
                ),
                shortcutItems = shortcuts(),
                showIntegratedShortcuts = false,
            )
        )

        assertEquals(
            listOf(false),
            adapter.buildClipboardPreviewCenterInStripFlagsForTesting()
        )
        assertEquals(
            listOf(false),
            adapter.buildClipboardPreviewOffsetForLeadingShortcutEntryFlagsForTesting()
        )
        assertEquals(
            listOf(true),
            adapter.buildClipboardPreviewInlineStartMarginFlagsForTesting()
        )
        adapter.release()
    }

    @Test
    fun clipboardPreviewWithQuickActionsAndNoShortcutEntryIsNotCentered() {
        val adapter = SuggestionAdapter()
        adapter.submitContent(
            CandidateStripContent.EmptyState(
                showShortcutEntry = false,
                quickActions = QuickActionsState(
                    incognitoVisible = false,
                    undoEnabled = true,
                    redoEnabled = false,
                    reconvertEnabled = false,
                    undoText = "元に戻す",
                    redoText = "",
                ),
                clipboardPreview = ClipboardPreviewState(
                    text = "clip",
                    bitmap = null,
                    descriptionShown = true,
                    tapToDelete = false,
                ),
                shortcutItems = shortcuts(),
                showIntegratedShortcuts = false,
            )
        )

        assertEquals(
            listOf(false),
            adapter.buildClipboardPreviewCenterInStripFlagsForTesting()
        )
        assertEquals(
            listOf(false),
            adapter.buildClipboardPreviewOffsetForLeadingShortcutEntryFlagsForTesting()
        )
        assertEquals(
            listOf(true),
            adapter.buildClipboardPreviewInlineStartMarginFlagsForTesting()
        )
        adapter.release()
    }

    @Test
    fun expandedShortcutEntryReplacesClipboardPreviewWithShortcutItems() {
        val adapter = SuggestionAdapter()
        adapter.submitContent(CandidateStripContent.ExpandedShortcutEntry(shortcuts()))

        assertEquals(
            listOf(
                SuggestionAdapter.SuggestionDisplayItemKind.ShortcutEntryItem,
                SuggestionAdapter.SuggestionDisplayItemKind.ShortcutItem,
                SuggestionAdapter.SuggestionDisplayItemKind.ShortcutItem,
            ),
            adapter.buildDisplayItemKindsForTesting()
        )

        adapter.submitContent(clipboardPreview(showShortcutEntry = true))

        assertEquals(
            listOf(
                SuggestionAdapter.SuggestionDisplayItemKind.ShortcutEntryItem,
                SuggestionAdapter.SuggestionDisplayItemKind.ClipboardPreviewItem,
            ),
            adapter.buildDisplayItemKindsForTesting()
        )
        adapter.release()
    }

    @Test
    fun integratedOffSelectionActionsDoNotShowShortcutEntry() {
        val adapter = SuggestionAdapter()
        adapter.submitContent(
            CandidateStripContent.SelectionActions(
                actions = selectionActions(),
                showShortcutEntry = false
            )
        )

        assertEquals(
            listOf(
                SuggestionAdapter.SuggestionDisplayItemKind.SelectionActionItem,
                SuggestionAdapter.SuggestionDisplayItemKind.SelectionActionItem,
            ),
            adapter.buildDisplayItemKindsForTesting()
        )
        adapter.release()
    }

    @Test
    fun integratedOnSelectionActionsShowShortcutEntry() {
        val adapter = SuggestionAdapter()
        adapter.submitContent(
            CandidateStripContent.SelectionActions(
                actions = selectionActions(),
                showShortcutEntry = true
            )
        )

        assertEquals(
            listOf(
                SuggestionAdapter.SuggestionDisplayItemKind.ShortcutEntryItem,
                SuggestionAdapter.SuggestionDisplayItemKind.SelectionActionItem,
                SuggestionAdapter.SuggestionDisplayItemKind.SelectionActionItem,
            ),
            adapter.buildDisplayItemKindsForTesting()
        )
        adapter.release()
    }

    @Test
    fun expandedShortcutEntryReplacesSelectionActionsWithShortcutItems() {
        val adapter = SuggestionAdapter()
        adapter.submitContent(CandidateStripContent.ExpandedShortcutEntry(shortcuts()))

        assertEquals(
            listOf(
                SuggestionAdapter.SuggestionDisplayItemKind.ShortcutEntryItem,
                SuggestionAdapter.SuggestionDisplayItemKind.ShortcutItem,
                SuggestionAdapter.SuggestionDisplayItemKind.ShortcutItem,
            ),
            adapter.buildDisplayItemKindsForTesting()
        )

        adapter.submitContent(
            CandidateStripContent.SelectionActions(
                actions = selectionActions(),
                showShortcutEntry = true
            )
        )

        assertEquals(
            listOf(
                SuggestionAdapter.SuggestionDisplayItemKind.ShortcutEntryItem,
                SuggestionAdapter.SuggestionDisplayItemKind.SelectionActionItem,
                SuggestionAdapter.SuggestionDisplayItemKind.SelectionActionItem,
            ),
            adapter.buildDisplayItemKindsForTesting()
        )
        adapter.release()
    }

    @Test
    fun normalCandidatesDoNotShowShortcutEntry() {
        val adapter = SuggestionAdapter()
        adapter.submitContent(
            CandidateStripContent.Candidates(
                candidates = listOf(candidate("候補1"), candidate("候補2"))
            )
        )

        assertEquals(
            listOf(
                SuggestionAdapter.SuggestionDisplayItemKind.CandidateItem,
                SuggestionAdapter.SuggestionDisplayItemKind.CandidateItem,
            ),
            adapter.buildDisplayItemKindsForTesting()
        )
        assertFalse(adapter.isStartAnchoredContentExpected())
        adapter.release()
    }

    @Test
    fun inlineSuggestionToggleUsesSelectionActionPresentationBeforeNormalCandidates() {
        val adapter = SuggestionAdapter()
        adapter.submitContent(
            CandidateStripContent.Candidates(
                candidates = listOf(candidate("通常候補")),
                inlineSuggestionToggle = InlineSuggestionToggle(
                    contentDescription = "インライン自動入力候補を表示",
                    badge = "⇄",
                ),
            )
        )

        assertEquals(
            listOf(
                SuggestionAdapter.SuggestionDisplayItemKind.InlineSuggestionToggleItem,
                SuggestionAdapter.SuggestionDisplayItemKind.CandidateItem,
            ),
            adapter.buildDisplayItemKindsForTesting()
        )
        assertEquals(
            listOf(candidate("通常候補")),
            adapter.buildClickCandidatesForTesting()
        )
        assertEquals(
            SuggestionAdapter.StartAnchorSignature(
                role = SuggestionAdapter.StartAnchorRole.InlineSuggestionToggle
            ),
            adapter.buildStartAnchorSignatureForTesting()
        )
        assertTrue(adapter.isStartAnchoredContentExpected())
        adapter.release()
    }

    @Test
    fun inlineSuggestionsAreDisplayedAsToggleThenInlineViewsAndCanReturnToNormalCandidates() {
        val adapter = SuggestionAdapter()
        val application = org.robolectric.RuntimeEnvironment.getApplication()
        val firstInlineView = View(application)
        val secondInlineView = View(application)
        val toggle = InlineSuggestionToggle(
            contentDescription = "通常候補を表示",
            badge = "⇄",
        )
        val content = CandidateStripContent.Candidates(
            candidates = listOf(candidate("通常候補")),
            inlineSuggestionToggle = toggle,
        )

        adapter.submitContent(
            content,
            InlineSuggestionStripState(
                views = listOf(firstInlineView, secondInlineView),
                showInlineSuggestions = true,
                toggle = toggle,
            ),
        )

        assertEquals(
            listOf(
                SuggestionAdapter.SuggestionDisplayItemKind.InlineSuggestionToggleItem,
                SuggestionAdapter.SuggestionDisplayItemKind.InlineSuggestionItem,
                SuggestionAdapter.SuggestionDisplayItemKind.InlineSuggestionItem,
            ),
            adapter.buildDisplayItemKindsForTesting(),
        )
        assertTrue(adapter.isInlineSuggestionStripShown())

        adapter.submitContent(
            content,
            InlineSuggestionStripState(
                views = listOf(firstInlineView, secondInlineView),
                showInlineSuggestions = false,
                toggle = toggle,
            ),
        )

        assertEquals(
            listOf(
                SuggestionAdapter.SuggestionDisplayItemKind.InlineSuggestionToggleItem,
                SuggestionAdapter.SuggestionDisplayItemKind.CandidateItem,
            ),
            adapter.buildDisplayItemKindsForTesting(),
        )
        assertFalse(adapter.isInlineSuggestionStripShown())

        adapter.submitContent(
            content,
            InlineSuggestionStripState(
                views = listOf(firstInlineView, secondInlineView),
                showInlineSuggestions = true,
                toggle = toggle,
            ),
        )

        assertEquals(
            listOf(
                SuggestionAdapter.SuggestionDisplayItemKind.InlineSuggestionToggleItem,
                SuggestionAdapter.SuggestionDisplayItemKind.InlineSuggestionItem,
                SuggestionAdapter.SuggestionDisplayItemKind.InlineSuggestionItem,
            ),
            adapter.buildDisplayItemKindsForTesting(),
        )
        adapter.release()
    }

    @Test
    fun inlineSuggestionSpacingHasOnlyFourDpEdgesAndGaps() {
        val decoration = InlineSuggestionItemDecoration(edgeSpacing = 4, itemSpacing = 4)

        assertEquals(android.graphics.Rect(4, 0, 0, 0), decoration.offsetsForPosition(0, 3))
        assertEquals(android.graphics.Rect(4, 0, 0, 0), decoration.offsetsForPosition(1, 3))
        assertEquals(android.graphics.Rect(4, 0, 4, 0), decoration.offsetsForPosition(2, 3))
    }

    @Test
    fun zeroQuerySuggestionsBuildCloseThenCandidates() {
        val adapter = SuggestionAdapter()
        val candidates = listOf(candidate("おめでとうございます"), candidate("よろしくお願いします"))
        adapter.submitContent(
            CandidateStripContent.ZeroQuerySuggestions(
                candidates = candidates
            )
        )

        assertEquals(
            listOf(
                SuggestionAdapter.SuggestionDisplayItemKind.ZeroQueryCloseItem,
                SuggestionAdapter.SuggestionDisplayItemKind.ZeroQueryCandidateItem,
                SuggestionAdapter.SuggestionDisplayItemKind.ZeroQueryCandidateItem,
            ),
            adapter.buildDisplayItemKindsForTesting()
        )
        assertEquals(
            listOf("[ ... ]", "おめでとうございます", "よろしくお願いします"),
            adapter.buildZeroQueryDisplayTextsForTesting()
        )
        assertTrue(adapter.buildClickCandidatesForTesting().isEmpty())
        adapter.release()
    }

    @Test
    fun hiddenZeroQueryEmptyStateBuildsToggleThenNormalItems() {
        val adapter = SuggestionAdapter()
        adapter.submitContent(
            emptyActions(
                undoEnabled = true,
                showIntegratedShortcuts = true,
                showZeroQueryToggle = true
            )
        )

        assertEquals(
            listOf(
                SuggestionAdapter.SuggestionDisplayItemKind.ZeroQueryCloseItem,
                SuggestionAdapter.SuggestionDisplayItemKind.QuickActionsItem,
                SuggestionAdapter.SuggestionDisplayItemKind.ShortcutItem,
                SuggestionAdapter.SuggestionDisplayItemKind.ShortcutItem,
            ),
            adapter.buildDisplayItemKindsForTesting()
        )
        assertEquals(
            listOf("[ ... ]"),
            adapter.buildZeroQueryDisplayTextsForTesting()
        )
        assertEquals(
            SuggestionAdapter.StartAnchorSignature(
                role = SuggestionAdapter.StartAnchorRole.QuickActions,
                quickActions = SuggestionAdapter.QuickActionsVisibilitySignature(
                    incognitoVisible = false,
                    undoVisible = true,
                    redoVisible = false,
                    reconvertVisible = false
                )
            ),
            adapter.buildStartAnchorSignatureForTesting()
        )
        adapter.release()
    }

    @Test
    fun customLayoutPickerDoesNotShowShortcutEntry() {
        val adapter = SuggestionAdapter()
        adapter.submitContent(
            CandidateStripContent.CustomLayoutPicker(
                listOf(CustomKeyboardLayout(name = "Custom", columnCount = 5, rowCount = 4))
            )
        )

        assertEquals(
            listOf(SuggestionAdapter.SuggestionDisplayItemKind.CustomLayoutItem),
            adapter.buildDisplayItemKindsForTesting()
        )
        adapter.release()
    }

    private fun shortcuts(): List<ShortcutType> =
        listOf(ShortcutType.SETTINGS, ShortcutType.EMOJI)

    private fun emptyActions(
        incognitoVisible: Boolean = false,
        undoEnabled: Boolean = false,
        redoEnabled: Boolean = false,
        reconvertEnabled: Boolean = false,
        showIntegratedShortcuts: Boolean = false,
        showZeroQueryToggle: Boolean = false,
    ): CandidateStripContent.EmptyState =
        CandidateStripContent.EmptyState(
            showShortcutEntry = false,
            quickActions = QuickActionsState(
                incognitoVisible = incognitoVisible,
                undoEnabled = undoEnabled,
                redoEnabled = redoEnabled,
                reconvertEnabled = reconvertEnabled,
                undoText = if (undoEnabled) "元に戻す" else "",
                redoText = if (redoEnabled) "やり直す" else "",
            ),
            clipboardPreview = null,
            shortcutItems = shortcuts(),
            showIntegratedShortcuts = showIntegratedShortcuts,
            showZeroQueryToggle = showZeroQueryToggle,
        )

    private fun clipboardPreview(
        showShortcutEntry: Boolean
    ): CandidateStripContent.EmptyState =
        CandidateStripContent.EmptyState(
            showShortcutEntry = showShortcutEntry,
            quickActions = QuickActionsState(
                incognitoVisible = false,
                undoEnabled = false,
                redoEnabled = false,
                reconvertEnabled = false,
                undoText = "",
                redoText = "",
            ),
            clipboardPreview = ClipboardPreviewState(
                text = "clip",
                bitmap = null,
                descriptionShown = true,
                tapToDelete = false,
            ),
            shortcutItems = shortcuts(),
            showIntegratedShortcuts = false,
        )

    private fun selectionActions(): List<Candidate> =
        listOf(
            candidate(
                string = "Translate",
                type = GemmaTranslationManager.SELECTION_TRANSLATE_ACTION_CANDIDATE_TYPE.toByte()
            ),
            candidate(
                string = "Prompt",
                type = GemmaTranslationManager.SELECTION_PROMPT_ACTION_CANDIDATE_TYPE.toByte()
            )
        )

    private fun candidate(
        string: String,
        type: Byte = 1.toByte()
    ): Candidate {
        return Candidate(
            string = string,
            type = type,
            length = string.length.toUByte(),
            score = 0,
            yomi = string
        )
    }
}
