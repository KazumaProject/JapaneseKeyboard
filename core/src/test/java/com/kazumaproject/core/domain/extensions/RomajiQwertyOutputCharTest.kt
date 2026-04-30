package com.kazumaproject.core.domain.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomajiQwertyOutputCharTest {
    @Test
    fun toRomajiQwertyOutputChar_defaultsToZenkaku() {
        assertEquals('１', '1'.toRomajiQwertyOutputChar(false, false))
        assertEquals('！', '!'.toRomajiQwertyOutputChar(false, false))
    }

    @Test
    fun toRomajiQwertyOutputChar_keepsOnlyNumbersHalfWidth() {
        assertEquals('1', '1'.toRomajiQwertyOutputChar(true, false))
        assertEquals('！', '!'.toRomajiQwertyOutputChar(true, false))
    }

    @Test
    fun toRomajiQwertyOutputChar_keepsOnlySymbolsHalfWidth() {
        assertEquals('１', '1'.toRomajiQwertyOutputChar(false, true))
        assertEquals('!', '!'.toRomajiQwertyOutputChar(false, true))
    }

    @Test
    fun toRomajiQwertyOutputChar_keepsNumbersAndSymbolsHalfWidth() {
        assertEquals('1', '1'.toRomajiQwertyOutputChar(true, true))
        assertEquals('!', '!'.toRomajiQwertyOutputChar(true, true))
    }

    @Test
    fun isAsciiSymbolForRomajiQwerty_excludesLettersNumbersAndSpace() {
        assertTrue('!'.isAsciiSymbolForRomajiQwerty())
        assertTrue('/'.isAsciiSymbolForRomajiQwerty())
        assertTrue(':'.isAsciiSymbolForRomajiQwerty())
        assertTrue('@'.isAsciiSymbolForRomajiQwerty())
        assertTrue('['.isAsciiSymbolForRomajiQwerty())
        assertTrue('`'.isAsciiSymbolForRomajiQwerty())
        assertTrue('{'.isAsciiSymbolForRomajiQwerty())
        assertTrue('~'.isAsciiSymbolForRomajiQwerty())
        assertFalse('a'.isAsciiSymbolForRomajiQwerty())
        assertFalse('Z'.isAsciiSymbolForRomajiQwerty())
        assertFalse('1'.isAsciiSymbolForRomajiQwerty())
        assertFalse(' '.isAsciiSymbolForRomajiQwerty())
    }
}
