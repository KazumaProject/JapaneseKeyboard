package com.kazumaproject.markdownhelperkeyboard.gemma.handwriting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GemmaHandwritingSettingsTest {
    @Test
    fun normalized_clampsRecognitionDelayToSupportedRange() {
        assertEquals(
            GemmaHandwritingSettings.MIN_AUTO_RECOGNITION_DELAY_MS.toLong(),
            GemmaHandwritingSettings.normalized(
                autoRecognitionDelayMs = -1,
                recognitionLanguage = "auto",
                additionalInstruction = "",
            ).autoRecognitionDelayMs,
        )
        assertEquals(
            GemmaHandwritingSettings.MAX_AUTO_RECOGNITION_DELAY_MS.toLong(),
            GemmaHandwritingSettings.normalized(
                autoRecognitionDelayMs = Int.MAX_VALUE,
                recognitionLanguage = "auto",
                additionalInstruction = "",
            ).autoRecognitionDelayMs,
        )
    }

    @Test
    fun normalized_keepsStandardRulesAroundAdditionalInstruction() {
        val additionalInstruction = "人名として読み取ってください。"

        val settings = GemmaHandwritingSettings.normalized(
            autoRecognitionDelayMs = 1_200,
            recognitionLanguage = "ja",
            additionalInstruction = additionalInstruction,
        )

        assertEquals(GemmaHandwritingLanguage.JAPANESE, settings.recognitionLanguage)
        assertEquals(additionalInstruction, settings.additionalInstruction)
        assertTrue(settings.recognitionPrompt.contains("Use Japanese as the recognition context."))
        assertTrue(settings.recognitionPrompt.contains(additionalInstruction))
        assertTrue(settings.recognitionPrompt.contains("<CANDIDATE>text</CANDIDATE>"))
        assertTrue(
            settings.recognitionPrompt.indexOf(additionalInstruction) <
                settings.recognitionPrompt.indexOf("Output contract:")
        )
    }

    @Test
    fun normalized_usesAutoForUnknownLanguageAndCapsAdditionalInstruction() {
        assertEquals(
            GemmaHandwritingLanguage.AUTO,
            GemmaHandwritingSettings.normalized(
                autoRecognitionDelayMs = 900,
                recognitionLanguage = "unknown",
                additionalInstruction = "",
            ).recognitionLanguage,
        )
        assertEquals(
            GemmaHandwritingSettings.MAX_ADDITIONAL_INSTRUCTION_LENGTH,
            GemmaHandwritingSettings.normalized(
                autoRecognitionDelayMs = 900,
                recognitionLanguage = "en",
                additionalInstruction = "a".repeat(5_000),
            ).additionalInstruction.length,
        )
    }

    @Test
    fun normalized_clampsPenSizeAndMakesCustomColorOpaque() {
        val settings = GemmaHandwritingSettings.normalized(
            autoRecognitionDelayMs = 900,
            recognitionLanguage = "auto",
            additionalInstruction = "",
            penSizeDp = 99,
            penColorArgb = 0x00123456,
        )

        assertEquals(GemmaHandwritingSettings.MAX_PEN_SIZE_DP, settings.penSizeDp)
        assertEquals(0xFF123456.toInt(), settings.penColorArgb)
    }

    @Test
    fun automaticPenColorFollowsLightAndDarkMode() {
        val settings = GemmaHandwritingSettings()

        assertEquals(
            GemmaHandwritingSettings.LIGHT_MODE_AUTOMATIC_PEN_COLOR,
            settings.resolvedPenColor(darkMode = false),
        )
        assertEquals(
            GemmaHandwritingSettings.DARK_MODE_AUTOMATIC_PEN_COLOR,
            settings.resolvedPenColor(darkMode = true),
        )
    }

    @Test
    fun recognitionColorIsAlwaysNormalizedToBlack() {
        val settings = GemmaHandwritingSettings(penColorArgb = 0xFFFF0000.toInt())

        assertEquals(
            GemmaHandwritingSettings.LIGHT_MODE_AUTOMATIC_PEN_COLOR,
            settings.resolvedRecognitionPenColor(),
        )
    }

    @Test
    fun recognitionPenSizeIsIndependentFromDisplayPenSize() {
        val settings = GemmaHandwritingSettings(
            penSizeDp = GemmaHandwritingSettings.MAX_PEN_SIZE_DP,
        )

        assertEquals(
            GemmaHandwritingSettings.DEFAULT_PEN_SIZE_DP,
            settings.resolvedRecognitionPenSizeDp(),
        )
    }
}
