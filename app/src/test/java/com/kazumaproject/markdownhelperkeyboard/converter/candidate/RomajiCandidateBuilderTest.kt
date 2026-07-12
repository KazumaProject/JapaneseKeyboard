package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isAllFullWidthAscii
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isAllHalfWidthAscii
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RomajiCandidateBuilderTest {

    @Test
    fun type30RomajiCandidatesAreFullWidthAndType31CandidatesAreHalfWidth() {
        val candidates = buildRomajiCandidates(
            readingLength = 4,
            romaji = "gakkou"
        )

        assertEquals(
            listOf("ｇａｋｋｏｕ", "gakkou", "Ｇａｋｋｏｕ", "Gakkou", "ＧＡＫＫＯＵ", "GAKKOU"),
            candidates.map { it.string }
        )
        assertEquals(listOf(30, 31, 30, 31, 30, 31), candidates.map { it.type.toInt() })
        assertTrue(candidates.filter { it.type == 30.toByte() }.all { it.string.isAllFullWidthAscii() })
        assertTrue(candidates.filter { it.type == 31.toByte() }.all { it.string.isAllHalfWidthAscii() })
        assertTrue(candidates.all { it.length.toInt() == 4 })
    }
}
