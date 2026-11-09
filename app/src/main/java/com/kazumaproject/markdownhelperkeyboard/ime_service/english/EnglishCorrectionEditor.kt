package com.kazumaproject.markdownhelperkeyboard.ime_service.english

import android.os.Build
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

internal data class EnglishCorrectionEditor(
    val before: String,
    val offset: Int,
    val caret: Int,
) {
    fun replace(connection: InputConnection, correction: EnglishGrammarCorrection): Boolean {
        if (capture(connection) != this || before.substring(correction.start, correction.end) != correction.original) {
            return false
        }
        val start = offset + correction.start
        val end = offset + correction.end
        val newCaret = caret + correction.replacement.length - correction.original.length
        if (!connection.beginBatchEdit()) return false
        return try {
            if (!connection.setSelection(start, end)) return false
            if (connection.getSelectedText(0)?.toString() != correction.original ||
                !connection.commitText(correction.replacement, 1)) {
                connection.setSelection(caret, caret)
                return false
            }
            connection.setSelection(newCaret, newCaret)
            true
        } finally {
            connection.endBatchEdit()
        }
    }

    companion object {
        private const val MAX_CONTEXT = 160

        fun capture(connection: InputConnection): EnglishCorrectionEditor? {
            if (Build.VERSION.SDK_INT >= 31) {
                val surrounding = connection.getSurroundingText(MAX_CONTEXT, 1, 0)
                if (surrounding != null && surrounding.offset >= 0) {
                    val selection = surrounding.selectionStart
                    if (selection != surrounding.selectionEnd || selection !in 0..surrounding.text.length) return null
                    if (surrounding.text.getOrNull(selection)?.let { it.isLetterOrDigit() || it in "'’" } == true) {
                        return null
                    }
                    val start = (selection - MAX_CONTEXT).coerceAtLeast(0)
                    return EnglishCorrectionEditor(
                        surrounding.text.subSequence(start, selection).toString(),
                        surrounding.offset + start, surrounding.offset + selection,
                    )
                }
            }

            val extracted = connection.getExtractedText(ExtractedTextRequest(), 0) ?: return null
            val text = extracted.text ?: return null
            val selection = extracted.selectionStart
            if (extracted.startOffset < 0 || extracted.partialStartOffset >= 0 ||
                selection != extracted.selectionEnd || selection !in 0..text.length) return null
            if (text.getOrNull(selection)?.let { it.isLetterOrDigit() || it in "'’" } == true) return null
            val start = (selection - MAX_CONTEXT).coerceAtLeast(0)
            return EnglishCorrectionEditor(text.subSequence(start, selection).toString(),
                extracted.startOffset + start, extracted.startOffset + selection)
        }
    }
}
