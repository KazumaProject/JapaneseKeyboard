package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.custom_keyboard.data.KeyCharacterCase

internal enum class CustomKeyboardShiftState {
    OFF,
    ONE_SHOT,
    LOCKED;

    val appliesUppercase: Boolean
        get() = this != OFF

    val keyCharacterCase: KeyCharacterCase
        get() = if (appliesUppercase) {
            KeyCharacterCase.UPPERCASE
        } else {
            KeyCharacterCase.AS_DEFINED
        }

    fun onShiftTap(): CustomKeyboardShiftState = when (this) {
        OFF -> ONE_SHOT
        ONE_SHOT, LOCKED -> OFF
    }

    fun onCapsLockTap(): CustomKeyboardShiftState = when (this) {
        LOCKED -> OFF
        OFF, ONE_SHOT -> LOCKED
    }

    fun consumeOneShot(): CustomKeyboardShiftState =
        if (this == ONE_SHOT) OFF else this

    fun transformAsciiLetters(text: String): String {
        return keyCharacterCase.transformAsciiLetters(text)
    }
}
