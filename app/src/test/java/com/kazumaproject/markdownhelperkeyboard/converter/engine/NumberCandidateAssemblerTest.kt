package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_USER_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.NumericCandidateRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberCandidateAssemblerTest {
    @Test fun everyRepresentativePrecedesEveryVariantAndSupplement() {
        val ranked = listOf(
            Candidate("荷縁", 1, 3, 1, conversionSegments = listOf(CandidateConversionSegment(0, 3, "荷縁"))),
            Candidate("二円", 1, 3, 2, conversionSegments = listOf(CandidateConversionSegment(0, 3, "二円"))),
            Candidate("別解", 1, 3, 3),
        )

        val result = NumberCandidateAssembler.assemble("にえん", ranked, PredictionConfig())

        assertEquals(listOf("荷縁", "2円", "別解"), result.take(3).map { it.string })
        val firstDerived = result.indexOfFirst { it.numericRole == NumericCandidateRole.VARIANT }
        assertTrue(firstDerived >= 3)
        assertTrue(result.take(firstDerived).none { it.numericRole == NumericCandidateRole.VARIANT })
        assertEquals(listOf(1, 2, 3), result.take(3).map { it.score })
    }

    @Test fun settingChangesNotationOrderButNotInterpretationOrder() {
        val ranked = listOf(
            Candidate("二円", 1, 3, 10, conversionSegments = listOf(CandidateConversionSegment(0, 3, "二円"))),
            Candidate("荷縁", 1, 3, 20, conversionSegments = listOf(CandidateConversionSegment(0, 3, "荷縁"))),
        )
        val config = PredictionConfig(numberCandidateOrder = NumberCandidateOrder.KANJI_FULL_HALF)

        val result = NumberCandidateAssembler.assemble("にえん", ranked, config)

        assertEquals(listOf("二円", "荷縁"), result.take(2).map { it.string })
        assertEquals(listOf("２円", "2円"), result.drop(2).take(2).map { it.string })
    }

    @Test fun multipleSpansDoNotCreateACartesianProduct() {
        val candidate = Candidate(
            "二円三人", 1, 7, 1,
            conversionSegments = listOf(
                CandidateConversionSegment(0, 3, "二円"),
                CandidateConversionSegment(3, 7, "三人"),
            ),
        )

        val result = NumberCandidateAssembler.assemble("にえんさんにん", listOf(candidate), PredictionConfig())

        assertEquals(listOf("2円3人", "２円３人", "二円三人"), result.take(3).map { it.string })
        assertEquals(3, result.count { it.derivedFromInterpretationId == result.first().interpretationId })
    }

    @Test fun userDictionaryCandidateIsNeverConvertedFromItsDisplayText() {
        val user = Candidate(
            "二円", CANDIDATE_TYPE_USER_DICTIONARY, 3, 1,
            conversionSegments = listOf(CandidateConversionSegment(0, 3, "二円")),
        )

        val result = NumberCandidateAssembler.assemble("にえん", listOf(user), PredictionConfig())

        assertEquals("二円", result.first().string)
        assertEquals(NumericCandidateRole.NONE, result.first().numericRole)
        assertTrue(result.none { it.derivedFromInterpretationId == result.first().interpretationId })
    }

    @Test fun parserOnlyEmbeddedInterpretationIsAppendedAsASupplement() {
        val ordinaryPath = Candidate(
            "荷縁分", 1, 5, 1,
            conversionSegments = listOf(
                CandidateConversionSegment(0, 3, "荷縁"),
                CandidateConversionSegment(3, 5, "分"),
            ),
        )

        val result = NumberCandidateAssembler.assemble("にえんぶん", listOf(ordinaryPath), PredictionConfig())

        assertEquals("荷縁分", result.first().string)
        assertTrue(result.any { it.string == "2円分" &&
            it.numericRole == NumericCandidateRole.SUPPLEMENT && !it.rankingEligible })
    }

    @Test fun overflowDigitsKeepNormalizedSpellingAndIntLength() {
        val input = "000000000000000000009223372036854775808"
        val proof = requireNotNull(ValidatedNumber.parseDigits(input))
        assertEquals(null, proof.value)
        assertEquals(input, proof.digits)

        val result = NumberCandidateAssembler.assemble(
            input,
            listOf(Candidate(input, 18, input.length, 1)),
            PredictionConfig(),
        )

        assertEquals(input.length, result.first().length)
        assertEquals(input, result.first().string)
        assertTrue(result.any { it.string.startsWith("０００") })
    }
}
