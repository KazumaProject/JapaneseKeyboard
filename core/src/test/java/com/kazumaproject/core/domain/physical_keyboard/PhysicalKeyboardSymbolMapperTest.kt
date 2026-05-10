package com.kazumaproject.core.domain.physical_keyboard

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhysicalKeyboardSymbolMapperTest {
    @Test
    fun resolve_usAndJapaneseSymbols() {
        assertEquals("@", PhysicalKeyboardSymbolMapper.resolve(KeyEvent.KEYCODE_2, true, PhysicalKeyboardLayout.US))
        assertEquals("\"", PhysicalKeyboardSymbolMapper.resolve(KeyEvent.KEYCODE_2, true, PhysicalKeyboardLayout.JAPANESE_109A))
        assertEquals("\"", PhysicalKeyboardSymbolMapper.resolve(KeyEvent.KEYCODE_2, true, PhysicalKeyboardLayout.JIS))
        assertEquals("^", PhysicalKeyboardSymbolMapper.resolve(KeyEvent.KEYCODE_6, true, PhysicalKeyboardLayout.US))
        assertEquals("&", PhysicalKeyboardSymbolMapper.resolve(KeyEvent.KEYCODE_6, true, PhysicalKeyboardLayout.JAPANESE_109A))
        assertEquals("&", PhysicalKeyboardSymbolMapper.resolve(KeyEvent.KEYCODE_6, true, PhysicalKeyboardLayout.JIS))
        assertEquals("'", PhysicalKeyboardSymbolMapper.resolve(KeyEvent.KEYCODE_7, true, PhysicalKeyboardLayout.JAPANESE_109A))
        assertEquals("(", PhysicalKeyboardSymbolMapper.resolve(KeyEvent.KEYCODE_8, true, PhysicalKeyboardLayout.JAPANESE_109A))
        assertEquals(")", PhysicalKeyboardSymbolMapper.resolve(KeyEvent.KEYCODE_9, true, PhysicalKeyboardLayout.JAPANESE_109A))
    }

    @Test
    fun resolve_japaneseDedicatedSymbolKeys() {
        assertEquals("¥", PhysicalKeyboardSymbolMapper.resolve(KeyEvent.KEYCODE_YEN, false, PhysicalKeyboardLayout.JAPANESE_109A))
        assertEquals("|", PhysicalKeyboardSymbolMapper.resolve(KeyEvent.KEYCODE_YEN, true, PhysicalKeyboardLayout.JAPANESE_109A))
        assertEquals("\\", PhysicalKeyboardSymbolMapper.resolve(KeyEvent.KEYCODE_RO, false, PhysicalKeyboardLayout.JAPANESE_109A))
        assertEquals("_", PhysicalKeyboardSymbolMapper.resolve(KeyEvent.KEYCODE_RO, true, PhysicalKeyboardLayout.JAPANESE_109A))
        assertEquals("¥", PhysicalKeyboardSymbolMapper.resolve(KeyEvent.KEYCODE_YEN, false, PhysicalKeyboardLayout.JIS))
        assertEquals("_", PhysicalKeyboardSymbolMapper.resolve(KeyEvent.KEYCODE_RO, true, PhysicalKeyboardLayout.JIS))
    }

    @Test
    fun resolve_layoutSpecificUnshiftedPunctuation() {
        assertEquals(":", PhysicalKeyboardSymbolMapper.resolve(KeyEvent.KEYCODE_APOSTROPHE, false, PhysicalKeyboardLayout.JAPANESE_109A))
        assertEquals("'", PhysicalKeyboardSymbolMapper.resolve(KeyEvent.KEYCODE_APOSTROPHE, false, PhysicalKeyboardLayout.US))
        assertEquals("@", PhysicalKeyboardSymbolMapper.resolve(KeyEvent.KEYCODE_AT, false, PhysicalKeyboardLayout.JAPANESE_109A))
        assertEquals("`", PhysicalKeyboardSymbolMapper.resolve(KeyEvent.KEYCODE_AT, true, PhysicalKeyboardLayout.JAPANESE_109A))
    }

    @Test
    fun resolve_returnsNullWhenNotShiftOrUnsupported() {
        assertNull(PhysicalKeyboardSymbolMapper.resolve(KeyEvent.KEYCODE_2, false, PhysicalKeyboardLayout.US))
        assertNull(PhysicalKeyboardSymbolMapper.resolve(KeyEvent.KEYCODE_A, true, PhysicalKeyboardLayout.US))
        assertNull(PhysicalKeyboardSymbolMapper.resolve(KeyEvent.KEYCODE_DPAD_LEFT, true, PhysicalKeyboardLayout.US))
        assertNull(PhysicalKeyboardSymbolMapper.resolve(KeyEvent.KEYCODE_DPAD_RIGHT, true, PhysicalKeyboardLayout.US))
    }
}
