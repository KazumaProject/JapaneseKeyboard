package com.kazumaproject.markdownhelperkeyboard.text_macro

import android.view.inputmethod.InputConnection

/** Commits a pre-expanded macro without relying on an editor's absolute cursor coordinates. */
object TextMacroInputConnectionExecutor {
    fun commit(inputConnection: InputConnection, macro: ExpandedMacro): Boolean {
        val cursor = macro.cursorOffset.coerceIn(0, macro.text.length)
        inputConnection.beginBatchEdit()
        return try {
            when (cursor) {
                macro.text.length -> inputConnection.commitText(macro.text, 1)
                0 -> inputConnection.commitText(macro.text, 0)
                else -> {
                    val prefixCommitted = inputConnection.commitText(
                        macro.text.substring(0, cursor),
                        1,
                    )
                    val suffixCommitted = inputConnection.commitText(
                        macro.text.substring(cursor),
                        0,
                    )
                    prefixCommitted && suffixCommitted
                }
            }
        } finally {
            inputConnection.endBatchEdit()
        }
    }
}

