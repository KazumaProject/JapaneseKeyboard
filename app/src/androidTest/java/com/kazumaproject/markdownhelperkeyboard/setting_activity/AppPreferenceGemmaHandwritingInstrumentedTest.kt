package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.gemma.handwriting.GemmaHandwritingLanguage
import com.kazumaproject.markdownhelperkeyboard.gemma.handwriting.GemmaHandwritingSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class AppPreferenceGemmaHandwritingInstrumentedTest {
    @Test
    fun gemmaScreenContainsHandwritingCategoryAndSettingsPersist() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val delayWasPresent = preferences.contains(
            AppPreference.GEMMA_HANDWRITING_AUTO_RECOGNITION_DELAY_KEY
        )
        val languageWasPresent = preferences.contains(
            AppPreference.GEMMA_HANDWRITING_RECOGNITION_LANGUAGE_KEY
        )
        val instructionWasPresent = preferences.contains(
            AppPreference.GEMMA_HANDWRITING_ADDITIONAL_INSTRUCTION_KEY
        )
        val penSizeWasPresent = preferences.contains(AppPreference.GEMMA_HANDWRITING_PEN_SIZE_KEY)
        val penColorWasPresent = preferences.contains(AppPreference.GEMMA_HANDWRITING_PEN_COLOR_KEY)
        val originalDelay = preferences.getInt(
            AppPreference.GEMMA_HANDWRITING_AUTO_RECOGNITION_DELAY_KEY,
            GemmaHandwritingSettings.DEFAULT_AUTO_RECOGNITION_DELAY_MS.toInt(),
        )
        val originalLanguage = preferences.getString(
            AppPreference.GEMMA_HANDWRITING_RECOGNITION_LANGUAGE_KEY,
            null,
        )
        val originalInstruction = preferences.getString(
            AppPreference.GEMMA_HANDWRITING_ADDITIONAL_INSTRUCTION_KEY,
            null,
        )
        val originalPenSize = preferences.getInt(
            AppPreference.GEMMA_HANDWRITING_PEN_SIZE_KEY,
            GemmaHandwritingSettings.DEFAULT_PEN_SIZE_DP,
        )
        val originalPenColor = preferences.getInt(
            AppPreference.GEMMA_HANDWRITING_PEN_COLOR_KEY,
            GemmaHandwritingSettings.AUTOMATIC_PEN_COLOR,
        )

        try {
            val keys = mutableSetOf<String>()
            var handwritingCategoryFound = false
            context.resources.getXml(R.xml.pref_gemma).use { parser ->
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG) {
                        parser.getAttributeValue(ANDROID_NAMESPACE, "key")?.let(keys::add)
                        if (
                            parser.name == "PreferenceCategory" &&
                            parser.getAttributeResourceValue(
                                ANDROID_NAMESPACE,
                                "title",
                                0,
                            ) == R.string.category_gemma_handwriting_title
                        ) {
                            handwritingCategoryFound = true
                        }
                    }
                    parser.next()
                }
            }
            assertTrue(handwritingCategoryFound)
            assertTrue(keys.contains(AppPreference.GEMMA_HANDWRITING_AUTO_RECOGNITION_DELAY_KEY))
            assertTrue(
                keys.contains(AppPreference.GEMMA_HANDWRITING_RECOGNITION_LANGUAGE_KEY)
            )
            assertTrue(
                keys.contains(AppPreference.GEMMA_HANDWRITING_ADDITIONAL_INSTRUCTION_KEY)
            )
            assertTrue(keys.contains(AppPreference.GEMMA_HANDWRITING_RESET_PROMPT_KEY))
            assertTrue(keys.contains(AppPreference.GEMMA_HANDWRITING_PEN_SIZE_KEY))
            assertTrue(keys.contains(AppPreference.GEMMA_HANDWRITING_PEN_COLOR_KEY))

            val additionalInstruction = "人名として読み取ってください。"
            val customColor = 0xFF1E88E5.toInt()
            AppPreference.init(context)
            AppPreference.gemma_handwriting_auto_recognition_delay_preference = 1_600
            AppPreference.gemma_handwriting_recognition_language_preference = "ja"
            AppPreference.gemma_handwriting_additional_instruction_preference =
                additionalInstruction
            AppPreference.gemma_handwriting_pen_size_preference = 11
            AppPreference.gemma_handwriting_pen_color_preference = customColor
            AppPreference.init(context)

            assertEquals(
                1_600,
                AppPreference.gemma_handwriting_auto_recognition_delay_preference,
            )
            assertEquals("ja", AppPreference.gemma_handwriting_recognition_language_preference)
            assertEquals(
                additionalInstruction,
                AppPreference.gemma_handwriting_additional_instruction_preference,
            )
            assertEquals(11, AppPreference.gemma_handwriting_pen_size_preference)
            assertEquals(customColor, AppPreference.gemma_handwriting_pen_color_preference)
            assertEquals(
                GemmaHandwritingSettings(
                    autoRecognitionDelayMs = 1_600,
                    recognitionLanguage = GemmaHandwritingLanguage.JAPANESE,
                    additionalInstruction = additionalInstruction,
                    penSizeDp = 11,
                    penColorArgb = customColor,
                ),
                GemmaHandwritingSettings.normalized(
                    autoRecognitionDelayMs =
                        AppPreference.gemma_handwriting_auto_recognition_delay_preference,
                    recognitionLanguage =
                        AppPreference.gemma_handwriting_recognition_language_preference,
                    additionalInstruction =
                        AppPreference.gemma_handwriting_additional_instruction_preference,
                    penSizeDp = AppPreference.gemma_handwriting_pen_size_preference,
                    penColorArgb = AppPreference.gemma_handwriting_pen_color_preference,
                ),
            )
        } finally {
            preferences.edit().apply {
                if (delayWasPresent) {
                    putInt(
                        AppPreference.GEMMA_HANDWRITING_AUTO_RECOGNITION_DELAY_KEY,
                        originalDelay,
                    )
                } else {
                    remove(AppPreference.GEMMA_HANDWRITING_AUTO_RECOGNITION_DELAY_KEY)
                }
                if (languageWasPresent) {
                    putString(
                        AppPreference.GEMMA_HANDWRITING_RECOGNITION_LANGUAGE_KEY,
                        originalLanguage,
                    )
                } else {
                    remove(AppPreference.GEMMA_HANDWRITING_RECOGNITION_LANGUAGE_KEY)
                }
                if (instructionWasPresent) {
                    putString(
                        AppPreference.GEMMA_HANDWRITING_ADDITIONAL_INSTRUCTION_KEY,
                        originalInstruction,
                    )
                } else {
                    remove(AppPreference.GEMMA_HANDWRITING_ADDITIONAL_INSTRUCTION_KEY)
                }
                if (penSizeWasPresent) {
                    putInt(AppPreference.GEMMA_HANDWRITING_PEN_SIZE_KEY, originalPenSize)
                } else {
                    remove(AppPreference.GEMMA_HANDWRITING_PEN_SIZE_KEY)
                }
                if (penColorWasPresent) {
                    putInt(AppPreference.GEMMA_HANDWRITING_PEN_COLOR_KEY, originalPenColor)
                } else {
                    remove(AppPreference.GEMMA_HANDWRITING_PEN_COLOR_KEY)
                }
            }.commit()
        }
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
