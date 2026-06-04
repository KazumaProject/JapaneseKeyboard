package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ZenzContextPolicyTest {

    @Test
    fun resolveZenzContext_returnsEmptyRightContextWhenPreferenceDisabled() {
        val context = resolveZenzContext(
            leftContext = "左かな".dropLast(2),
            rawRightContext = "右側",
            enableRightContext = false
        )

        assertEquals("左", context.leftContext)
        assertEquals("", context.rightContext)
    }

    @Test
    fun resolveZenzContext_keepsRightContextSeparateWhenPreferenceEnabled() {
        val context = resolveZenzContext(
            leftContext = "左かな".dropLast(2),
            rawRightContext = "右側",
            enableRightContext = true
        )

        assertEquals("左", context.leftContext)
        assertEquals("右側", context.rightContext)
    }

    @Test
    fun resolveZenzContext_doesNotUseRightContextAsLeftContextFallback() {
        val context = resolveZenzContext(
            leftContext = "かな".dropLast(2),
            rawRightContext = "右側",
            enableRightContext = true
        )

        assertEquals("", context.leftContext)
        assertEquals("右側", context.rightContext)
    }

    @Test
    fun buildZenzRerankCacheKey_includesRightContext() {
        val targets = listOf(
            IndexedValue(
                index = 0,
                value = Candidate(
                    string = "候補",
                    type = 1.toByte(),
                    length = 2.toUByte(),
                    score = -100
                )
            )
        )

        val key = buildZenzRerankCacheKey(
            profile = "profile",
            leftContext = "left",
            rightContext = "right-a",
            input = "input",
            rerankTargets = targets
        )
        val otherRightContextKey = buildZenzRerankCacheKey(
            profile = "profile",
            leftContext = "left",
            rightContext = "right-b",
            input = "input",
            rerankTargets = targets
        )

        assertEquals("profile\u0001left\u0001right-a\u0001input\u00020\u0003候補\u0003-100", key)
        assertNotEquals(key, otherRightContextKey)
    }
}
