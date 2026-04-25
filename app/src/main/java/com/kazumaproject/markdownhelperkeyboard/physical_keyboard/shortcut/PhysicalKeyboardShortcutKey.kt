package com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut

import android.view.KeyEvent

enum class PhysicalKeyboardShortcutKey(val keyCode: Int, val label: String) {
    A(KeyEvent.KEYCODE_A, "A"), B(KeyEvent.KEYCODE_B, "B"), C(KeyEvent.KEYCODE_C, "C"),
    D(KeyEvent.KEYCODE_D, "D"), E(KeyEvent.KEYCODE_E, "E"), F(KeyEvent.KEYCODE_F, "F"),
    G(KeyEvent.KEYCODE_G, "G"), H(KeyEvent.KEYCODE_H, "H"), I(KeyEvent.KEYCODE_I, "I"),
    J(KeyEvent.KEYCODE_J, "J"), K(KeyEvent.KEYCODE_K, "K"), L(KeyEvent.KEYCODE_L, "L"),
    M(KeyEvent.KEYCODE_M, "M"), N(KeyEvent.KEYCODE_N, "N"), O(KeyEvent.KEYCODE_O, "O"),
    P(KeyEvent.KEYCODE_P, "P"), Q(KeyEvent.KEYCODE_Q, "Q"), R(KeyEvent.KEYCODE_R, "R"),
    S(KeyEvent.KEYCODE_S, "S"), T(KeyEvent.KEYCODE_T, "T"), U(KeyEvent.KEYCODE_U, "U"),
    V(KeyEvent.KEYCODE_V, "V"), W(KeyEvent.KEYCODE_W, "W"), X(KeyEvent.KEYCODE_X, "X"),
    Y(KeyEvent.KEYCODE_Y, "Y"), Z(KeyEvent.KEYCODE_Z, "Z"),
    NUM_0(KeyEvent.KEYCODE_0, "0"), NUM_1(KeyEvent.KEYCODE_1, "1"),
    NUM_2(KeyEvent.KEYCODE_2, "2"), NUM_3(KeyEvent.KEYCODE_3, "3"),
    NUM_4(KeyEvent.KEYCODE_4, "4"), NUM_5(KeyEvent.KEYCODE_5, "5"),
    NUM_6(KeyEvent.KEYCODE_6, "6"), NUM_7(KeyEvent.KEYCODE_7, "7"),
    NUM_8(KeyEvent.KEYCODE_8, "8"), NUM_9(KeyEvent.KEYCODE_9, "9"),
    F1(KeyEvent.KEYCODE_F1, "F1"), F2(KeyEvent.KEYCODE_F2, "F2"),
    F3(KeyEvent.KEYCODE_F3, "F3"), F4(KeyEvent.KEYCODE_F4, "F4"),
    F5(KeyEvent.KEYCODE_F5, "F5"), F6(KeyEvent.KEYCODE_F6, "F6"),
    F7(KeyEvent.KEYCODE_F7, "F7"), F8(KeyEvent.KEYCODE_F8, "F8"),
    F9(KeyEvent.KEYCODE_F9, "F9"), F10(KeyEvent.KEYCODE_F10, "F10"),
    F11(KeyEvent.KEYCODE_F11, "F11"), F12(KeyEvent.KEYCODE_F12, "F12"),
    SPACE(KeyEvent.KEYCODE_SPACE, "Space"), ENTER(KeyEvent.KEYCODE_ENTER, "Enter"),
    BACKSPACE(KeyEvent.KEYCODE_DEL, "Backspace"), DELETE(KeyEvent.KEYCODE_FORWARD_DEL, "Delete"),
    ESCAPE(KeyEvent.KEYCODE_ESCAPE, "Escape"), TAB(KeyEvent.KEYCODE_TAB, "Tab"),
    LEFT(KeyEvent.KEYCODE_DPAD_LEFT, "Left"), RIGHT(KeyEvent.KEYCODE_DPAD_RIGHT, "Right"),
    UP(KeyEvent.KEYCODE_DPAD_UP, "Up"), DOWN(KeyEvent.KEYCODE_DPAD_DOWN, "Down"),
    HENKAN(KeyEvent.KEYCODE_HENKAN, "変換"), MUHENKAN(KeyEvent.KEYCODE_MUHENKAN, "無変換"),
    ZENKAKU_HANKAKU(KeyEvent.KEYCODE_ZENKAKU_HANKAKU, "全角/半角"),
    KANA(KeyEvent.KEYCODE_KANA, "かな"),
    MINUS(KeyEvent.KEYCODE_MINUS, "Minus"), EQUALS(KeyEvent.KEYCODE_EQUALS, "Equals"),
    LEFT_BRACKET(KeyEvent.KEYCODE_LEFT_BRACKET, "LeftBracket"),
    RIGHT_BRACKET(KeyEvent.KEYCODE_RIGHT_BRACKET, "RightBracket"),
    BACKSLASH(KeyEvent.KEYCODE_BACKSLASH, "Backslash"),
    SEMICOLON(KeyEvent.KEYCODE_SEMICOLON, "Semicolon"),
    APOSTROPHE(KeyEvent.KEYCODE_APOSTROPHE, "Apostrophe"),
    COMMA(KeyEvent.KEYCODE_COMMA, "Comma"), PERIOD(KeyEvent.KEYCODE_PERIOD, "Period"),
    SLASH(KeyEvent.KEYCODE_SLASH, "Slash"), GRAVE(KeyEvent.KEYCODE_GRAVE, "Grave");

    companion object {
        fun fromKeyCode(keyCode: Int): PhysicalKeyboardShortcutKey? {
            return entries.firstOrNull { it.keyCode == keyCode }
        }
    }
}
