package com.kazumaproject.markdownhelperkeyboard.gemma

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.preference.PreferenceManager
import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaMediaType
import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaRuntimeClient
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GemmaManagerInstrumentedTest {
    @Test
    fun autoSelectsInstalledModelAndExposesItsCapabilities() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AppPreference.init(context)
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val originalPath = preferences.getString(MODEL_PATH_PREFERENCE, null)
        try {
            val manager = GemmaTranslationManager(context, AppPreference, GemmaRuntimeClient(context))
            val installed = manager.installedModels()
            assumeTrue("Install a .litertlm model before running this test", installed.isNotEmpty())

            val selected = requireNotNull(manager.selectedModel())
            assertEquals(selected.file.absolutePath, AppPreference.gemma_translation_model_path_preference)
            assertEquals(GemmaModelCatalog.descriptorFor(selected.file).id, selected.descriptor.id)
            assertTrue(selected.descriptor.supports(GemmaMediaType.TEXT))
            if (selected.descriptor.id == "gemma4_e2b_it") {
                assertTrue(selected.descriptor.supports(GemmaMediaType.IMAGE))
                assertTrue(selected.descriptor.supports(GemmaMediaType.AUDIO))
            }
        } finally {
            preferences.edit().apply {
                if (originalPath == null) remove(MODEL_PATH_PREFERENCE)
                else putString(MODEL_PATH_PREFERENCE, originalPath)
            }.commit()
        }
    }

    @Test(timeout = 360_000)
    fun translatesTextUsingPersistedLanguageCode() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AppPreference.init(context)
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val originalPath = preferences.getString(MODEL_PATH_PREFERENCE, null)
        val originalTarget = preferences.getString(TARGET_LANGUAGE_PREFERENCE, null)
        val originalEnabled = preferences.getBoolean(ENABLE_PREFERENCE, false)
        val hadEnabled = preferences.contains(ENABLE_PREFERENCE)
        val manager = GemmaTranslationManager(context, AppPreference, GemmaRuntimeClient(context))
        try {
            val model = manager.installedModels().firstOrNull()
            assumeTrue("Install a .litertlm model before running this test", model != null)
            AppPreference.gemma_translation_model_path_preference = requireNotNull(model).file.absolutePath
            AppPreference.gemma_translation_target_language_preference = "en"
            AppPreference.enable_gemma_translation_preference = true

            assertTrue("Gemma text runtime did not initialize", manager.initializeIfEnabled(true))
            val translated = manager.translate("こんにちは")
            assertTrue("Expected an English translation, got: $translated", translated.contains("hello", true))
        } finally {
            manager.disable()
            preferences.edit().apply {
                if (originalPath == null) remove(MODEL_PATH_PREFERENCE)
                else putString(MODEL_PATH_PREFERENCE, originalPath)
                if (originalTarget == null) remove(TARGET_LANGUAGE_PREFERENCE)
                else putString(TARGET_LANGUAGE_PREFERENCE, originalTarget)
                if (hadEnabled) putBoolean(ENABLE_PREFERENCE, originalEnabled)
                else remove(ENABLE_PREFERENCE)
            }.commit()
        }
    }

    private companion object {
        const val MODEL_PATH_PREFERENCE = "gemma_translation_model_path_preference"
        const val TARGET_LANGUAGE_PREFERENCE = "gemma_translation_target_language_preference"
        const val ENABLE_PREFERENCE = "gemma_translation_enable_preference"
    }
}
