package com.kazumaproject.core.domain.physical_keyboard

import android.view.KeyEvent

object PhysicalKanaMapper {
    fun resolve(
        keyCode: Int,
        isShift: Boolean,
        layout: PhysicalKeyboardLayout
    ): String? {
        return if (isShift) shiftedMap[keyCode] ?: baseMap[keyCode] else baseMap[keyCode]
    }

    private val baseMap = mapOf(
        KeyEvent.KEYCODE_1 to "ぬ",
        KeyEvent.KEYCODE_2 to "ふ",
        KeyEvent.KEYCODE_3 to "あ",
        KeyEvent.KEYCODE_4 to "う",
        KeyEvent.KEYCODE_5 to "え",
        KeyEvent.KEYCODE_6 to "お",
        KeyEvent.KEYCODE_7 to "や",
        KeyEvent.KEYCODE_8 to "ゆ",
        KeyEvent.KEYCODE_9 to "よ",
        KeyEvent.KEYCODE_0 to "わ",
        KeyEvent.KEYCODE_Q to "た",
        KeyEvent.KEYCODE_W to "て",
        KeyEvent.KEYCODE_E to "い",
        KeyEvent.KEYCODE_R to "す",
        KeyEvent.KEYCODE_T to "か",
        KeyEvent.KEYCODE_Y to "ん",
        KeyEvent.KEYCODE_U to "な",
        KeyEvent.KEYCODE_I to "に",
        KeyEvent.KEYCODE_O to "ら",
        KeyEvent.KEYCODE_P to "せ",
        KeyEvent.KEYCODE_A to "ち",
        KeyEvent.KEYCODE_S to "と",
        KeyEvent.KEYCODE_D to "し",
        KeyEvent.KEYCODE_F to "は",
        KeyEvent.KEYCODE_G to "き",
        KeyEvent.KEYCODE_H to "く",
        KeyEvent.KEYCODE_J to "ま",
        KeyEvent.KEYCODE_K to "の",
        KeyEvent.KEYCODE_L to "り",
        KeyEvent.KEYCODE_Z to "つ",
        KeyEvent.KEYCODE_X to "さ",
        KeyEvent.KEYCODE_C to "そ",
        KeyEvent.KEYCODE_V to "ひ",
        KeyEvent.KEYCODE_B to "こ",
        KeyEvent.KEYCODE_N to "み",
        KeyEvent.KEYCODE_M to "も",
        KeyEvent.KEYCODE_COMMA to "ね",
        KeyEvent.KEYCODE_PERIOD to "る",
        KeyEvent.KEYCODE_SLASH to "め",
        KeyEvent.KEYCODE_LEFT_BRACKET to "゛",
        KeyEvent.KEYCODE_RIGHT_BRACKET to "゜",
    )

    private val shiftedMap = mapOf(
        KeyEvent.KEYCODE_3 to "ぁ",
        KeyEvent.KEYCODE_4 to "ぅ",
        KeyEvent.KEYCODE_5 to "ぇ",
        KeyEvent.KEYCODE_6 to "ぉ",
        KeyEvent.KEYCODE_7 to "ゃ",
        KeyEvent.KEYCODE_8 to "ゅ",
        KeyEvent.KEYCODE_9 to "ょ",
        KeyEvent.KEYCODE_0 to "を",
        KeyEvent.KEYCODE_E to "ぃ",
        KeyEvent.KEYCODE_Z to "っ",
        KeyEvent.KEYCODE_COMMA to "、",
        KeyEvent.KEYCODE_PERIOD to "。",
        KeyEvent.KEYCODE_SLASH to "・",
        KeyEvent.KEYCODE_RIGHT_BRACKET to "「",
    )
}
