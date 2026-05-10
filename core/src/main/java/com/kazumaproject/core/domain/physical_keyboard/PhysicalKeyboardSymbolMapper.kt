package com.kazumaproject.core.domain.physical_keyboard

import android.view.KeyEvent

object PhysicalKeyboardSymbolMapper {
    private data class SymbolLayoutMap(
        val base: Map<Int, String>,
        val shifted: Map<Int, String>
    )

    /**
     * Symbol fallback for physical keyboard layouts.
     *
     * Unshifted letters and digits are left to KeyEvent/Romaji handling so existing romaji input and
     * number preferences keep their current path. Punctuation keys that differ on Japanese layouts,
     * plus shifted symbols, are handled here.
     */
    fun resolve(
        keyCode: Int,
        isShift: Boolean,
        layout: PhysicalKeyboardLayout
    ): String? {
        val layoutMap = when (layout) {
            PhysicalKeyboardLayout.US -> usMap
            PhysicalKeyboardLayout.JAPANESE_109A,
            PhysicalKeyboardLayout.JIS -> japaneseOadgMap
        }
        return if (isShift) {
            layoutMap.shifted[keyCode] ?: layoutMap.base[keyCode]
        } else {
            layoutMap.base[keyCode]
        }
    }

    private val usMap = SymbolLayoutMap(
        base = mapOf(
            KeyEvent.KEYCODE_MINUS to "-",
            KeyEvent.KEYCODE_EQUALS to "=",
            KeyEvent.KEYCODE_LEFT_BRACKET to "[",
            KeyEvent.KEYCODE_RIGHT_BRACKET to "]",
            KeyEvent.KEYCODE_BACKSLASH to "\\",
            KeyEvent.KEYCODE_SEMICOLON to ";",
            KeyEvent.KEYCODE_APOSTROPHE to "'",
            KeyEvent.KEYCODE_COMMA to ",",
            KeyEvent.KEYCODE_PERIOD to ".",
            KeyEvent.KEYCODE_SLASH to "/",
            KeyEvent.KEYCODE_GRAVE to "`",
        ),
        shifted = mapOf(
            KeyEvent.KEYCODE_1 to "!",
            KeyEvent.KEYCODE_2 to "@",
            KeyEvent.KEYCODE_3 to "#",
            KeyEvent.KEYCODE_4 to "$",
            KeyEvent.KEYCODE_5 to "%",
            KeyEvent.KEYCODE_6 to "^",
            KeyEvent.KEYCODE_7 to "&",
            KeyEvent.KEYCODE_8 to "*",
            KeyEvent.KEYCODE_9 to "(",
            KeyEvent.KEYCODE_0 to ")",
            KeyEvent.KEYCODE_MINUS to "_",
            KeyEvent.KEYCODE_EQUALS to "+",
            KeyEvent.KEYCODE_LEFT_BRACKET to "{",
            KeyEvent.KEYCODE_RIGHT_BRACKET to "}",
            KeyEvent.KEYCODE_BACKSLASH to "|",
            KeyEvent.KEYCODE_SEMICOLON to ":",
            KeyEvent.KEYCODE_APOSTROPHE to "\"",
            KeyEvent.KEYCODE_COMMA to "<",
            KeyEvent.KEYCODE_PERIOD to ">",
            KeyEvent.KEYCODE_SLASH to "?",
            KeyEvent.KEYCODE_GRAVE to "~",
        )
    )

    private val japaneseOadgMap = SymbolLayoutMap(
        base = mapOf(
            KeyEvent.KEYCODE_MINUS to "-",
            KeyEvent.KEYCODE_EQUALS to "^",
            KeyEvent.KEYCODE_LEFT_BRACKET to "@",
            KeyEvent.KEYCODE_RIGHT_BRACKET to "[",
            KeyEvent.KEYCODE_BACKSLASH to "]",
            KeyEvent.KEYCODE_SEMICOLON to ";",
            KeyEvent.KEYCODE_APOSTROPHE to ":",
            KeyEvent.KEYCODE_COMMA to ",",
            KeyEvent.KEYCODE_PERIOD to ".",
            KeyEvent.KEYCODE_SLASH to "/",
            KeyEvent.KEYCODE_GRAVE to "`",
            KeyEvent.KEYCODE_AT to "@",
            KeyEvent.KEYCODE_YEN to "¥",
            KeyEvent.KEYCODE_RO to "\\",
        ),
        shifted = mapOf(
            KeyEvent.KEYCODE_1 to "!",
            KeyEvent.KEYCODE_2 to "\"",
            KeyEvent.KEYCODE_3 to "#",
            KeyEvent.KEYCODE_4 to "$",
            KeyEvent.KEYCODE_5 to "%",
            KeyEvent.KEYCODE_6 to "&",
            KeyEvent.KEYCODE_7 to "'",
            KeyEvent.KEYCODE_8 to "(",
            KeyEvent.KEYCODE_9 to ")",
            KeyEvent.KEYCODE_MINUS to "=",
            KeyEvent.KEYCODE_EQUALS to "~",
            KeyEvent.KEYCODE_LEFT_BRACKET to "`",
            KeyEvent.KEYCODE_RIGHT_BRACKET to "{",
            KeyEvent.KEYCODE_BACKSLASH to "}",
            KeyEvent.KEYCODE_SEMICOLON to "+",
            KeyEvent.KEYCODE_APOSTROPHE to "*",
            KeyEvent.KEYCODE_COMMA to "<",
            KeyEvent.KEYCODE_PERIOD to ">",
            KeyEvent.KEYCODE_SLASH to "?",
            KeyEvent.KEYCODE_AT to "`",
            KeyEvent.KEYCODE_YEN to "|",
            KeyEvent.KEYCODE_RO to "_",
        )
    )
}
