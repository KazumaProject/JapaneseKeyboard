package com.kazumaproject.core.domain.physical_keyboard

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhysicalKanaMapperTest {
    @Test
    fun resolve_kanaKeys() {
        assertEquals("ぬ", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_1, false, PhysicalKeyboardLayout.JAPANESE_109A))
        assertEquals("ぁ", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_3, true, PhysicalKeyboardLayout.JAPANESE_109A))
        assertEquals("た", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_Q, false, PhysicalKeyboardLayout.JAPANESE_109A))
        assertEquals("っ", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_Z, true, PhysicalKeyboardLayout.JAPANESE_109A))
        assertEquals("め", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_SLASH, false, PhysicalKeyboardLayout.JAPANESE_109A))
        assertEquals("・", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_SLASH, true, PhysicalKeyboardLayout.JAPANESE_109A))
        assertEquals("゛", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_LEFT_BRACKET, false, PhysicalKeyboardLayout.JAPANESE_109A))
        assertEquals("゜", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_RIGHT_BRACKET, false, PhysicalKeyboardLayout.JAPANESE_109A))
    }

    @Test
    fun resolve_returnsNullForUnsupportedKey() {
        assertNull(PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_DPAD_LEFT, false, PhysicalKeyboardLayout.JAPANESE_109A))
    }
}
