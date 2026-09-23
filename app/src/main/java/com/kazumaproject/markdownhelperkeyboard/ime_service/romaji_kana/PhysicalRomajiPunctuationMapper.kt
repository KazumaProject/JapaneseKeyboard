package com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana

import android.view.KeyEvent

/**
 * Normalize unshifted punctuation from a physical keyboard in Japanese romaji mode.
 * Android key character maps may report ASCII or fullwidth Latin punctuation for these keys.
 * Layouts that already report Japanese punctuation are left untouched.
 */
internal object PhysicalRomajiPunctuationMapper {
    fun map(keyCode: Int, unicode: Int, isShiftPressed: Boolean): Int {
        if (isShiftPressed) return unicode

        return when {
            keyCode == KeyEvent.KEYCODE_COMMA &&
                (unicode == ','.code || unicode == '，'.code) -> '、'.code
            keyCode == KeyEvent.KEYCODE_PERIOD &&
                (unicode == '.'.code || unicode == '．'.code) -> '。'.code
            else -> unicode
        }
    }
}
