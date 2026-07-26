package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertEquals
import org.junit.Test

class ZenzRequestTimingPolicyTest {
    @Test
    fun zeroRemainsImmediate() {
        assertEquals(0L, resolveZenzDebounceMillis(0))
    }

    @Test
    fun configuredDelayIsPreserved() {
        assertEquals(300L, resolveZenzDebounceMillis(300))
    }

    @Test
    fun missingValueUsesDefault() {
        assertEquals(DEFAULT_ZENZ_DEBOUNCE_MILLIS, resolveZenzDebounceMillis(null))
    }

    @Test
    fun negativeValueCannotDelayIndefinitely() {
        assertEquals(0L, resolveZenzDebounceMillis(-1))
    }
}
