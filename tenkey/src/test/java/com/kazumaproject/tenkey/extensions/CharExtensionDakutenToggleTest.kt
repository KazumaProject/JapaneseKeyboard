package com.kazumaproject.tenkey.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CharExtensionDakutenToggleTest {

    @Test
    fun toggleDakutenOnlyRoundTripsSeionAndDakuten() {
        assertEquals('が', 'か'.toggleDakutenWithSeion())
        assertEquals('か', 'が'.toggleDakutenWithSeion())
        assertEquals('ば', 'は'.toggleDakutenWithSeion())
        assertEquals('は', 'ば'.toggleDakutenWithSeion())
    }

    @Test
    fun toggleDakutenOnlyReturnsHandakutenToSeion() {
        assertEquals('は', 'ぱ'.toggleDakutenWithSeion())
    }

    @Test
    fun toggleHandakutenOnlyRoundTripsSeionAndHandakuten() {
        assertEquals('ぱ', 'は'.toggleHandakutenWithSeion())
        assertEquals('は', 'ぱ'.toggleHandakutenWithSeion())
    }

    @Test
    fun toggleHandakutenOnlyReturnsDakutenToSeion() {
        assertEquals('は', 'ば'.toggleHandakutenWithSeion())
    }

    @Test
    fun unsupportedCharactersReturnNull() {
        assertNull('あ'.toggleDakutenWithSeion())
        assertNull('あ'.toggleHandakutenWithSeion())
        assertNull('A'.toggleDakutenWithSeion())
        assertNull('A'.toggleHandakutenWithSeion())
    }
}
