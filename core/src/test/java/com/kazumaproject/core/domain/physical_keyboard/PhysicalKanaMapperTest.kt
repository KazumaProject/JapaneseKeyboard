package com.kazumaproject.core.domain.physical_keyboard

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhysicalKanaMapperTest {
    @Test
    fun resolve_kanaKeys() {
        assertEquals("ぬ", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_1, false))
        assertEquals("ぁ", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_3, true))
        assertEquals("た", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_Q, false))
        assertEquals("て", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_W, false))
        assertEquals("い", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_E, false))
        assertEquals("っ", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_Z, true))
        assertEquals("め", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_SLASH, false))
        assertEquals("・", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_SLASH, true))
        assertEquals("゛", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_LEFT_BRACKET, false))
        assertEquals("゜", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_RIGHT_BRACKET, false))
    }

    @Test
    fun resolve_japaneseDedicatedKanaKeys() {
        assertEquals("ー", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_YEN, false))
        assertEquals("ー", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_YEN, true))
        assertEquals("ろ", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_RO, false))
        assertEquals("ろ", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_RO, true))
    }

    @Test
    fun resolve_returnsNullForUnsupportedKey() {
        assertNull(PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_DPAD_LEFT, false))
    }
}
