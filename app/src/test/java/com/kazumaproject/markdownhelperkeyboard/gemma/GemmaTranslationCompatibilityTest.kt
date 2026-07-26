package com.kazumaproject.markdownhelperkeyboard.gemma

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaRuntimeClient
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GemmaTranslationCompatibilityTest {

    @Test
    fun candidateTypeIdsKeepLegacyValuesAndDoNotOverlapKanaKanjiTypes() {
        val gemmaTypes = listOf(
            GemmaTranslationManager.TRANSLATED_CANDIDATE_TYPE,
            GemmaTranslationManager.PROMPT_RESULT_CANDIDATE_TYPE,
            GemmaTranslationManager.SELECTION_TRANSLATE_ACTION_CANDIDATE_TYPE,
            GemmaTranslationManager.SELECTION_PROMPT_ACTION_CANDIDATE_TYPE,
        )

        assertEquals(listOf(41, 42, 43, 44), gemmaTypes)
        assertFalse(gemmaTypes.any { it in 24..27 })
    }

    @Test
    fun translationTargetsAcceptEveryPersistedPreferenceValue() {
        val persistedValues = listOf(
            "en", "ja", "ko", "zh-Hans", "zh-Hant", "es", "fr", "de", "it", "pt",
            "ru", "ar", "hi", "id", "th", "vi", "tr", "pl", "nl", "uk",
        )

        assertEquals(
            persistedValues,
            GemmaTranslationManager.TranslationTargetLanguage.entries.map { it.preferenceValue },
        )
        persistedValues.forEach { value ->
            assertEquals(
                value,
                GemmaTranslationManager.TranslationTargetLanguage.fromPreference(value).preferenceValue,
            )
        }
    }

    @Test
    fun unknownTranslationTargetFallsBackToEnglish() {
        assertEquals(
            GemmaTranslationManager.TranslationTargetLanguage.English,
            GemmaTranslationManager.TranslationTargetLanguage.fromPreference("unknown"),
        )
    }

    @Test
    fun discoversModelsImportedByTheLegacyVersion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppPreference.init(context)
        val originalPath = AppPreference.gemma_translation_model_path_preference
        val legacyDirectory = File(requireNotNull(context.getExternalFilesDir(null)), "models")
        val legacyModel = File(legacyDirectory, "gemma-4-E2B-it-legacy-test.litertlm")
        try {
            legacyDirectory.mkdirs()
            legacyModel.writeBytes(byteArrayOf(1))
            AppPreference.gemma_translation_model_path_preference = ""
            val manager = GemmaTranslationManager(context, AppPreference, GemmaRuntimeClient(context))

            assertTrue(manager.installedModels().any { it.file.absolutePath == legacyModel.absolutePath })
            assertEquals(legacyModel.absolutePath, manager.selectedModel()?.file?.absolutePath)
        } finally {
            legacyModel.delete()
            AppPreference.gemma_translation_model_path_preference = originalPath
        }
    }
}
