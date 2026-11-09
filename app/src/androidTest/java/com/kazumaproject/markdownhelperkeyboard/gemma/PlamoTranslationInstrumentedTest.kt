package com.kazumaproject.markdownhelperkeyboard.gemma

import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaRuntimeClient
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Official weights are provided externally; neither weights nor sidecar templates are bundled. */
@RunWith(AndroidJUnit4::class)
class PlamoTranslationInstrumentedTest {
    @Test(timeout = 600_000)
    fun translatesBothDirectionsWithoutTranslatingTheInstruction() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val args = InstrumentationRegistry.getArguments()
        val name = args.getString("plamoModel")
        assumeTrue("Supply an external plamoModel fixture", name != null)
        val model = File(context.getExternalFilesDir(null), "gemma/$name")
        assertTrue(model.isFile)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val saved = prefs.all.toMap()
        AppPreference.init(context)
        val manager = GemmaTranslationManager(context, AppPreference, GemmaRuntimeClient(context))
        try {
            AppPreference.gemma_translation_model_path_preference = model.absolutePath
            AppPreference.gemma_translation_backend_preference = args.getString("plamoBackend") ?: "cpu"
            AppPreference.enable_gemma_translation_preference = true
            assertTrue(manager.getModelSummary(), manager.initializeIfEnabled(true))
            for ((target, source, expected) in listOf(
                Triple("en", "会議は来週の月曜日に変更されました。", "Monday"),
                Triple("ja", "The meeting has been moved to next Monday.", "月曜日"),
                Triple("en", "こんにちは。", "Hello"),
                Triple("ja", "Hello.", "こんにちは"),
            )) {
                AppPreference.gemma_translation_target_language_preference = target
                val text = manager.translate(source)
                println("PLAMO model=$name backend=${manager.loadState.value} target=$target source=$source output=$text")
                assertTrue(text, text.contains(expected, true))
                assertFalse(text, text.contains("<|plamo:"))
                assertFalse(text, text.contains("Detect the source language", true))
                assertFalse(text, text.contains("ソース言語"))
            }
        } finally {
            manager.disable()
            prefs.edit().clear().apply {
                saved.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                        is Set<*> -> @Suppress("UNCHECKED_CAST") putStringSet(key, value as Set<String>)
                    }
                }
            }.commit()
            AppPreference.init(context)
        }
    }
}
