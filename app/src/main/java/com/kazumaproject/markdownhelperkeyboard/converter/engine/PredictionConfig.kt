package com.kazumaproject.markdownhelperkeyboard.converter.engine

enum class PredictionAggressiveness(val preferenceValue: String) {
    CONSERVATIVE("conservative"),
    STANDARD("standard"),
    AGGRESSIVE("aggressive");

    companion object {
        fun fromPreference(value: String?): PredictionAggressiveness =
            entries.firstOrNull { it.preferenceValue == value } ?: STANDARD
    }
}

/** Runtime settings for dictionary-based completion candidates. */
data class PredictionConfig(
    val japanesePredictionEnabled: Boolean = true,
    val englishPredictionEnabled: Boolean = true,
    val systemDictionaryEnabled: Boolean = true,
    val minimumInputLength: Int = 3,
    val systemCandidateLimit: Int = 4,
    val lookaheadCharacterCount: Int = DEFAULT_LOOKAHEAD_CHARACTER_COUNT,
    val aggressiveness: PredictionAggressiveness = PredictionAggressiveness.STANDARD,
    val systemUserDictionaryEnabled: Boolean = true,
    val readingCorrectionEnabled: Boolean = true,
    val proverbEnabled: Boolean = true,
    val externalMozcEnabled: Boolean = true,
    val symbolEmojiEnabled: Boolean = true,
) {
    val normalizedMinimumInputLength: Int
        get() = minimumInputLength.coerceIn(MIN_INPUT_LENGTH, MAX_INPUT_LENGTH)

    val normalizedSystemCandidateLimit: Int
        get() = systemCandidateLimit.coerceIn(MIN_CANDIDATE_LIMIT, MAX_CANDIDATE_LIMIT)

    val normalizedLookaheadCharacterCount: Int
        get() = lookaheadCharacterCount.coerceIn(
            MIN_LOOKAHEAD_CHARACTER_COUNT,
            MAX_LOOKAHEAD_CHARACTER_COUNT,
        )

    fun acceptsJapaneseReading(inputLength: Int, readingLength: Int): Boolean {
        if (!japanesePredictionEnabled) return false
        if (inputLength !in normalizedMinimumInputLength..MAX_PREDICTION_INPUT_LENGTH) return false
        if (readingLength <= inputLength) return false
        return readingLength <= inputLength + normalizedLookaheadCharacterCount
    }

    fun completionPenalty(
        inputLength: Int,
        readingLength: Int,
        penaltyPerCharacter: Int = 8_000,
        longInputFlatPenalty: Int? = 6_000,
    ): Int {
        val extraLength = (readingLength - inputLength).coerceAtLeast(0)
        if (extraLength == 0) return 0
        val standardPenalty = if (inputLength <= 5 || longInputFlatPenalty == null) {
            penaltyPerCharacter * extraLength
        } else {
            longInputFlatPenalty
        }
        return when (aggressiveness) {
            PredictionAggressiveness.CONSERVATIVE -> standardPenalty * 3 / 2
            PredictionAggressiveness.STANDARD -> standardPenalty
            PredictionAggressiveness.AGGRESSIVE -> standardPenalty / 2
        }
    }

    companion object {
        const val MIN_INPUT_LENGTH = 3
        const val MAX_INPUT_LENGTH = 8
        const val MAX_PREDICTION_INPUT_LENGTH = 16
        const val MIN_CANDIDATE_LIMIT = 1
        const val MAX_CANDIDATE_LIMIT = 16
        const val MIN_LOOKAHEAD_CHARACTER_COUNT = 1
        const val MAX_LOOKAHEAD_CHARACTER_COUNT = 6
        const val DEFAULT_LOOKAHEAD_CHARACTER_COUNT = 3
    }
}
