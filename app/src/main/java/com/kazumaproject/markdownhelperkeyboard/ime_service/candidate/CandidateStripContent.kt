package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType

sealed interface CandidateStripContent {
    data class Candidates(
        val candidates: List<Candidate>,
        val inlineSuggestionToggle: InlineSuggestionToggle? = null,
    ) : CandidateStripContent

    /** Explicit actions for selected text: local macros first, then translation and prompts. */
    data class SelectionActions(
        val actions: List<Candidate>,
        val showShortcutEntry: Boolean,
        val inlineSuggestionToggle: InlineSuggestionToggle? = null,
    ) : CandidateStripContent

    data class ZeroQuerySuggestions(
        val candidates: List<Candidate>,
        val inlineSuggestionToggle: InlineSuggestionToggle? = null,
    ) : CandidateStripContent

    data class CustomLayoutPicker(
        val layouts: List<CustomKeyboardLayout>,
        val inlineSuggestionToggle: InlineSuggestionToggle? = null,
    ) : CandidateStripContent

    data class ExpandedShortcutEntry(
        val shortcutItems: List<ShortcutType>,
        val inlineSuggestionToggle: InlineSuggestionToggle? = null,
    ) : CandidateStripContent

    data class EmptyState(
        val showShortcutEntry: Boolean,
        val quickActions: QuickActionsState,
        val clipboardPreview: ClipboardPreviewState?,
        val shortcutItems: List<ShortcutType>,
        val showIntegratedShortcuts: Boolean,
        val showZeroQueryToggle: Boolean = false,
        val inlineSuggestionToggle: InlineSuggestionToggle? = null,
    ) : CandidateStripContent

    data object Empty : CandidateStripContent
}

/** A candidate-strip action that switches between inline autofill and keyboard candidates. */
data class InlineSuggestionToggle(
    val contentDescription: String,
    val badge: String? = null,
    @DrawableRes val iconResId: Int? = null,
)

data class QuickActionsState(
    val incognitoVisible: Boolean,
    val undoEnabled: Boolean,
    val redoEnabled: Boolean,
    val reconvertEnabled: Boolean,
    val undoText: String,
    val redoText: String,
) {
    val hasAnyAction: Boolean
        get() = incognitoVisible || undoEnabled || redoEnabled || reconvertEnabled
}

data class ClipboardPreviewState(
    val text: String,
    val bitmap: Bitmap?,
    val descriptionShown: Boolean,
    val tapToDelete: Boolean,
)
