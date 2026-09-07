package com.kazumaproject.markdownhelperkeyboard.enter

import android.text.InputType
import android.view.inputmethod.EditorInfo

/** A case describes inputs only. Expectations must come from reviewed observations. */
data class EnterProbeCase(
    val id: String,
    val inputType: Int,
    val imeOptions: Int,
    val state: String = "committed",
    val actionId: Int = 0,
    val actionLabel: String? = null,
    val text: String = "abc",
    val selectionStart: Int = text.length,
    val selectionEnd: Int = selectionStart,
    val operation: String = "tap-twice",
    val setupCheckpoints: List<String> = emptyList(),
) {
    companion object {
        fun all(): List<EnterProbeCase> = buildList {
            for (action in 0..8) for (flags in 0..7) {
                val type = InputType.TYPE_CLASS_TEXT or
                    (if (flags and 1 != 0) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0) or
                    (if (flags and 2 != 0) InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE else 0)
                val options = (if (action == 8) EditorInfo.IME_ACTION_UNSPECIFIED else action) or
                    (if (flags and 4 != 0) EditorInfo.IME_FLAG_NO_ENTER_ACTION else 0)
                for (state in listOf("committed", "composing")) {
                    add(EnterProbeCase("action-$action-flags-$flags-$state", type, options, state,
                        if (action == 8) 12345 else 0, if (action == 8) "Probe action" else null))
                }
            }
            // The priority matrix is composing vs. committed. Other editing states are
            // individual cases, not an unnecessary full cross-product.
            for (state in listOf("converting", "segment", "composing-middle")) {
                add(EnterProbeCase("action-1-flags-5-$state",
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                    EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION, state,
                    operation = if (state == "segment") "tap-thrice" else "tap-twice",
                    setupCheckpoints = if (state == "composing-middle") listOf("abcあいな", "abcあな") else emptyList()))
            }
            val types = linkedMapOf(
                "uri" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI),
                "email" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),
                "password" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD),
                "short-message" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE),
                "long-message" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE),
                "number" to InputType.TYPE_CLASS_NUMBER,
                "decimal" to (InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL),
                "phone" to InputType.TYPE_CLASS_PHONE,
                "date" to (InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_DATE),
                "null" to InputType.TYPE_NULL,
            )
            types.forEach { (name, type) -> for (action in 0..7) {
                add(EnterProbeCase("type-$name-action-$action", type, action, text = "123"))
            } }
            val multiline = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            for ((name, text, start, end) in listOf(
                Position("empty", "", 0, 0), Position("start", "abc", 0, 0),
                Position("middle", "abc", 1, 1), Position("selection", "abc", 0, 2),
                Position("before-newline", "a\nb", 1, 1), Position("after-newline", "a\nb", 2, 2),
            )) add(EnterProbeCase("position-$name", multiline, EditorInfo.IME_ACTION_NONE,
                text = text, selectionStart = start, selectionEnd = end))
            for (operation in listOf("rapid", "long", "physical", "shift", "ctrl", "alt", "numpad")) {
                add(EnterProbeCase("operation-$operation", multiline, EditorInfo.IME_ACTION_NONE, operation = operation))
            }
        }
        private data class Position(val name: String, val text: String, val start: Int, val end: Int)
    }
}
