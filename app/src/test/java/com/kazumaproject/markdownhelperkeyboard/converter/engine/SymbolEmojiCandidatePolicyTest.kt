package com.kazumaproject.markdownhelperkeyboard.converter.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolEmojiCandidatePolicyTest {

    @Test
    fun exactSymbolEmojiReadingSurvivesConversionMode() {
        val conversionConfig = PredictionConfig(
            japanesePredictionEnabled = false,
            symbolEmojiEnabled = false,
        )

        assertTrue(
            shouldIncludeSymbolEmojiReading(
                inputLength = 3,
                readingLength = 3,
                predictionConfig = conversionConfig,
            )
        )
        assertFalse(
            shouldIncludeSymbolEmojiReading(
                inputLength = 3,
                readingLength = 4,
                predictionConfig = conversionConfig,
            )
        )
    }

    @Test
    fun symbolEmojiSettingOnlyControlsCompletionReadings() {
        val disabledConfig = PredictionConfig(symbolEmojiEnabled = false)

        assertTrue(
            shouldIncludeSymbolEmojiReading(
                inputLength = 3,
                readingLength = 3,
                predictionConfig = disabledConfig,
            )
        )
        assertFalse(
            shouldIncludeSymbolEmojiReading(
                inputLength = 3,
                readingLength = 4,
                predictionConfig = disabledConfig,
            )
        )
    }

    @Test
    fun japaneseNumberReadingIncludesValueBasedSymbols() {
        assertTrue(
            createJapaneseNumberValueBasedCandidates("いち")
                .any { it.string == "①" }
        )
    }
}
