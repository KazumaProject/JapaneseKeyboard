package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

/** Commit raw input without conversion, leaving the cursor before the preserved tail. */
internal fun InputConnection.commitRawTextAndInsertSpace(rawText: String, tail: String): Boolean {
    beginBatchEdit()
    try {
        if (!commitText("$rawText $tail", 1)) return false
        // commitText already handles selected text, changed composing lengths, and editor filters.
        // Only a preserved tail requires moving the cursor away from the committed end.
        if (tail.isNotEmpty()) {
            val extracted = getExtractedText(ExtractedTextRequest(), 0)
            if (extracted != null && extracted.selectionEnd >= 0 && extracted.startOffset >= 0) {
                val cursor = extracted.startOffset + extracted.selectionEnd - tail.length
                if (cursor >= 0) setSelection(cursor, cursor)
            }
        }
        return true
    } finally {
        endBatchEdit()
    }
}
