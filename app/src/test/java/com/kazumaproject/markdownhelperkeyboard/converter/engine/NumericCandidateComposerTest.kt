package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import org.junit.Assert.*
import org.junit.Test

class NumericCandidateComposerTest {
    @Test fun readingsPreserveLexicalOrderRegardlessOfNumericScore() {
        for (input in listOf("にほん", "ひゃく", "いちじかんはん")) {
            val numeric = NumericCandidateProvider.generate(input)
            assertTrue(numeric.isNotEmpty())
            val lexical = Candidate("通常候補", 1, input.length.toUByte(), 9000)
            val result = NumericCandidateComposer.compose(input, numeric + lexical, numeric)
            assertSame(lexical, result.first())
            assertEquals(numeric.map { it.string }, result.drop(1).map { it.string })
        }
    }

    @Test fun identicalDictionaryEntryIsNotDemotedOrReplaced() {
        val numeric = NumericCandidateProvider.generate("ひゃく")
        val dictionary = numeric.first().copy()
        val second = Candidate("百という語", 1, 3u, 8000)
        val result = NumericCandidateComposer.compose("ひゃく", numeric + listOf(dictionary, second), numeric)
        assertSame(dictionary, result.first())
        assertSame(second, result[1])
        assertEquals(1, result.count { it.string == dictionary.string })
    }

    @Test fun explicitDigitsRespectNotationButKeepLearnedSelection() {
        val numeric = NumericCandidateProvider.generate("100", NumericNotationPreference.FULL_WIDTH_FIRST)
        val lexical = Candidate("100", 1, 3u, 9000)
        assertEquals("１００", NumericCandidateComposer.compose("100", listOf(lexical) + numeric, numeric).first().string)
        val learned = Candidate("百", 34, 3u, 0)
        val result = NumericCandidateComposer.compose("100", listOf(learned, lexical) + numeric, numeric)
        assertSame(learned, result.first())
        assertEquals(1, result.count { it.string == "百" })
    }
}
