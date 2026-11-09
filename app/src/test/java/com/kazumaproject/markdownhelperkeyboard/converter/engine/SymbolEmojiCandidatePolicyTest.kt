package com.kazumaproject.markdownhelperkeyboard.converter.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolEmojiCandidatePolicyTest {

    @Test
    fun exactSymbolEmojiReadingSurvivesConversionMode() {
        val conversionConfig = PredictionConfig(
            japanesePredictionEnabled = false,
            symbolEmojiEnabled = false,
        )

        assertEquals(3, maxSymbolEmojiReadingLength(3, conversionConfig))
    }

    @Test
    fun symbolEmojiSettingOnlyControlsCompletionReadings() {
        val disabledConfig = PredictionConfig(symbolEmojiEnabled = false)
        val enabledConfig = PredictionConfig(symbolEmojiEnabled = true, lookaheadCharacterCount = 4)

        assertEquals(3, maxSymbolEmojiReadingLength(3, disabledConfig))
        assertEquals(7, maxSymbolEmojiReadingLength(3, enabledConfig))
        assertEquals(2, maxSymbolEmojiReadingLength(2, enabledConfig))
    }

    @Test
    fun japaneseNumberReadingIncludesValueBasedSymbols() {
        assertTrue(
            createJapaneseNumberValueBasedCandidates("いち")
                .any { it.string == "①" }
        )
    }
}
