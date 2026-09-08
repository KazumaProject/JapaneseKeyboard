package com.kazumaproject.markdownhelperkeyboard.ng_word

import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWord
import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWordMatchMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NgWordMatcherTest {
    @Test
    fun partialMatchBlocksCandidateContainingTheWord() {
        val ngWord = NgWord(yomi = "きょう", tango = "今日")

        assertTrue(NgWordMatcher.matches("きょうは", "今日は晴れ", ngWord))
        assertFalse(NgWordMatcher.matches("きょうは", "明日は晴れ", ngWord))
    }

    @Test
    fun exactMatchRequiresBothInputAndCandidateToMatch() {
        val ngWord = NgWord(
            yomi = "きょう",
            tango = "今日",
            matchMode = NgWordMatchMode.EXACT,
        )

        assertTrue(NgWordMatcher.matches("きょう", "今日", ngWord))
        assertFalse(NgWordMatcher.matches("きょうは", "今日", ngWord))
        assertFalse(NgWordMatcher.matches("きょう", "今日は", ngWord))
        assertFalse(NgWordMatcher.matches("あした", "今日", ngWord))
    }

    @Test
    fun partialMatchTreatsRegexCharactersAsPlainText() {
        val ngWord = NgWord(yomi = "記号", tango = "a.b")

        assertTrue(NgWordMatcher.matches("記号", "xa.by", ngWord))
        assertFalse(NgWordMatcher.matches("記号", "acb", ngWord))
    }

    @Test
    fun matchesAnyBlocksWhenOneNgWordMatches() {
        val ngWords = listOf(
            NgWord(yomi = "きょう", tango = "今日"),
            NgWord(yomi = "あした", tango = "明日", matchMode = NgWordMatchMode.EXACT),
        )

        assertTrue(NgWordMatcher.matchesAny("あした", "明日", ngWords))
        assertFalse(NgWordMatcher.matchesAny("あしたは", "明日", ngWords))
    }

    @Test
    fun invalidStoredModeFallsBackToPartial() {
        assertEquals(NgWordMatchMode.PARTIAL, NgWordMatchMode.fromStorage(null))
        assertEquals(NgWordMatchMode.PARTIAL, NgWordMatchMode.fromStorage("UNKNOWN"))
        assertEquals(NgWordMatchMode.EXACT, NgWordMatchMode.fromStorage("EXACT"))
    }
}
