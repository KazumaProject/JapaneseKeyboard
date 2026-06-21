package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import android.graphics.Bitmap
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType

data class CandidateStripInputState(
    val candidates: List<Candidate>,
    val inputStringEmpty: Boolean,
    val tailEmpty: Boolean,
    val candidatesShown: Boolean,
    val symbolKeyboardShown: Boolean,
    val customLayoutPickerShown: Boolean,
    val customLayouts: List<CustomKeyboardLayout>,
    val selectedTextGemmaActionsShown: Boolean,
    val clipboardPreviewEnabled: Boolean,
    val clipboardPreviewDescriptionShown: Boolean,
    val clipboardPreviewTapToDelete: Boolean,
    val clipboardText: String,
    val clipboardBitmap: Bitmap?,
    val clipboardTextIsLastPasted: Boolean,
    val incognitoVisible: Boolean,
    val undoEnabled: Boolean,
    val redoEnabled: Boolean,
    val reconvertEnabled: Boolean,
    val undoText: String,
    val redoText: String,
    val shortcutToolbarVisible: Boolean,
    val shortcutToolbarIntegratedInSuggestion: Boolean,
    val integratedShortcutEntryExpanded: Boolean,
    val shortcutItems: List<ShortcutType>,
)
