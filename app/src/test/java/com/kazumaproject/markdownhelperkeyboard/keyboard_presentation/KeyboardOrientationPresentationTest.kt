package com.kazumaproject.markdownhelperkeyboard.keyboard_presentation

import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardOrientationPresentationTest {
    @Test fun portraitKeepsEveryRegisteredFamily() {
        KeyboardType.entries.forEach { assertEquals(it, requestedKeyboardForOrientation(it, false, true, true)) }
    }
    @Test fun landscapeWithoutOverrideKeepsEveryRegisteredFamily() {
        KeyboardType.entries.forEach { assertEquals(it, requestedKeyboardForOrientation(it, true, false, true)) }
    }
    @Test fun landscapeQwertyOverrideIncludesSplitAndCustom() {
        KeyboardType.entries.forEach { assertEquals(KeyboardType.QWERTY, requestedKeyboardForOrientation(it, true, true, false)) }
    }
    @Test fun landscapeRomajiOverrideUsesRomaji() {
        KeyboardType.entries.forEach { assertEquals(KeyboardType.ROMAJI, requestedKeyboardForOrientation(it, true, true, true)) }
    }
}
