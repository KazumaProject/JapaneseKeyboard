package com.kazumaproject.markdownhelperkeyboard.physical_keyboard.romaji

import android.view.KeyEvent

object PhysicalRomajiSymbolNormalizer {

    fun normalize(
        keyCode: Int,
        resolvedUnicode: Int,
        isShiftPressed: Boolean
    ): Int {
        if (resolvedUnicode == 0) return 0
        if (isShiftPressed) return resolvedUnicode

        return when (keyCode) {
            KeyEvent.KEYCODE_COMMA -> '、'.code
            KeyEvent.KEYCODE_PERIOD -> '。'.code
            KeyEvent.KEYCODE_MINUS -> 'ー'.code
            else -> resolvedUnicode
        }
    }
}
