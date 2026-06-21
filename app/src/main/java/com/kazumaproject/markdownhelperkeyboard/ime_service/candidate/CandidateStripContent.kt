package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import android.graphics.Bitmap
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType

sealed interface CandidateStripContent {
    data class Candidates(
        val candidates: List<Candidate>,
    ) : CandidateStripContent

    data class GemmaActions(
        val actions: List<Candidate>,
        val showShortcutEntry: Boolean,
    ) : CandidateStripContent

    data class CustomLayoutPicker(
        val layouts: List<CustomKeyboardLayout>,
    ) : CandidateStripContent

    data class ExpandedShortcutEntry(
        val shortcutItems: List<ShortcutType>,
    ) : CandidateStripContent

    data class EmptyState(
        val showShortcutEntry: Boolean,
        val quickActions: QuickActionsState,
        val clipboardPreview: ClipboardPreviewState?,
        val shortcutItems: List<ShortcutType>,
        val showIntegratedShortcuts: Boolean,
    ) : CandidateStripContent

    data object Empty : CandidateStripContent
}

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
