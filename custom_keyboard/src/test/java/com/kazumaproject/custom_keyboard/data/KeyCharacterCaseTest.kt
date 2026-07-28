package com.kazumaproject.custom_keyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class KeyCharacterCaseTest {
    @Test
    fun uppercase_transformsOnlyLowercaseAsciiLetters() {
        assertEquals(
            "ABC XYZ-123 あİß",
            KeyCharacterCase.UPPERCASE.transformAsciiLetters("aBc xyz-123 あİß")
        )
    }

    @Test
    fun asDefined_returnsCanonicalInstance() {
        val canonical = "qWERTY あいう"

        assertSame(canonical, KeyCharacterCase.AS_DEFINED.transformAsciiLetters(canonical))
    }

    @Test
    fun uppercase_withoutLowercaseAscii_returnsCanonicalInstance() {
        val canonical = "ABC 123 あいう"

        assertSame(canonical, KeyCharacterCase.UPPERCASE.transformAsciiLetters(canonical))
    }
}
