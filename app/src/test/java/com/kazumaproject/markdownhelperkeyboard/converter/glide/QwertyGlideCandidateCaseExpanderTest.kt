package com.kazumaproject.markdownhelperkeyboard.converter.glide

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QwertyGlideCandidateCaseExpanderTest {
    private val expander = QwertyGlideCandidateCaseExpander()

    @Test
    fun lowercaseCandidateAddsCapitalizedAndUppercaseVariants() {
        val result = expander.expand(listOf(candidate("hello", 1000)), limit = 10)

        assertEquals(
            listOf("hello" to 1000, "Hello" to 2500, "HELLO" to 4000),
            result.map { it.string to it.score }
        )
        assertEquals("Hello".length.toUByte(), result.first { it.string == "Hello" }.length)
        assertEquals("HELLO".length.toUByte(), result.first { it.string == "HELLO" }.length)
    }

    @Test
    fun returnsCandidatesSortedByAscendingScore() {
        val result = expander.expand(
            listOf(
                candidate("world", 2000),
                candidate("hello", 1000)
            ),
            limit = 10
        )

        assertEquals(result.map { it.score }.sorted(), result.map { it.score })
    }

    @Test
    fun appliesLimitAfterExpansion() {
        val result = expander.expand(
            listOf(
                candidate("hello", 1000),
                candidate("world", 2000)
            ),
            limit = 4
        )

        assertEquals(listOf("hello", "world", "Hello", "World"), result.map { it.string })
    }

    @Test
    fun duplicateStringsKeepLowestScore() {
        val result = expander.expand(
            listOf(
                candidate("hello", 3000),
                candidate("hello", 1000)
            ),
            limit = 10
        )

        assertEquals(1, result.count { it.string == "hello" })
        assertEquals(1000, result.first { it.string == "hello" }.score)
        assertEquals(2500, result.first { it.string == "Hello" }.score)
        assertEquals(4000, result.first { it.string == "HELLO" }.score)
    }

    @Test
    fun emptyCandidateListReturnsEmptyList() {
        assertTrue(expander.expand(emptyList(), limit = 10).isEmpty())
    }

    @Test
    fun emptyStringCandidateDoesNotAddCaseVariants() {
        val result = expander.expand(listOf(candidate("", 1000)), limit = 10)

        assertEquals(listOf("" to 1000), result.map { it.string to it.score })
    }

    @Test
    fun existingCapitalizedAndUppercaseStringsAreNotDuplicated() {
        val result = expander.expand(
            listOf(
                candidate("hello", 1000),
                candidate("Hello", 1200),
                candidate("HELLO", 1300)
            ),
            limit = 10
        )

        assertEquals(1, result.count { it.string == "Hello" })
        assertEquals(1, result.count { it.string == "HELLO" })
        assertEquals(1200, result.first { it.string == "Hello" }.score)
        assertEquals(1300, result.first { it.string == "HELLO" }.score)
    }

    private fun candidate(
        string: String,
        score: Int
    ): Candidate {
        return Candidate(
            string = string,
            type = 36.toByte(),
            length = string.length.toUByte(),
            score = score,
            yomi = "source",
            leftId = 1,
            rightId = 2
        )
    }
}
