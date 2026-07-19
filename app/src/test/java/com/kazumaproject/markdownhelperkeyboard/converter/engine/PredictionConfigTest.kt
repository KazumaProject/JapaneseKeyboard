package com.kazumaproject.markdownhelperkeyboard.converter.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictionConfigTest {

    @Test
    fun defaultLookaheadAcceptsExactlyThreeAdditionalCharacters() {
        val config = PredictionConfig()

        assertFalse(config.acceptsJapaneseReading(inputLength = 2, readingLength = 3))
        assertTrue(config.acceptsJapaneseReading(inputLength = 3, readingLength = 6))
        assertFalse(config.acceptsJapaneseReading(inputLength = 3, readingLength = 7))
        assertTrue(config.acceptsJapaneseReading(inputLength = 5, readingLength = 8))
        assertFalse(config.acceptsJapaneseReading(inputLength = 5, readingLength = 9))
        assertTrue(config.acceptsJapaneseReading(inputLength = 7, readingLength = 10))
        assertFalse(config.acceptsJapaneseReading(inputLength = 7, readingLength = 11))
        assertFalse(config.acceptsJapaneseReading(inputLength = 17, readingLength = 18))
    }

    @Test
    fun lookaheadCharacterCountControlsMaximumCompletionLength() {
        val oneCharacter = PredictionConfig(lookaheadCharacterCount = 1)
        val sixCharacters = PredictionConfig(lookaheadCharacterCount = 6)

        assertTrue(oneCharacter.acceptsJapaneseReading(inputLength = 4, readingLength = 5))
        assertFalse(oneCharacter.acceptsJapaneseReading(inputLength = 4, readingLength = 6))
        assertTrue(sixCharacters.acceptsJapaneseReading(inputLength = 4, readingLength = 10))
        assertFalse(sixCharacters.acceptsJapaneseReading(inputLength = 4, readingLength = 11))
    }

    @Test
    fun disabledJapanesePredictionRejectsCompletions() {
        val config = PredictionConfig(japanesePredictionEnabled = false)

        assertFalse(config.acceptsJapaneseReading(inputLength = 3, readingLength = 4))
    }

    @Test
    fun limitsAreNormalizedAndAggressivenessChangesPenalty() {
        val belowRange = PredictionConfig(
            minimumInputLength = 0,
            systemCandidateLimit = 0,
            lookaheadCharacterCount = 0,
        )
        val aboveRange = PredictionConfig(
            minimumInputLength = 99,
            systemCandidateLimit = 99,
            lookaheadCharacterCount = 99,
        )

        assertEquals(3, belowRange.normalizedMinimumInputLength)
        assertEquals(1, belowRange.normalizedSystemCandidateLimit)
        assertEquals(1, belowRange.normalizedLookaheadCharacterCount)
        assertEquals(8, aboveRange.normalizedMinimumInputLength)
        assertEquals(16, aboveRange.normalizedSystemCandidateLimit)
        assertEquals(6, aboveRange.normalizedLookaheadCharacterCount)

        val conservative = PredictionConfig(
            aggressiveness = PredictionAggressiveness.CONSERVATIVE,
        ).completionPenalty(inputLength = 4, readingLength = 6)
        val standard = PredictionConfig().completionPenalty(inputLength = 4, readingLength = 6)
        val aggressive = PredictionConfig(
            aggressiveness = PredictionAggressiveness.AGGRESSIVE,
        ).completionPenalty(inputLength = 4, readingLength = 6)

        assertTrue(conservative > standard)
        assertTrue(standard > aggressive)
    }

    @Test
    fun standardAggressivenessKeepsEachDictionarysExistingPenaltyScale() {
        val config = PredictionConfig()

        assertEquals(6_000, config.completionPenalty(inputLength = 6, readingLength = 9))
        assertEquals(
            24_000,
            config.completionPenalty(
                inputLength = 6,
                readingLength = 9,
                penaltyPerCharacter = 8_000,
                longInputFlatPenalty = null,
            ),
        )
        assertEquals(
            4_500,
            config.completionPenalty(
                inputLength = 6,
                readingLength = 9,
                penaltyPerCharacter = 1_500,
                longInputFlatPenalty = null,
            ),
        )
    }

    @Test
    fun unknownStoredAggressivenessFallsBackToStandard() {
        assertEquals(
            PredictionAggressiveness.STANDARD,
            PredictionAggressiveness.fromPreference("unknown"),
        )
    }
}
