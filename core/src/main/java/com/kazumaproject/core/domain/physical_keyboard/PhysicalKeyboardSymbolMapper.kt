package com.kazumaproject.core.domain.physical_keyboard

import android.view.KeyEvent

object PhysicalKeyboardSymbolMapper {
    fun resolve(
        keyCode: Int,
        isShift: Boolean,
        layout: PhysicalKeyboardLayout
    ): String? {
        if (!isShift) return null
        return when (layout) {
            PhysicalKeyboardLayout.US -> usMap[keyCode]
            PhysicalKeyboardLayout.JAPANESE_109A,
            PhysicalKeyboardLayout.JIS -> japaneseMap[keyCode]
        }
    }

    private val usMap = mapOf(
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

    private val japaneseMap = mapOf(
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
        KeyEvent.KEYCODE_BACKSLASH to "|",
        KeyEvent.KEYCODE_SEMICOLON to "+",
        KeyEvent.KEYCODE_APOSTROPHE to "*",
        KeyEvent.KEYCODE_COMMA to "<",
        KeyEvent.KEYCODE_PERIOD to ">",
        KeyEvent.KEYCODE_SLASH to "?",
    )
}
