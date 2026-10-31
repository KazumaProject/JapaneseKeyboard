package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaTranslationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateTypePresentationTest {
    @Test fun candidateTypeIdsDoNotOverlapExistingOrReservedIds() {
        val fields = Class.forName("com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateTypesKt")
            .declaredFields.filter { it.name.startsWith("CANDIDATE_TYPE_") || it.name == "QWERTY_GLIDE_CANDIDATE_TYPE" }
        val ids = fields.map { it.getByte(null).toInt() }
        assertEquals(ids.toSet().size, ids.size)
        val gemmaIds = listOf(
            GemmaTranslationManager.TRANSLATED_CANDIDATE_TYPE,
            GemmaTranslationManager.PROMPT_RESULT_CANDIDATE_TYPE,
            GemmaTranslationManager.SELECTION_TRANSLATE_ACTION_CANDIDATE_TYPE,
            GemmaTranslationManager.SELECTION_PROMPT_ACTION_CANDIDATE_TYPE,
        )
        assertEquals(gemmaIds.size, gemmaIds.toSet().size)
        assertTrue(ids.none { it in gemmaIds || it in setOf(6, 8, 10, 24, 25, 26, 27) })
    }

    @Test fun symbolWidthKeepsItsOriginalSourceAndLeavesOtherFormsAlone() {
        val cases = listOf(
            Triple("！", CANDIDATE_TYPE_SYMBOL, CANDIDATE_TYPE_FULL_WIDTH_SYMBOL),
            Triple("!", CANDIDATE_TYPE_SYMBOL, CANDIDATE_TYPE_HALF_WIDTH_SYMBOL),
            Triple("１２３４", CANDIDATE_TYPE_SYMBOL, CANDIDATE_TYPE_FULL_WIDTH_SYMBOL),
            Triple("１２", CANDIDATE_TYPE_NUMBER_SYMBOL, CANDIDATE_TYPE_FULL_WIDTH_NUMBER_SYMBOL),
            Triple("12", CANDIDATE_TYPE_NUMBER_SYMBOL, CANDIDATE_TYPE_HALF_WIDTH_NUMBER_SYMBOL),
            Triple("①", CANDIDATE_TYPE_NUMBER_SYMBOL, CANDIDATE_TYPE_NUMBER_SYMBOL),
            Triple("😀", CANDIDATE_TYPE_SYMBOL, CANDIDATE_TYPE_SYMBOL),
            Triple("!Ａ", CANDIDATE_TYPE_SYMBOL, CANDIDATE_TYPE_SYMBOL),
            Triple("!", CANDIDATE_TYPE_EMOJI, CANDIDATE_TYPE_EMOJI),
        )
        for ((text, originalType, expectedType) in cases) {
            val original = Candidate(text, originalType, 2u, 42)
            val classified = original.withSymbolWidthType()
            assertEquals("$text/$originalType", expectedType, classified.type)
            assertEquals(original.string, classified.string)
            assertEquals(original.commitText, classified.commitText)
            assertEquals(original.length, classified.length)
            assertEquals(original.score, classified.score)
            assertEquals(classified, classified.withSymbolWidthType())
        }
    }
}
