package com.kazumaproject.markdownhelperkeyboard.learning.database

import org.junit.Assert.assertEquals
import org.junit.Test

class LearningScorePolicyTest {
    @Test
    fun initialRankBonusIsCappedAndHandlesExtremeIndices() {
        assertEquals(10_000, LearningScorePolicy.initial(candidateScore = 10_000, candidateIndex = 0))
        assertEquals(9500, LearningScorePolicy.initial(candidateScore = 10_000, candidateIndex = 1))
        assertEquals(7000, LearningScorePolicy.initial(candidateScore = 10_000, candidateIndex = Int.MAX_VALUE))
        assertEquals(10_000, LearningScorePolicy.initial(candidateScore = 10_000, candidateIndex = -1))
        assertEquals(Int.MAX_VALUE, LearningScorePolicy.initial(candidateScore = Int.MAX_VALUE, candidateIndex = 0))
    }

    @Test
    fun reinforcementReducesTheExistingScoreByAtMostFiveHundredWithoutOverflow() {
        assertEquals(2500, LearningScorePolicy.reinforce(existingScore = 3000, incomingScore = 3000))
        assertEquals(0, LearningScorePolicy.reinforce(existingScore = 400, incomingScore = 400))
        assertEquals(
            Int.MAX_VALUE - 500,
            LearningScorePolicy.reinforce(existingScore = Int.MAX_VALUE, incomingScore = Int.MAX_VALUE),
        )
    }
}
