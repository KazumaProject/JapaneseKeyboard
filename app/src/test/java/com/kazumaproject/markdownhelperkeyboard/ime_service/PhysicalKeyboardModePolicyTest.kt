package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalKeyboardModePolicyTest {
    @Test
    fun `ignores a stale input device when Android reports no hardware keys`() {
        assertFalse(
            PhysicalKeyboardModePolicy.shouldUsePhysicalMode(
                configurationKeyboard = Configuration.KEYBOARD_NOKEYS,
                hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_NO,
                hasAlphabeticInputDevice = true,
                showImeWithHardKeyboard = false,
            )
        )
    }

    @Test
    fun `ignores a built-in keyboard while it is hidden`() {
        assertFalse(
            PhysicalKeyboardModePolicy.shouldUsePhysicalMode(
                configurationKeyboard = Configuration.KEYBOARD_QWERTY,
                hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_YES,
                hasAlphabeticInputDevice = true,
                showImeWithHardKeyboard = false,
            )
        )
    }

    @Test
    fun `requires an alphabetic input device even when configuration is qwerty`() {
        assertFalse(
            PhysicalKeyboardModePolicy.shouldUsePhysicalMode(
                configurationKeyboard = Configuration.KEYBOARD_QWERTY,
                hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_NO,
                hasAlphabeticInputDevice = false,
                showImeWithHardKeyboard = false,
            )
        )
    }

    @Test
    fun `uses physical mode for an exposed alphabetic hardware keyboard`() {
        assertTrue(
            PhysicalKeyboardModePolicy.shouldUsePhysicalMode(
                configurationKeyboard = Configuration.KEYBOARD_QWERTY,
                hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_NO,
                hasAlphabeticInputDevice = true,
                showImeWithHardKeyboard = false,
            )
        )
    }

    @Test
    fun `keeps software mode when Android requests the IME with hardware keys`() {
        assertFalse(
            PhysicalKeyboardModePolicy.shouldUsePhysicalMode(
                configurationKeyboard = Configuration.KEYBOARD_QWERTY,
                hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_NO,
                hasAlphabeticInputDevice = true,
                showImeWithHardKeyboard = true,
            )
        )
    }
}
