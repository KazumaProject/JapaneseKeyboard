package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaTranslationManager
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import org.junit.Assert.assertEquals
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
        adapter.setUndoEnabled(true)
        adapter.setShortcutItems(shortcuts())
        adapter.setIntegratedShortcutItemsVisibility(false)

        assertEquals(
            listOf(SuggestionAdapter.SuggestionDisplayItemKind.QuickActionsItem),
            adapter.buildDisplayItemKindsForTesting()
        )
        adapter.release()
    }

    @Test
    fun integratedOnShowsQuickActionsAndShortcutItemsInSameLane() {
        val adapter = SuggestionAdapter()
        adapter.setUndoEnabled(true)
        adapter.setShortcutItems(shortcuts())
        adapter.setIntegratedShortcutItemsVisibility(true)

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
    fun integratedOffClipboardPreviewDoesNotShowShortcutEntry() {
        val adapter = SuggestionAdapter()
        adapter.setShortcutItems(shortcuts())
        adapter.setIntegratedShortcutEntryVisibility(false)
        adapter.setClipboardPreview("clip")

        assertEquals(
            listOf(SuggestionAdapter.SuggestionDisplayItemKind.ClipboardPreviewItem),
            adapter.buildDisplayItemKindsForTesting()
        )
        adapter.release()
    }

    @Test
    fun integratedOnClipboardPreviewShowsShortcutEntry() {
        val adapter = SuggestionAdapter()
        adapter.setShortcutItems(shortcuts())
        adapter.setIntegratedShortcutEntryVisibility(true)
        adapter.setClipboardPreview("clip")

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
    fun expandedShortcutEntryReplacesClipboardPreviewWithShortcutItems() {
        val adapter = SuggestionAdapter()
        adapter.setShortcutItems(shortcuts())
        adapter.setIntegratedShortcutEntryVisibility(true)
        adapter.setClipboardPreview("clip")
        adapter.setIntegratedShortcutEntryExpanded(true)

        assertEquals(
            listOf(
                SuggestionAdapter.SuggestionDisplayItemKind.ShortcutEntryItem,
                SuggestionAdapter.SuggestionDisplayItemKind.ShortcutItem,
                SuggestionAdapter.SuggestionDisplayItemKind.ShortcutItem,
            ),
            adapter.buildDisplayItemKindsForTesting()
        )

        adapter.setIntegratedShortcutEntryExpanded(false)

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
    fun integratedOffGemmaActionsDoNotShowShortcutEntry() {
        val adapter = SuggestionAdapter()
        adapter.setShortcutItems(shortcuts())
        adapter.setIntegratedShortcutEntryVisibility(false)
        adapter.suggestions = gemmaActions()

        assertEquals(
            listOf(
                SuggestionAdapter.SuggestionDisplayItemKind.GemmaActionItem,
                SuggestionAdapter.SuggestionDisplayItemKind.GemmaActionItem,
            ),
            adapter.buildDisplayItemKindsForTesting()
        )
        adapter.release()
    }

    @Test
    fun integratedOnGemmaActionsShowShortcutEntry() {
        val adapter = SuggestionAdapter()
        adapter.setShortcutItems(shortcuts())
        adapter.setIntegratedShortcutEntryVisibility(true)
        adapter.suggestions = gemmaActions()

        assertEquals(
            listOf(
                SuggestionAdapter.SuggestionDisplayItemKind.ShortcutEntryItem,
                SuggestionAdapter.SuggestionDisplayItemKind.GemmaActionItem,
                SuggestionAdapter.SuggestionDisplayItemKind.GemmaActionItem,
            ),
            adapter.buildDisplayItemKindsForTesting()
        )
        adapter.release()
    }

    @Test
    fun expandedShortcutEntryReplacesGemmaActionsWithShortcutItems() {
        val adapter = SuggestionAdapter()
        adapter.setShortcutItems(shortcuts())
        adapter.setIntegratedShortcutEntryVisibility(true)
        adapter.suggestions = gemmaActions()
        adapter.setIntegratedShortcutEntryExpanded(true)

        assertEquals(
            listOf(
                SuggestionAdapter.SuggestionDisplayItemKind.ShortcutEntryItem,
                SuggestionAdapter.SuggestionDisplayItemKind.ShortcutItem,
                SuggestionAdapter.SuggestionDisplayItemKind.ShortcutItem,
            ),
            adapter.buildDisplayItemKindsForTesting()
        )

        adapter.setIntegratedShortcutEntryExpanded(false)

        assertEquals(
            listOf(
                SuggestionAdapter.SuggestionDisplayItemKind.ShortcutEntryItem,
                SuggestionAdapter.SuggestionDisplayItemKind.GemmaActionItem,
                SuggestionAdapter.SuggestionDisplayItemKind.GemmaActionItem,
            ),
            adapter.buildDisplayItemKindsForTesting()
        )
        adapter.release()
    }

    @Test
    fun normalCandidatesDoNotShowShortcutEntry() {
        val adapter = SuggestionAdapter()
        adapter.setShortcutItems(shortcuts())
        adapter.setIntegratedShortcutEntryVisibility(true)
        adapter.suggestions = listOf(candidate("候補1"), candidate("候補2"))

        assertEquals(
            listOf(
                SuggestionAdapter.SuggestionDisplayItemKind.CandidateItem,
                SuggestionAdapter.SuggestionDisplayItemKind.CandidateItem,
            ),
            adapter.buildDisplayItemKindsForTesting()
        )
        adapter.release()
    }

    @Test
    fun customLayoutPickerDoesNotShowShortcutEntry() {
        val adapter = SuggestionAdapter()
        adapter.setShortcutItems(shortcuts())
        adapter.setIntegratedShortcutEntryVisibility(true)
        adapter.updateState(
            TenKeyQWERTYMode.Custom,
            listOf(CustomKeyboardLayout(name = "Custom", columnCount = 5, rowCount = 4))
        )

        assertEquals(
            listOf(SuggestionAdapter.SuggestionDisplayItemKind.CustomLayoutItem),
            adapter.buildDisplayItemKindsForTesting()
        )
        adapter.release()
    }

    private fun shortcuts(): List<ShortcutType> =
        listOf(ShortcutType.SETTINGS, ShortcutType.EMOJI)

    private fun gemmaActions(): List<Candidate> =
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
