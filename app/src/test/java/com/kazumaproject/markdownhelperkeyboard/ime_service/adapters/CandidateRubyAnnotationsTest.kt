package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.*
import org.junit.Test

class CandidateRubyAnnotationsTest {
    @Test fun wordsAndOkuriganaAlignUsingConversionPath() {
        assertEquals(listOf(CandidateRubyAnnotation(0, 2, "がっこう"), CandidateRubyAnnotation(3, 4, "い")),
            resolveCandidateRubyAnnotations("学校に行く", "がっこうにいく", listOf(
                CandidateConversionSegment(0, 4, "学校"),
                CandidateConversionSegment(4, 5, "に"),
                CandidateConversionSegment(5, 7, "行く"),
            )))
    }

    @Test fun aSingleDictionaryEntryCanContainMultipleKanjiRuns() {
        assertEquals(listOf(CandidateRubyAnnotation(0, 2, "がっこう"), CandidateRubyAnnotation(3, 4, "い")),
            resolve("学校に行く", "がっこうにいく"))
        assertEquals(listOf(CandidateRubyAnnotation(0, 1, "た")), resolve("食べる", "たべる"))
        assertEquals(listOf(CandidateRubyAnnotation(1, 2, "いわ")), resolve("お祝い", "おいわい"))
    }

    @Test fun compoundsStayTogetherAndKanaHaveNoRuby() {
        assertEquals(listOf(CandidateRubyAnnotation(0, 3, "とうきょうと")), resolve("東京都", "とうきょうと"))
        assertEquals(listOf(CandidateRubyAnnotation(0, 2, "ひとびと")), resolve("人々", "ひとびと"))
        assertTrue(resolve("かな", "かな")!!.isEmpty())
        assertTrue(resolve("カナ", "かな")!!.isEmpty())
        assertEquals(listOf(CandidateRubyAnnotation(2, 3, "ご")), resolve("カナ語", "かなご"))
    }

    @Test fun repeatedKanaWithAmbiguousAlignmentAnnotatesTheEntireSegment() {
        assertEquals(listOf(CandidateRubyAnnotation(0, 3, "ああああ")), resolve("明あ日", "ああああ"))
        assertEquals(listOf(CandidateRubyAnnotation(0, 2, "いく")), resolve("行け", "いく"))
    }

    @Test fun absentOrInvalidCorrespondenceFallsBackToWholeReading() {
        assertNull(resolveCandidateRubyAnnotations("学校", "がっこう", emptyList()))
        for (segments in listOf(
            listOf(CandidateConversionSegment(-1, 4, "学校")),
            listOf(CandidateConversionSegment(1, 4, "学校")),
            listOf(CandidateConversionSegment(0, 5, "学校")),
            listOf(CandidateConversionSegment(0, 3, "学校")),
            listOf(CandidateConversionSegment(0, 4, "学")),
            listOf(CandidateConversionSegment(0, 4, "別字")),
            listOf(CandidateConversionSegment(0, 0, "学校")),
            listOf(CandidateConversionSegment(0, 4, "")),
        )) assertNull(resolveCandidateRubyAnnotations("学校", "がっこう", segments))
    }

    @Test fun supplementaryCharactersUseUtf16RangesWithoutSplittingSurrogates() {
        assertEquals(listOf(CandidateRubyAnnotation(0, 2, "よし")), resolve("𠮷", "よし"))
        assertNull(resolveCandidateRubyAnnotations("学校", "😀", listOf(
            CandidateConversionSegment(0, 1, "学"), CandidateConversionSegment(1, 2, "校"),
        )))
        assertNull(resolveCandidateRubyAnnotations("𠮷", "よし", listOf(
            CandidateConversionSegment(0, 1, "\uD842"), CandidateConversionSegment(1, 2, "\uDFB7"),
        )))
    }

    @Test fun rubyPresentationKeepsExistingEligibilityAndFallbacks() {
        val candidate = Candidate("学校に行く", 1, 7u, 0, "がっこうにいく",
            conversionSegments = listOf(CandidateConversionSegment(0, 7, "学校に行く")))
        val ruby = resolveCandidateYomiPresentation(true, true, candidate, 14f, AppPreference.CANDIDATE_YOMI_MODE_RUBY)
        assertTrue(ruby.isVisible)
        assertEquals("がっこう い", ruby.text)
        assertEquals(2, ruby.annotations!!.size)
        for ((enabled, first) in listOf(false to true, true to false)) {
            val hidden = resolveCandidateYomiPresentation(enabled, first, candidate, 14f, "ruby")
            assertFalse(hidden.isVisible)
            assertNull(hidden.annotations)
        }
        val original = resolveCandidateYomiPresentation(true, true, candidate, 14f)
        assertEquals("がっこうにいく", original.text)
        assertNull(original.annotations)
        val missing = resolveCandidateYomiPresentation(true, true, candidate.copy(conversionSegments = emptyList()), 14f, "ruby")
        assertEquals(original, missing)
        val kana = resolveCandidateYomiPresentation(true, true,
            Candidate("カナ", 1, 2u, 0, "かな", conversionSegments = listOf(CandidateConversionSegment(0, 2, "カナ"))),
            14f, "ruby")
        assertFalse(kana.isVisible)
    }

    private fun resolve(output: String, reading: String) = resolveCandidateRubyAnnotations(
        output, reading, listOf(CandidateConversionSegment(0, reading.length, output)))
}
