package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.res.Configuration

/**
 * Resolves whether a detected alphabetic input device is currently exposed to the user.
 *
 * InputManager can contain built-in keyboard devices that Android has disabled at the
 * configuration layer. A device entry alone therefore does not mean that the user has an
 * available hardware keyboard.
 */
internal object PhysicalKeyboardAvailability {
    fun isAvailable(
        configurationKeyboard: Int,
        hardKeyboardHidden: Int,
        hasAlphabeticInputDevice: Boolean,
    ): Boolean {
        val configurationHasKeyboard =
            configurationKeyboard == Configuration.KEYBOARD_QWERTY ||
                configurationKeyboard == Configuration.KEYBOARD_12KEY
        val keyboardIsExposed =
            hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES

        return hasAlphabeticInputDevice && configurationHasKeyboard && keyboardIsExposed
    }
}
