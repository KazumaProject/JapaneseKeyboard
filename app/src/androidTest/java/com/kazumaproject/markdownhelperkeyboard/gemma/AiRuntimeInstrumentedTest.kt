package com.kazumaproject.markdownhelperkeyboard.gemma

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.BuildConfig
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaMediaType
import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaRuntimeClient
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android runtime/manager test; fixtures are supplied externally, never bundled. */
@RunWith(AndroidJUnit4::class)
class AiRuntimeInstrumentedTest {
    @Test
    fun tensorDispatchIsExtractedOnArm64() {
        assumeTrue(Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dispatch = File(context.applicationInfo.nativeLibraryDir, "libLiteRtDispatch_GoogleTensor.so")
        assertEquals("The Tensor dispatch library belongs to Full builds", BuildConfig.HAS_GEMMA, dispatch.isFile)
    }

    @Test(timeout = 60_000)
    fun rejectsTensorModelOnIncompatibleDevice() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val filename = arguments.getString("aiIncompatibleTensorModel")
        assumeTrue("Supply a Tensor model built for a different SoC", filename != null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = File(File(context.getExternalFilesDir(null), "gemma"), requireNotNull(filename))
        assertTrue("Missing fixture: $model", model.isFile)
        val client = GemmaRuntimeClient(context)
        try {
            val error = runCatching { client.initialize(model.absolutePath, "npu_if_available") }.exceptionOrNull()
            assertEquals(context.getString(R.string.ai_npu_incompatible_tensor_model), error?.message)
        } finally {
            client.close()
        }
    }

    @Test(timeout = 600_000)
    fun selectedModelHandlesDeclaredInputsAndRecovers() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val arguments = InstrumentationRegistry.getArguments()
        val filename = arguments.getString("aiModel")
        assumeTrue("Supply aiModel and install its fixture in files/gemma", filename != null)
        val directory = File(requireNotNull(context.getExternalFilesDir(null)), "gemma")
        val model = File(directory, requireNotNull(filename))
        assertTrue("Missing fixture: $model", model.isFile)
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val saved = preferences.all.toMap()
        AppPreference.init(context)
        val client = GemmaRuntimeClient(context)
        val manager = GemmaTranslationManager(context, AppPreference, client)
        val image = File(context.cacheDir, "ai-runtime-red-square.png")
        try {
            AppPreference.gemma_translation_model_path_preference = model.absolutePath
            AppPreference.gemma_translation_backend_preference = arguments.getString("aiBackend") ?: "cpu"
            AppPreference.enable_gemma_translation_preference = true
            AppPreference.gemma_translation_target_language_preference = "en"
            if (arguments.getString("aiImport") == "true") {
                assertEquals(model.absolutePath, manager.importModelFromUri(Uri.fromFile(model)))
            }
            val projector = arguments.getString("aiProjector")
            if (projector != null) {
                val fixture = File(directory, projector)
                val path = if (arguments.getString("aiImport") == "true") manager.importProjectorFromUri(Uri.fromFile(fixture)) else fixture.absolutePath
                manager.selectProjector(path)
                if (path != fixture.absolutePath) assertFalse(manager.installedModels().any { it.file.absolutePath == path })
            }
            val started = SystemClock.elapsedRealtime()
            val initialized = manager.initializeIfEnabled(true)
            assertTrue(manager.getModelSummary(), initialized)
            arguments.getString("aiExpectedBackend")?.let { expected ->
                assertEquals(expected, (manager.loadState.value as GemmaLoadState.Ready).backend)
            }
            println("AI fixture=$filename initMs=${SystemClock.elapsedRealtime() - started} state=${manager.loadState.value}")
            val expectedFlags = arguments.getString("aiFlags")?.toInt() ?: 0
            assertEquals(expectedFlags, (manager.loadState.value as GemmaLoadState.Ready).modalityFlags)
            assertEquals(expectedFlags and 1 != 0, manager.supportsMedia(GemmaMediaType.IMAGE))
            assertEquals(expectedFlags and 2 != 0, manager.supportsMedia(GemmaMediaType.AUDIO))
            val text = manager.runCustomPrompt("hello", "Uppercase", "Convert Latin letters to uppercase.")
            println("AI text=$text")
            // Text models and the general multimodal fixture exercise text transformation.
            // Specialized vision/audio fixtures are evaluated on their media task below.
            if (expectedFlags == 0 || expectedFlags == 3) assertEquals("HELLO", text)
            else assertFalse(text.isBlank())
            assertFalse(text.contains("<think>"))

            Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).also { bitmap ->
                Canvas(bitmap).drawColor(Color.RED)
                image.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                bitmap.recycle()
            }
            if (expectedFlags and 1 != 0) {
                val result = manager.runMediaPrompt("What is the main color of this image? Reply with only the color name in English.", image.absolutePath, GemmaMediaType.IMAGE)
                println("AI image=$result")
                assertTrue("Expected red: $result", result.contains("red", true))
            } else {
                assertTrue(runCatching { manager.runMediaPrompt("Describe.", image.absolutePath, GemmaMediaType.IMAGE) }.isFailure)
            }
            if (expectedFlags and 2 != 0) {
                val audio = File(directory, "speech.wav")
                assertTrue("Supply the spoken Hello world fixture", audio.isFile)
                val result = manager.runMediaPrompt(arguments.getString("aiAudioPrompt") ?: GemmaPromptBuilder.build(GemmaBuiltInActions.all().first { it.builtInKey == "audio_dictate" }, "English"), audio.absolutePath, GemmaMediaType.AUDIO)
                println("AI audio=$result")
                assertTrue("Expected spoken hello: $result", result.contains("hello", true))
            }
            val invalid = File(context.cacheDir, "invalid-ai-model.litertlm")
            try {
                invalid.outputStream().use { it.write("<html>not a model</html>".toByteArray()); it.write(ByteArray(1_048_576)) }
                assertTrue(runCatching { manager.importModelFromUri(Uri.fromFile(invalid)) }.isFailure)
                assertEquals(model.absolutePath, AppPreference.gemma_translation_model_path_preference)
                assertTrue(manager.isTranslationAvailable())
            } finally { invalid.delete() }
            if (model.extension.equals("gguf", true)) {
                val overflow = runCatching { client.generate("word ".repeat(8_000)) }.exceptionOrNull()
                assertTrue("Expected an explicit context limit error: $overflow", overflow?.message?.contains("limit") == true)
                if (projector != null) {
                    val selectedProjector = manager.projectorPath()
                    assertTrue(runCatching { manager.selectProjector("/missing/projector.gguf") }.isFailure)
                    assertTrue(runCatching { manager.selectProjector(model.absolutePath) }.isFailure)
                    assertEquals(selectedProjector, manager.projectorPath())
                }
            }
            val preview = async { runCatching { manager.generateFloatingText("hello", "Write a long story about this word.") } }
            delay(150)
            manager.cancelFloatingText()
            preview.await()
            assertFalse(manager.runCustomPrompt("hello", "Uppercase", "Convert Latin letters to uppercase.").isBlank())

            arguments.getString("aiOtherModel")?.let { other ->
                val pending = async { runCatching { manager.generateFloatingText("hello", "Write a 1000-word story about this word.") } }
                delay(150)
                assertTrue(manager.selectModel(File(directory, other).absolutePath))
                assertNull("A result from the old model must not survive a model switch", pending.await().getOrNull())
                assertTrue(manager.getModelSummary(), manager.initializeIfEnabled(true))
                assertFalse(manager.runCustomPrompt("hello", "Uppercase", "Convert Latin letters to uppercase.").isBlank())
                assertTrue(manager.selectModel(model.absolutePath))
                assertTrue(manager.getModelSummary(), manager.initializeIfEnabled(true))
            }
            arguments.getString("aiWrongProjector")?.let { wrong ->
                val originalProjector = manager.projectorPath()
                manager.selectProjector(File(directory, wrong).absolutePath)
                assertFalse("An incompatible projector must fail initialization", manager.initializeIfEnabled(true))
                manager.selectProjector(originalProjector)
                assertTrue(manager.getModelSummary(), manager.initializeIfEnabled(true))
            }

            // Kill only the isolated model process, then use the same client/configuration again.
            fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(command),
            ).bufferedReader().use { it.readText().trim() }
            val pid = shell("pidof ${context.packageName}:gemma").toInt()
            shell("run-as ${context.packageName} kill -9 $pid")
            delay(800)
            val recovered = manager.runCustomPrompt("hello", "Uppercase", "Convert Latin letters to uppercase.")
            println("AI recovered=$recovered")
            if (expectedFlags == 0 || expectedFlags == 3) assertEquals("HELLO", recovered)
            else assertFalse(recovered.isBlank())
        } finally {
            manager.disable()
            image.delete()
            preferences.edit().clear().apply {
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
