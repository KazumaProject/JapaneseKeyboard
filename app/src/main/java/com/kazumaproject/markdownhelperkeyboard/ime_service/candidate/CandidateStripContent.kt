package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import android.graphics.Bitmap
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType

sealed interface CandidateStripContent {
    data class Candidates(
        val candidates: List<Candidate>,
        val showShortcutEntry: Boolean,
    ) : CandidateStripContent

    data class GemmaActions(
        val actions: List<Candidate>,
        val showShortcutEntry: Boolean,
    ) : CandidateStripContent

    data class CustomLayoutPicker(
        val layouts: List<CustomKeyboardLayout>,
    ) : CandidateStripContent

    data class ClipboardPreview(
        val text: String,
        val bitmap: Bitmap?,
        val descriptionShown: Boolean,
        val tapToDelete: Boolean,
        val showShortcutEntry: Boolean,
    ) : CandidateStripContent

    data class EmptyStateActions(
        val incognitoVisible: Boolean,
        val undoEnabled: Boolean,
        val redoEnabled: Boolean,
        val reconvertEnabled: Boolean,
        val undoText: String,
        val redoText: String,
        val shortcutItems: List<ShortcutType>,
        val showIntegratedShortcuts: Boolean,
    ) : CandidateStripContent

    data class IntegratedShortcuts(
        val shortcutItems: List<ShortcutType>,
    ) : CandidateStripContent

    data class ExpandedShortcutEntry(
        val shortcutItems: List<ShortcutType>,
    ) : CandidateStripContent

    data object Empty : CandidateStripContent
}
