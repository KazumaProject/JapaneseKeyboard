package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalKeyboardAvailabilityTest {
    @Test
    fun `ignores a stale input device when Android reports no hardware keys`() {
        assertFalse(
            PhysicalKeyboardAvailability.isAvailable(
                configurationKeyboard = Configuration.KEYBOARD_NOKEYS,
                hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_NO,
                hasAlphabeticInputDevice = true,
            )
        )
    }

    @Test
    fun `ignores a built-in keyboard while it is hidden`() {
        assertFalse(
            PhysicalKeyboardAvailability.isAvailable(
                configurationKeyboard = Configuration.KEYBOARD_QWERTY,
                hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_YES,
                hasAlphabeticInputDevice = true,
            )
        )
    }

    @Test
    fun `requires an alphabetic input device even when configuration is qwerty`() {
        assertFalse(
            PhysicalKeyboardAvailability.isAvailable(
                configurationKeyboard = Configuration.KEYBOARD_QWERTY,
                hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_NO,
                hasAlphabeticInputDevice = false,
            )
        )
    }

    @Test
    fun `accepts an exposed alphabetic hardware keyboard`() {
        assertTrue(
            PhysicalKeyboardAvailability.isAvailable(
                configurationKeyboard = Configuration.KEYBOARD_QWERTY,
                hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_NO,
                hasAlphabeticInputDevice = true,
            )
        )
    }
}
