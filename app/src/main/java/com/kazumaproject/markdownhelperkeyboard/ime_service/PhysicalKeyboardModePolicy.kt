package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.res.Configuration

/**
 * Resolves whether the IME should use its physical-keyboard UI.
 *
 * InputManager can contain keyboard devices that Android has disabled at the configuration
 * layer. Android can also explicitly request the full software keyboard while hardware keys
 * are present. A device entry alone therefore must not switch the IME into physical mode.
 */
internal object PhysicalKeyboardModePolicy {
    fun shouldUsePhysicalMode(
        configurationKeyboard: Int,
        hardKeyboardHidden: Int,
        hasAlphabeticInputDevice: Boolean,
        showImeWithHardKeyboard: Boolean,
    ): Boolean {
        val configurationHasKeyboard =
            configurationKeyboard == Configuration.KEYBOARD_QWERTY ||
                configurationKeyboard == Configuration.KEYBOARD_12KEY
        val keyboardIsExposed =
            hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES

        return hasAlphabeticInputDevice &&
            configurationHasKeyboard &&
            keyboardIsExposed &&
            !showImeWithHardKeyboard
    }
}
