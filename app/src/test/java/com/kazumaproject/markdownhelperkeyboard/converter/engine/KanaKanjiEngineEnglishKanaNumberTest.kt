package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class KanaKanjiEngineEnglishKanaNumberTest {

    private lateinit var engine: KanaKanjiEngine

    @Before
    fun setUp() {
        engine = KanaKanjiEngine()

        val englishEngine = mock<EnglishEngine>()
        whenever(englishEngine.getCandidates(org.mockito.kotlin.any(), org.mockito.kotlin.any())).doAnswer { invocation ->
            val input = invocation.arguments[0] as String
            listOf(
                Candidate(
                    string = input,
                    type = 29,
                    length = input.length.toUByte(),
                    score = 100
                )
            )
        }

        KanaKanjiEngine::class.java.getDeclaredField("englishEngine").apply {
            isAccessible = true
            set(engine, englishEngine)
        }
    }

    @Test
    fun getCandidatesEnglishKana_returns_numeric_variants_for_hiragana_numbers() {
        val candidateStrings =
            engine.getCandidatesEnglishKana("いちまんにせんさんびゃくよんじゅうご").map { it.string }

        assertTrue(candidateStrings.contains("12345"))
        assertTrue(candidateStrings.contains("12,345"))
        assertTrue(candidateStrings.contains("一万二千三百四十五"))
        assertTrue(candidateStrings.contains("1万2345"))
    }

    @Test
    fun getCandidatesEnglishKana_returns_number_unit_candidates_for_hiragana_inputs() {
        assertTrue(engine.getCandidatesEnglishKana("さんにん").any { it.string == "3人" })
        assertTrue(engine.getCandidatesEnglishKana("ごえん").any { it.string == "5円" })
        assertTrue(engine.getCandidatesEnglishKana("にじゅっぷん").any { it.string == "20分" })
        assertTrue(engine.getCandidatesEnglishKana("にじゅっふん").any { it.string == "20分" })
        assertTrue(engine.getCandidatesEnglishKana("ろくじ").any { it.string == "6時" })
        assertTrue(engine.getCandidatesEnglishKana("にじゅうよじ").any { it.string == "24時" })
    }

    @Test
    fun getCandidatesEnglishKana_preserves_existing_non_numeric_behaviour() {
        assertTrue(engine.getCandidatesEnglishKana("1234").any { it.string == "1234" })
        assertTrue(engine.getCandidatesEnglishKana("1234").any { it.string == "1,234" })
        assertTrue(engine.getCandidatesEnglishKana("１２３４").any { it.string == "１２３４" })
        assertTrue(engine.getCandidatesEnglishKana("１２３４").any { it.string == "1234" })
        assertTrue(engine.getCandidatesEnglishKana("abc").any { it.string == "abc" })
        assertTrue(engine.getCandidatesEnglishKana("ａｂｃ").any { it.string == "abc" })
        assertTrue(engine.getCandidatesEnglishKana("きょう").any { it.string == "きょう" })
        assertTrue(engine.getCandidatesEnglishKana("2025/04/01").any { it.string == "2025/04/01" })
    }
}
