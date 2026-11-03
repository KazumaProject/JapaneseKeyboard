package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import org.junit.Assert.*
import org.junit.Test

class CandidateTextTransformTest {
    @Test fun halfWidthVariantPreservesOriginAndReadingWhileUpdatingCommit() {
        val original = Candidate("１８時", CANDIDATE_TYPE_LEARNED_DICTIONARY, 6u, 100,
            yomi = "じゅうはちじ", conversionSegments = listOf(CandidateConversionSegment(0, 6, "１８時")))
        val method = KanaKanjiEngine::class.java.getDeclaredMethod("addHalfWidthCandidates", List::class.java)
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val variants = method.invoke(KanaKanjiEngine(), listOf(original)) as List<Candidate>
        assertEquals(original, variants.first())
        val half = variants.last()
        assertEquals("18時", half.string)
        assertEquals(half.string, half.commitText)
        assertEquals(CANDIDATE_TYPE_HALF_WIDTH, half.type)
        assertEquals(original.dictionaryOrigins, half.dictionaryOrigins)
        assertEquals(listOf(CandidateConversionSegment(0, 6, "18時")), half.conversionSegments)
        assertEquals(original.length, half.length)
        assertEquals(original.yomi, half.yomi)
    }

    @Test fun generatedResultDoesNotKeepDictionaryLabelsOrStaleReadingPath() {
        val original = Candidate("東京", CANDIDATE_TYPE_USER_DICTIONARY, 5u, 100,
            yomi = "とうきょう", sourceId = 12, dictionaryCategories = setOf(DictionaryCategory.CLASSIFIED_PLACE),
            conversionSegments = listOf(CandidateConversionSegment(0, 5, "東京")))
        val translated = original.withGeneratedText("Tokyo", 41)
        assertEquals("Tokyo", translated.string)
        assertEquals("Tokyo", translated.commitText)
        assertEquals(41.toByte(), translated.type)
        assertTrue(translated.dictionaryOrigins.isEmpty())
        assertTrue(translated.dictionaryCategories.isEmpty())
        assertTrue(translated.conversionSegments.isEmpty())
        assertNull(translated.yomi)
        assertFalse(com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.resolveCandidateYomiPresentation(
            showCandidateYomiForLiveConversion = true,
            isFirstCandidate = true,
            suggestion = translated,
            readingTextSize = 12f,
            readingMode = "whole",
        ).isVisible)
        assertNull(translated.sourceId)
    }

    @Test fun numericDateDoesNotUseTheZenzType() {
        val candidates = KanaKanjiEngine().getCandidatesEnglishKana("1231", PredictionConfig())
        val date = candidates.single { it.string == "12月31日" }
        assertEquals(CANDIDATE_TYPE_DIGIT_DATE, date.type)
        assertNotEquals(CANDIDATE_TYPE_ZENZ_REGENERATED, date.type)
        assertEquals("12月31日", date.commitText)
        assertEquals(4.toUByte(), date.length)
        assertTrue(date.dictionaryOrigins.isEmpty())
        assertTrue(candidates.filter { it.string == "千二百三十一" }.all { it.type == CANDIDATE_TYPE_KANJI_NUMBER })
    }
}
