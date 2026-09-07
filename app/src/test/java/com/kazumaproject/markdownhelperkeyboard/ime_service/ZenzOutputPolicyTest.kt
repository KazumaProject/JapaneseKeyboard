package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.ime_service.models.CandidateEvaluationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenzOutputPolicyTest {
    @Test
    fun acceptsNormalAndLongConversionTextWithoutInputLengthHeuristic() {
        assertEquals("末", ZenzOutputPolicy.acceptedTextOrNull("末"))

        val longValidText = "これは意味のある長い変換結果です。".repeat(8)
        assertEquals(longValidText, ZenzOutputPolicy.acceptedTextOrNull(longValidText))
    }

    @Test
    fun rejectsEveryReservedProtocolMarker() {
        for (codePoint in 0xEE00..0xEE0F) {
            val marker = String(Character.toChars(codePoint))
            assertNull(
                "U+${codePoint.toString(16).uppercase()} must not reach candidates",
                ZenzOutputPolicy.acceptedTextOrNull("末${marker}まつ"),
            )
        }
    }

    @Test
    fun rejectsEmptyControlAndReplacementText() {
        assertNull(ZenzOutputPolicy.acceptedTextOrNull(null))
        assertNull(ZenzOutputPolicy.acceptedTextOrNull(""))
        assertNull(ZenzOutputPolicy.acceptedTextOrNull("末\n"))
        assertNull(ZenzOutputPolicy.acceptedTextOrNull("末\uFFFD"))
        assertNull(ZenzOutputPolicy.acceptedTextOrNull("末\u0001"))
    }

    @Test
    fun candidateEvaluationParserRejectsUnsafePayloadsAndNonFiniteScores() {
        assertTrue(CandidateEvaluationResult.parse("PASS:-1.25") is CandidateEvaluationResult.Pass)
        assertTrue(CandidateEvaluationResult.parse("PASS:NaN") is CandidateEvaluationResult.Error)
        assertTrue(CandidateEvaluationResult.parse("FIX:末\uEE08まつ") is CandidateEvaluationResult.Error)
        assertTrue(CandidateEvaluationResult.parse("WHOLE:\u0001") is CandidateEvaluationResult.Error)
    }
}
