package com.kazumaproject.markdownhelperkeyboard.ime_service.state

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardTypeTest {
    @Test
    fun gojuonUsesTenkeySizeFamily() {
        assertTrue(KeyboardType.TENKEY.isTenKeyFamily)
        assertTrue(KeyboardType.GOJUON.isTenKeyFamily)
        assertTrue(KeyboardType.SUMIRE.isTenKeyFamily)
        assertTrue(KeyboardType.CUSTOM.isTenKeyFamily)
        assertFalse(KeyboardType.QWERTY.isTenKeyFamily)
        assertFalse(KeyboardType.ROMAJI.isTenKeyFamily)
    }
}
