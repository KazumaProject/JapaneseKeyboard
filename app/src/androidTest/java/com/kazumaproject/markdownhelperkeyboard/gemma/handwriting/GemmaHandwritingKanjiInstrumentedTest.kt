package com.kazumaproject.markdownhelperkeyboard.gemma.handwriting

import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaTranslationManager
import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaMediaType
import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaRuntimeClient
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GemmaHandwritingKanjiInstrumentedTest {
    @Test(timeout = 360_000)
    fun recognizesConsecutiveJapaneseKanjiInOneImage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        AppPreference.init(context)
        val originalPath = AppPreference.gemma_translation_model_path_preference
        val originalEnabled = AppPreference.enable_gemma_translation_preference
        val originalLanguage = preferences.getString(
            AppPreference.GEMMA_HANDWRITING_RECOGNITION_LANGUAGE_KEY,
            null,
        )
        val languageWasPresent = preferences.contains(
            AppPreference.GEMMA_HANDWRITING_RECOGNITION_LANGUAGE_KEY
        )
        val originalInstruction = preferences.getString(
            AppPreference.GEMMA_HANDWRITING_ADDITIONAL_INSTRUCTION_KEY,
            null,
        )
        val instructionWasPresent = preferences.contains(
            AppPreference.GEMMA_HANDWRITING_ADDITIONAL_INSTRUCTION_KEY
        )
        val manager = GemmaTranslationManager(context, AppPreference, GemmaRuntimeClient(context))
        val imageFile = File(context.cacheDir, "gemma_handwriting_test_kanji_nihon.png")
        try {
            val model = manager.installedModels()
                .firstOrNull { installed -> installed.descriptor.supports(GemmaMediaType.IMAGE) }
            assumeTrue("Install an image-capable .litertlm model before this test", model != null)
            AppPreference.gemma_translation_model_path_preference =
                requireNotNull(model).file.absolutePath
            AppPreference.enable_gemma_translation_preference = true

            val additionalInstruction = """
                画像には日本語の漢字が2文字、左から右へ続けて手書きされています。
                2文字とも省略せず、文字の形と順序を正確に読み取ってください。
            """.trimIndent()
            AppPreference.gemma_handwriting_recognition_language_preference = "ja"
            AppPreference.gemma_handwriting_additional_instruction_preference =
                additionalInstruction
            AppPreference.init(context)
            val settings = GemmaHandwritingSettings.normalized(
                autoRecognitionDelayMs =
                    AppPreference.gemma_handwriting_auto_recognition_delay_preference,
                recognitionLanguage =
                    AppPreference.gemma_handwriting_recognition_language_preference,
                additionalInstruction =
                    AppPreference.gemma_handwriting_additional_instruction_preference,
            )
            assertEquals(GemmaHandwritingLanguage.JAPANESE, settings.recognitionLanguage)
            assertTrue(settings.recognitionPrompt.contains(additionalInstruction))

            HandwritingBitmapExporter.writePng(
                strokes = listOf(
                    // 日
                    stroke(0.10f, 0.20f, 0.10f, 0.80f),
                    stroke(0.10f, 0.20f, 0.40f, 0.20f, 0.40f, 0.80f),
                    stroke(0.10f, 0.50f, 0.40f, 0.50f),
                    stroke(0.10f, 0.80f, 0.40f, 0.80f),
                    // 本
                    stroke(0.56f, 0.30f, 0.92f, 0.30f),
                    stroke(0.74f, 0.12f, 0.74f, 0.85f),
                    stroke(0.73f, 0.40f, 0.54f, 0.72f),
                    stroke(0.75f, 0.40f, 0.94f, 0.72f),
                    stroke(0.62f, 0.66f, 0.86f, 0.66f),
                ),
                target = imageFile,
            )

            assertTrue(
                "Gemma image runtime did not initialize",
                manager.initializeIfEnabled(forceReload = true),
            )
            val raw = manager.runMediaPrompt(
                prompt = settings.recognitionPrompt,
                mediaPath = imageFile.absolutePath,
                mediaType = GemmaMediaType.IMAGE,
            )
            val candidates = GemmaHandwritingPrompt.parseCandidates(raw)
            println("Consecutive Japanese handwriting candidates: $candidates")

            assertTrue(
                "Expected the consecutive Japanese kanji 日本, raw result: $raw",
                candidates.contains("日本"),
            )
        } finally {
            manager.disable()
            imageFile.delete()
            AppPreference.gemma_translation_model_path_preference = originalPath
            AppPreference.enable_gemma_translation_preference = originalEnabled
            preferences.edit().apply {
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
            }.commit()
        }
    }

    @Test(timeout = 360_000)
    fun recognizesJapaneseKanjiUsingPersistedHandwritingPrompt() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        AppPreference.init(context)
        val originalPath = AppPreference.gemma_translation_model_path_preference
        val originalEnabled = AppPreference.enable_gemma_translation_preference
        val originalLanguage = preferences.getString(
            AppPreference.GEMMA_HANDWRITING_RECOGNITION_LANGUAGE_KEY,
            null,
        )
        val languageWasPresent = preferences.contains(
            AppPreference.GEMMA_HANDWRITING_RECOGNITION_LANGUAGE_KEY
        )
        val originalInstruction = preferences.getString(
            AppPreference.GEMMA_HANDWRITING_ADDITIONAL_INSTRUCTION_KEY,
            null,
        )
        val instructionWasPresent = preferences.contains(
            AppPreference.GEMMA_HANDWRITING_ADDITIONAL_INSTRUCTION_KEY
        )
        val manager = GemmaTranslationManager(context, AppPreference, GemmaRuntimeClient(context))
        val imageFile = File(context.cacheDir, "gemma_handwriting_test_kanji_yama.png")
        try {
            val model = manager.installedModels()
                .firstOrNull { installed -> installed.descriptor.supports(GemmaMediaType.IMAGE) }
            assumeTrue("Install an image-capable .litertlm model before this test", model != null)
            AppPreference.gemma_translation_model_path_preference =
                requireNotNull(model).file.absolutePath
            AppPreference.enable_gemma_translation_preference = true

            val additionalInstruction = """
                画像には日本語の漢字がちょうど1文字だけ手書きされています。
                文字の形を正確に読み取ってください。
            """.trimIndent()
            AppPreference.gemma_handwriting_recognition_language_preference = "ja"
            AppPreference.gemma_handwriting_additional_instruction_preference =
                additionalInstruction
            AppPreference.init(context)
            val settings = GemmaHandwritingSettings.normalized(
                autoRecognitionDelayMs =
                    AppPreference.gemma_handwriting_auto_recognition_delay_preference,
                recognitionLanguage =
                    AppPreference.gemma_handwriting_recognition_language_preference,
                additionalInstruction =
                    AppPreference.gemma_handwriting_additional_instruction_preference,
            )
            assertEquals(GemmaHandwritingLanguage.JAPANESE, settings.recognitionLanguage)
            assertTrue(settings.recognitionPrompt.contains(additionalInstruction))

            HandwritingBitmapExporter.writePng(
                strokes = listOf(
                    stroke(0.50f, 0.12f, 0.50f, 0.80f),
                    stroke(0.25f, 0.30f, 0.25f, 0.80f, 0.76f, 0.80f),
                    stroke(0.76f, 0.30f, 0.76f, 0.80f),
                ),
                target = imageFile,
            )

            assertTrue(
                "Gemma image runtime did not initialize",
                manager.initializeIfEnabled(forceReload = true),
            )
            val raw = manager.runMediaPrompt(
                prompt = settings.recognitionPrompt,
                mediaPath = imageFile.absolutePath,
                mediaType = GemmaMediaType.IMAGE,
            )
            val candidates = GemmaHandwritingPrompt.parseCandidates(raw)

            assertTrue(
                "Expected the Japanese kanji 山, raw result: $raw",
                candidates.contains("山"),
            )
        } finally {
            manager.disable()
            imageFile.delete()
            AppPreference.gemma_translation_model_path_preference = originalPath
            AppPreference.enable_gemma_translation_preference = originalEnabled
            preferences.edit().apply {
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
            }.commit()
        }
    }

    private fun stroke(vararg coordinates: Float): HandwritingStroke {
        return HandwritingStroke(
            coordinates.toList().chunked(2).map { (x, y) -> HandwritingPoint(x, y) },
        )
    }
}
