package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.graph.*
import org.junit.Assert.*
import org.junit.Test

class FlickCorrectionRankingTest {
    private fun corrected(text: String, score: Int, supported: Boolean = false, trailing: Boolean = false) =
        Candidate(text, CANDIDATE_TYPE_FLICK_TYPO_CORRECTION, 4u, score, yomi = "こんにちは",
            flickCorrection = FlickCorrectionInfo(listOf(FlickCorrectionEdit(
                if (trailing) FlickCorrectionKind.MISSING else FlickCorrectionKind.DIRECTION,
                if (trailing) 4 else 1, if (trailing) 4 else 2, "に", 1000, supported))))

    @Test fun weakEvidenceCannotDisplaceLiteralButStrongEvidenceCan() {
        val literal = Candidate("入力どおり", 1, 4u, 4000)
        assertEquals(literal, FlickCorrectionRanking.merge(listOf(literal), listOf(corrected("補正", 1000)), 4).first())
        val unknown = literal.copy(score = 20000)
        assertEquals(unknown, FlickCorrectionRanking.merge(listOf(unknown), listOf(corrected("補正", 1000)), 4).first())
        assertEquals(literal, FlickCorrectionRanking.merge(listOf(literal), listOf(corrected("補正", 1000, true)), 4).first())
        assertEquals("補正", FlickCorrectionRanking.merge(listOf(unknown), listOf(corrected("補正", 1000, true)), 4).first().string)
    }

    @Test fun duplicateOutputPrefersLiteralAndTrailingInsertionIsNotPromoted() {
        val literal = Candidate("入力どおり", 1, 4u, 20000)
        assertEquals(listOf(literal), FlickCorrectionRanking.merge(listOf(literal), listOf(corrected(literal.string, 1000)), 4))
        assertEquals(literal, FlickCorrectionRanking.merge(listOf(literal), listOf(corrected("補正", 1000, trailing = true)), 4).first())
    }

    @Test fun explicitlyRegisteredOrLearnedChoicesRemainFirst() {
        for (type in listOf(CANDIDATE_TYPE_USER_DICTIONARY, CANDIDATE_TYPE_LEARNED_DICTIONARY)) {
            val literal = Candidate("選んだ言葉", type, 4u, 20000)
            assertEquals(literal, FlickCorrectionRanking.merge(listOf(literal), listOf(corrected("補正", 1000, true)), 4).first())
        }
    }

    @Test fun boostingKeepsTheOriginalOrderBetweenCorrections() {
        val literals = (0..3).map { Candidate("通常$it", 1, 4u, 2000 + it * 1000) }
        val first = corrected("頻度の高い補正", 5100)
        val second = corrected("頻度の低い補正", 6000)
        val ranked = FlickCorrectionRanking.merge(literals, listOf(second, first), 8)
        assertTrue(ranked.indexOfFirst { it.string == first.string } < ranked.indexOfFirst { it.string == second.string })
    }

    @Test fun normalPredictionAndModifierOmissionWinDuplicateCommittedRanges() {
        val correction = corrected("こんにちは", 1000, trailing = true)
        val prediction = Candidate("こんにちは", 9, 5u, 8000, yomi = "こんにちは")
        val partial = correction.copy(length = 3u)
        assertEquals(listOf(prediction), FlickCorrectionRanking.preferLiteralDuplicates(listOf(correction, prediction), 4))
        assertEquals(listOf(partial, prediction), FlickCorrectionRanking.preferLiteralDuplicates(listOf(partial, prediction), 4))
    }

    @Test fun tiedLiteralSpellingsKeepTheFirstTwoAndExposeOnePlausibleCorrection() {
        val literals = (0..3).map { Candidate("表記$it", 1, 4u, 5000, yomi = "こんちは") }
        val correction = corrected("こんにちは", 9000)
        val merged = FlickCorrectionRanking.merge(literals, listOf(correction), 8)
        val ranked = FlickCorrectionRanking.preferLiteralDuplicates(merged, 4)
        assertEquals(literals.take(2), ranked.take(2))
        assertEquals("こんにちは", ranked[2].string)
        assertEquals(9000, ranked[2].flickCorrection!!.scoreBeforeRanking)
        assertTrue(ranked.contains(literals[2]))
    }

    @Test fun diversityDoesNotBoostWeakTrailingOrPartialCorrectionsOrHideDistinctLiteralReadings() {
        val literals = (0..3).map { Candidate("表記$it", 1, 4u, 5000, yomi = "こんちは") }
        for (correction in listOf(corrected("弱い補正", 16000),
            corrected("末尾追加", 6000, trailing = true), corrected("部分補正", 6000).copy(length = 3u))) {
            val merged = FlickCorrectionRanking.merge(literals, listOf(correction), 8)
            assertEquals(literals.take(3), FlickCorrectionRanking.preferLiteralDuplicates(merged, 4).take(3))
        }
        val diverse = literals.mapIndexed { i,c -> c.copy(yomi = "読み$i") }
        val merged = FlickCorrectionRanking.merge(diverse, listOf(corrected("補正", 9000)), 8)
        assertEquals(diverse.take(3), FlickCorrectionRanking.preferLiteralDuplicates(merged, 4).take(3))
    }
}
