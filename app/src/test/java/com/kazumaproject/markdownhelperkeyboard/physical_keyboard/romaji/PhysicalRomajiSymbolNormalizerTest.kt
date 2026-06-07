package com.kazumaproject.markdownhelperkeyboard.physical_keyboard.romaji

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class PhysicalRomajiSymbolNormalizerTest {

    @Test
    fun normalize_convertsCommaPeriodAndMinusWhenShiftIsNotPressed() {
        assertEquals(
            '、'.code,
            PhysicalRomajiSymbolNormalizer.normalize(
                keyCode = KeyEvent.KEYCODE_COMMA,
                resolvedUnicode = ','.code,
                isShiftPressed = false
            )
        )

        assertEquals(
            '。'.code,
            PhysicalRomajiSymbolNormalizer.normalize(
                keyCode = KeyEvent.KEYCODE_PERIOD,
                resolvedUnicode = '.'.code,
                isShiftPressed = false
            )
        )

        assertEquals(
            'ー'.code,
            PhysicalRomajiSymbolNormalizer.normalize(
                keyCode = KeyEvent.KEYCODE_MINUS,
                resolvedUnicode = '-'.code,
                isShiftPressed = false
            )
        )
    }

    @Test
    fun normalize_keepsResolvedUnicodeWhenShiftIsPressed() {
        assertEquals(
            '<'.code,
            PhysicalRomajiSymbolNormalizer.normalize(
                keyCode = KeyEvent.KEYCODE_COMMA,
                resolvedUnicode = '<'.code,
                isShiftPressed = true
            )
        )

        assertEquals(
            '>'.code,
            PhysicalRomajiSymbolNormalizer.normalize(
                keyCode = KeyEvent.KEYCODE_PERIOD,
                resolvedUnicode = '>'.code,
                isShiftPressed = true
            )
        )

        assertEquals(
            '='.code,
            PhysicalRomajiSymbolNormalizer.normalize(
                keyCode = KeyEvent.KEYCODE_MINUS,
                resolvedUnicode = '='.code,
                isShiftPressed = true
            )
        )
    }

    @Test
    fun normalize_keepsUnsupportedKeysAsResolvedUnicode() {
        assertEquals(
            'a'.code,
            PhysicalRomajiSymbolNormalizer.normalize(
                keyCode = KeyEvent.KEYCODE_A,
                resolvedUnicode = 'a'.code,
                isShiftPressed = false
            )
        )
    }

    @Test
    fun normalize_returnsZeroWhenResolvedUnicodeIsZero() {
        assertEquals(
            0,
            PhysicalRomajiSymbolNormalizer.normalize(
                keyCode = KeyEvent.KEYCODE_COMMA,
                resolvedUnicode = 0,
                isShiftPressed = false
            )
        )
    }
}
