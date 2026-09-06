package com.kazumaproject.markdownhelperkeyboard.ng_word

import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWord
import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWordMatchMode

object NgWordMatcher {
    fun matches(
        input: String,
        candidate: String,
        ngWord: NgWord,
    ): Boolean = when (ngWord.matchMode) {
        NgWordMatchMode.PARTIAL -> candidate.contains(ngWord.tango)
        NgWordMatchMode.EXACT -> input == ngWord.yomi && candidate == ngWord.tango
    }

    fun matchesAny(
        input: String,
        candidate: String,
        ngWords: List<NgWord>,
    ): Boolean = ngWords.any { matches(input, candidate, it) }
}
