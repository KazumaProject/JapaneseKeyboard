package com.kazumaproject.markdownhelperkeyboard.gemma

import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaRuntimeClient
import com.kazumaproject.markdownhelperkeyboard.ime_service.voice_input.AiVoiceInputManager
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiVoiceInputInstrumentedTest {
    @Test(timeout = 600_000)
    fun independentVoiceModelTranscribesAndRestoresTranslationRuntime() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()
        val voiceName = args.getString("voiceModel")
        assumeTrue("Supply external voiceModel and translationModel fixtures", voiceName != null)
        val files = File(context.getExternalFilesDir(null), "gemma")
        val voiceModel = File(files, requireNotNull(voiceName))
        val translation = File(files, args.getString("translationModel") ?: "Qwen3-0.6B-Q8_0.gguf")
        val audio = File(files, args.getString("voiceAudio") ?: "speech.wav")
        assertTrue(voiceModel.isFile && translation.isFile && audio.isFile)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val saved = prefs.all.toMap()
        AppPreference.init(context)
        val client = GemmaRuntimeClient(context)
        val manager = GemmaTranslationManager(context, AppPreference, client)
        val voice = AiVoiceInputManager(context, client, manager)
        try {
            AppPreference.enable_gemma_translation_preference = true
            AppPreference.gemma_translation_model_path_preference = translation.absolutePath
            AppPreference.gemma_translation_backend_preference = "cpu"
            AppPreference.voice_input_engine = "ai"
            AppPreference.voice_input_model = voiceModel.absolutePath
            AppPreference.voice_input_backend = args.getString("voiceBackend") ?: "cpu"
            args.getString("voiceProjector")?.let {
                manager.selectProjectorForModel(voiceModel.absolutePath, File(files, it).absolutePath)
            }
            assertTrue(manager.getModelSummary(), manager.initializeIfEnabled(true))
            assertEquals("HELLO", manager.runCustomPrompt("hello", "Uppercase", "Convert every Latin letter to uppercase. Return only the result."))
            assertTrue(voice.loadState.value.toString(), voice.validate())
            val text = voice.transcribe(audio.absolutePath, args.getString("voiceLanguage") ?: "en-CA", AppPreference.voice_input_settings)
            println("VOICE model=$voiceName backend=${voice.loadState.value} transcript=$text")
            assertTrue(text, text.contains(args.getString("voiceExpected") ?: "hello", true))
            assertEquals(translation.absolutePath, AppPreference.gemma_translation_model_path_preference)
            assertEquals(voiceModel.absolutePath, AppPreference.voice_input_model)
            // The same client must restore translation after voice evicted its resident model.
            assertEquals("NEXT", manager.runCustomPrompt("next", "Uppercase", "Convert every Latin letter to uppercase. Return only the result."))
            AppPreference.voice_input_model = translation.absolutePath
            assertFalse("Text-only models must be rejected for voice", voice.validate())
            assertTrue(voice.loadState.value is GemmaLoadState.Failed)
            assertEquals("WORLD", manager.runCustomPrompt("world", "Uppercase", "Convert every Latin letter to uppercase. Return only the result."))
        } finally {
            voice.cancel()
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
