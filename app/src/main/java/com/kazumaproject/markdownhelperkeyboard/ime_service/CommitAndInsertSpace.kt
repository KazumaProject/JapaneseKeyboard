package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.view.inputmethod.InputConnection

/** Commit raw input without conversion, leaving the cursor before the preserved tail. */
internal fun InputConnection.commitRawTextAndInsertSpace(rawText: String, tail: String): Boolean {
    beginBatchEdit()
    try {
        // A zero cursor position is relative to the start of the replacement.
        // Commit the tail first, then insert before it, so neither text extraction
        // nor assumptions about lengths after editor filters are needed.
        if (tail.isNotEmpty() && !commitText(tail, 0)) return false
        return commitText("$rawText ", 1)
    } finally {
        endBatchEdit()
    }
}
