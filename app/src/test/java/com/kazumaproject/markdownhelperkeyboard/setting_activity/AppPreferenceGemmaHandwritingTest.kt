package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.gemma.handwriting.GemmaHandwritingLanguage
import com.kazumaproject.markdownhelperkeyboard.gemma.handwriting.GemmaHandwritingPrompt
import com.kazumaproject.markdownhelperkeyboard.gemma.handwriting.GemmaHandwritingSettings
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPreferenceGemmaHandwritingTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun defaultsMatchTheSettingsScreenAndRuntimePrompt() {
        assertEquals(
            GemmaHandwritingSettings.DEFAULT_AUTO_RECOGNITION_DELAY_MS.toInt(),
            AppPreference.gemma_handwriting_auto_recognition_delay_preference,
        )
        assertEquals(
            GemmaHandwritingLanguage.AUTO.preferenceValue,
            AppPreference.gemma_handwriting_recognition_language_preference,
        )
        assertEquals(
            "",
            AppPreference.gemma_handwriting_additional_instruction_preference,
        )
        assertEquals(GemmaHandwritingPrompt.DEFAULT_TEXT, GemmaHandwritingSettings().recognitionPrompt)
        assertArrayEquals(
            GemmaHandwritingLanguage.entries
                .map(GemmaHandwritingLanguage::preferenceValue)
                .toTypedArray(),
            context.resources.getStringArray(
                R.array.gemma_handwriting_recognition_language_values
            ),
        )
        assertEquals(
            GemmaHandwritingSettings.DEFAULT_PEN_SIZE_DP,
            AppPreference.gemma_handwriting_pen_size_preference,
        )
        assertEquals(
            GemmaHandwritingSettings.AUTOMATIC_PEN_COLOR,
            AppPreference.gemma_handwriting_pen_color_preference,
        )
    }

    @Test
    fun handwritingSettingsPersistInDefaultSharedPreferences() {
        val additionalInstruction = "人名として読み取ってください。"
        val color = 0xFF1E88E5.toInt()

        AppPreference.gemma_handwriting_auto_recognition_delay_preference = 1_700
        AppPreference.gemma_handwriting_recognition_language_preference = "ja"
        AppPreference.gemma_handwriting_additional_instruction_preference =
            additionalInstruction
        AppPreference.gemma_handwriting_pen_size_preference = 12
        AppPreference.gemma_handwriting_pen_color_preference = color
        AppPreference.init(context)

        assertEquals(
            1_700,
            AppPreference.gemma_handwriting_auto_recognition_delay_preference,
        )
        assertEquals("ja", AppPreference.gemma_handwriting_recognition_language_preference)
        assertEquals(
            additionalInstruction,
            AppPreference.gemma_handwriting_additional_instruction_preference,
        )
        assertEquals(12, AppPreference.gemma_handwriting_pen_size_preference)
        assertEquals(color, AppPreference.gemma_handwriting_pen_color_preference)
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        assertEquals(
            1_700,
            preferences.getInt(
                AppPreference.GEMMA_HANDWRITING_AUTO_RECOGNITION_DELAY_KEY,
                -1,
            ),
        )
        assertEquals(
            "ja",
            preferences.getString(
                AppPreference.GEMMA_HANDWRITING_RECOGNITION_LANGUAGE_KEY,
                null,
            ),
        )
        assertEquals(
            additionalInstruction,
            preferences.getString(
                AppPreference.GEMMA_HANDWRITING_ADDITIONAL_INSTRUCTION_KEY,
                null,
            ),
        )
        assertEquals(
            12,
            preferences.getInt(AppPreference.GEMMA_HANDWRITING_PEN_SIZE_KEY, -1),
        )
        assertEquals(
            color,
            preferences.getInt(AppPreference.GEMMA_HANDWRITING_PEN_COLOR_KEY, 0),
        )
    }

    @Test
    fun invalidPersistedValuesAreNormalized() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit()
            .putInt(AppPreference.GEMMA_HANDWRITING_AUTO_RECOGNITION_DELAY_KEY, 60_000)
            .putString(AppPreference.GEMMA_HANDWRITING_PROMPT_KEY, "unsafe full prompt")
            .putString(AppPreference.GEMMA_HANDWRITING_RECOGNITION_LANGUAGE_KEY, "invalid")
            .putString(
                AppPreference.GEMMA_HANDWRITING_ADDITIONAL_INSTRUCTION_KEY,
                "a".repeat(5_000),
            )
            .putInt(AppPreference.GEMMA_HANDWRITING_PEN_SIZE_KEY, 100)
            .putInt(AppPreference.GEMMA_HANDWRITING_PEN_COLOR_KEY, 0x00123456)
            .commit()
        AppPreference.init(context)

        assertEquals(
            GemmaHandwritingSettings.MAX_AUTO_RECOGNITION_DELAY_MS,
            AppPreference.gemma_handwriting_auto_recognition_delay_preference,
        )
        assertEquals(
            GemmaHandwritingLanguage.AUTO.preferenceValue,
            AppPreference.gemma_handwriting_recognition_language_preference,
        )
        assertEquals(
            GemmaHandwritingSettings.MAX_ADDITIONAL_INSTRUCTION_LENGTH,
            AppPreference.gemma_handwriting_additional_instruction_preference.length,
        )
        assertEquals(false, preferences.contains(AppPreference.GEMMA_HANDWRITING_PROMPT_KEY))
        assertEquals(
            GemmaHandwritingSettings.MAX_PEN_SIZE_DP,
            AppPreference.gemma_handwriting_pen_size_preference,
        )
        assertEquals(0xFF123456.toInt(), AppPreference.gemma_handwriting_pen_color_preference)
    }

    @Test
    fun resetPromptRemovesAdditionalInstructionAndLegacyOverride() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit()
            .putString(AppPreference.GEMMA_HANDWRITING_PROMPT_KEY, "unsafe")
            .putString(AppPreference.GEMMA_HANDWRITING_ADDITIONAL_INSTRUCTION_KEY, "hint")
            .commit()

        AppPreference.resetGemmaHandwritingPromptToDefault()

        assertEquals("", AppPreference.gemma_handwriting_additional_instruction_preference)
        assertEquals(false, preferences.contains(AppPreference.GEMMA_HANDWRITING_PROMPT_KEY))
        assertEquals(
            false,
            preferences.contains(AppPreference.GEMMA_HANDWRITING_ADDITIONAL_INSTRUCTION_KEY),
        )
    }
}
