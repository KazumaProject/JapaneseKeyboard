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

/** Exact model outputs through the production manager, using external model fixtures. */
@RunWith(AndroidJUnit4::class)
class AiTextTransformationInstrumentedTest {
    @Test(timeout = 600_000)
    fun foregroundAndFloatingInstructionsTransformTheInput() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("Use the isolated QA application ID", context.packageName == "com.kazumaproject.universalaiqa")
        val args = InstrumentationRegistry.getArguments()
        val filename = args.getString("aiModel")
        assumeTrue("Supply aiModel and its external fixture", filename != null)
        val model = File(context.getExternalFilesDir(null), "gemma/$filename")
        assertTrue(model.isFile)
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val saved = preferences.all.toMap()
        AppPreference.init(context)
        val client = GemmaRuntimeClient(context)
        val manager = GemmaTranslationManager(context, AppPreference, client)
        try {
            AppPreference.gemma_translation_model_path_preference = model.absolutePath
            AppPreference.gemma_translation_backend_preference = args.getString("aiBackend") ?: "cpu"
            AppPreference.enable_gemma_translation_preference = true
            assertTrue(manager.getModelSummary(), manager.initializeIfEnabled(true))
            args.getString("aiExpectedBackend")?.let {
                assertEquals(it, (manager.loadState.value as GemmaLoadState.Ready).backend)
            }
            val uppercase = "Convert every Latin letter to uppercase."
            val cases = listOf(
                Triple("hello", uppercase, "HELLO"),
                Triple("next", uppercase, "NEXT"),
                Triple("world", uppercase, "WORLD"),
                Triple("keyboard", uppercase, "KEYBOARD"),
                Triple("Android 13!", uppercase, "ANDROID 13!"),
                Triple("HELLO", "Convert every Latin letter to lowercase.", "hello"),
                Triple("red", "Replace the word red with blue.", "blue"),
                // Repeat after different instructions to detect stale results or leaked conversation state.
                Triple("next", uppercase, "NEXT"),
            )
            val failures = mutableListOf<String>()
            for ((input, instruction, expected) in cases) {
                for (floating in listOf(false, true)) {
                    val actual = if (floating) manager.generateFloatingText(input, instruction)
                        else manager.runCustomPrompt(input, "QA action", instruction)
                    println("AI transform model=$filename floating=$floating input=[$input] expected=[$expected] actual=[$actual]")
                    if (actual != expected) failures += "floating=$floating input=[$input] expected=[$expected] actual=[$actual]"
                }
            }
            assertTrue(failures.joinToString("\n"), failures.isEmpty())
        } finally {
            client.close()
            preferences.edit().clear().apply {
                saved.forEach { (key, value) -> when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is Set<*> -> @Suppress("UNCHECKED_CAST") putStringSet(key, value as Set<String>)
                } }
            }.commit()
            AppPreference.init(context)
        }
    }
}
