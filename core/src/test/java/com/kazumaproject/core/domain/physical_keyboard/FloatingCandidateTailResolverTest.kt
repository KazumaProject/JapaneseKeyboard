package com.kazumaproject.core.domain.physical_keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingCandidateTailResolverTest {
    @Test
    fun resolveTail_returnsOnlyCurrentTail() {
        assertEquals("では", FloatingCandidateTailResolver.resolveTail("ここでは", "ここ".length))
        assertEquals("", FloatingCandidateTailResolver.resolveTail("ここでは", "ここでは".length))
        assertEquals("", FloatingCandidateTailResolver.resolveTail("ここでは", 100))
        assertEquals("", FloatingCandidateTailResolver.resolveTail("ここでは", -1))
    }
}
