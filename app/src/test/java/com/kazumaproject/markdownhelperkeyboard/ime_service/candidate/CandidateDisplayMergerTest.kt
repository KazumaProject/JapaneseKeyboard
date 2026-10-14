package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_LEARNED_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_TEXT_MACRO
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_USER_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateDisplayMergerTest {
    private val kampo = DictionaryCategory.CLASSIFIED_KAMPO
    private val medical = DictionaryCategory.CLASSIFIED_MEDICAL
    private val dictionary = Candidate("阿仙薬", 9, 5u, 9_066, yomi = "あせんやく",
        leftId = 1851, rightId = 1851, dictionaryCategories = setOf(kampo))

    @Test fun learnedWinnerKeepsItsScoreTypeReadingAndCommitWhileInheritingKampoLabel() {
        val learned = dictionary.copy(type = CANDIDATE_TYPE_LEARNED_DICTIONARY, score = -1_000,
            leftId = 1, rightId = 2, sourceId = 123, dictionaryCategories = emptySet())
        assertEquals(listOf(learned.copy(dictionaryCategories = setOf(kampo))),
            CandidateDisplayMerger.merge(listOf(learned, dictionary)))
    }

    @Test fun userWinnerKeepsPositionAndUnionsAllDuplicateLabels() {
        val user = dictionary.copy(type = CANDIDATE_TYPE_USER_DICTIONARY, score = 123,
            yomi = null, dictionaryCategories = emptySet())
        val other = Candidate("アセンヤク", 4, 5u, 6_000)
        assertEquals(listOf(user.copy(dictionaryCategories = setOf(kampo, medical)), other),
            CandidateDisplayMerger.merge(listOf(user, other, dictionary,
                dictionary.copy(score = 8_000, dictionaryCategories = setOf(medical)), dictionary)))
    }

    @Test fun dictionaryWinnerKeepsMetadataWhenLaterLearnedCandidateHasNoLabels() {
        assertEquals(listOf(dictionary), CandidateDisplayMerger.merge(listOf(dictionary,
            dictionary.copy(type = CANDIDATE_TYPE_LEARNED_DICTIONARY, dictionaryCategories = emptySet()))))
    }

    @Test fun macroActionsRemainSeparateAndNeverInheritConversionLabels() {
        val macro = dictionary.copy(type = CANDIDATE_TYPE_TEXT_MACRO, sourceId = 1,
            dictionaryCategories = emptySet())
        val secondMacro = macro.copy(sourceId = 2)
        assertEquals(listOf(macro, dictionary, secondMacro), CandidateDisplayMerger.merge(
            listOf(macro, dictionary, secondMacro, macro.copy(dictionaryCategories = setOf(medical)))))
    }

    @Test fun sameDisplayWithDifferentCommitTextDoesNotInheritLabels() {
        val first = dictionary.copy(commitText = "別の確定文字列", dictionaryCategories = emptySet())
        assertEquals(listOf(first), CandidateDisplayMerger.merge(listOf(first, dictionary)))
    }

    @Test fun allNonCategoryFieldsAndOrderMatchLegacyDeduplication() {
        val candidates = (0 until 200).map { index ->
            Candidate(string = "候補${index % 17}",
                type = if (index % 7 == 0) CANDIDATE_TYPE_TEXT_MACRO else CANDIDATE_TYPE_LEARNED_DICTIONARY,
                length = (index % 16).toUByte(), score = index - 100, yomi = "よみ$index",
                sourceId = (index % 4).toLong(),
                dictionaryCategories = if (index % 3 == 0) setOf(kampo) else setOf(medical))
        }
        val expected = candidates.distinctBy {
            if (it.type == CANDIDATE_TYPE_TEXT_MACRO) "text-macro:${it.sourceId}" else "text:${it.string}"
        }.map { it.copy(dictionaryCategories = emptySet()) }
        assertEquals(expected, CandidateDisplayMerger.merge(candidates)
            .map { it.copy(dictionaryCategories = emptySet()) })
    }
}
