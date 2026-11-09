package com.kazumaproject.markdownhelperkeyboard.gemma

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GemmaRuntimeClientModelTest {
    private class Runtime : IGemmaRuntime.Stub() {
        var model = ""
        var target = ""
        var cancelled = 0
        var holdAudio = false
        var pending: Pair<Long, IGemmaRuntimeCallback>? = null
        override fun initialize(path: String, backend: String, flags: Int, callback: IGemmaRuntimeCallback) = initializeModel(path, backend, "", callback)
        override fun initializeModel(path: String, backend: String, projector: String, callback: IGemmaRuntimeCallback) {
            model = path
            callback.onStateChanged("CAPABILITIES", if (path == "audio") "2" else "0")
            callback.onStateChanged("READY", "CPU")
        }
        override fun inspect(id: Long, path: String, format: String, callback: IGemmaRuntimeCallback) = callback.onResult(id, "{}")
        override fun generate(id: Long, prompt: String, media: String, type: Int, callback: IGemmaRuntimeCallback) {
            if (holdAudio && type == 2) pending = id to callback else callback.onResult(id, model)
        }
        override fun generateText(id: Long, text: String, instruction: String, callback: IGemmaRuntimeCallback) = callback.onResult(id, model)
        override fun generateTranslation(id: Long, text: String, instruction: String, targetLanguage: String, callback: IGemmaRuntimeCallback) {
            target = targetLanguage
            callback.onResult(id, model)
        }
        override fun generatePreview(id: Long, prompt: String, instruction: String, callback: IGemmaRuntimeCallback) = callback.onResult(id, model)
        override fun cancel(id: Long) {
            cancelled++
            pending?.takeIf { it.first == id }?.second?.onError(id, "cancelled")
        }
        override fun closeEngine() { model = "" }
    }

    private fun client(runtime: Runtime): GemmaRuntimeClient {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return GemmaRuntimeClient(context).also {
            GemmaRuntimeClient::class.java.getDeclaredField("runtime").apply { isAccessible = true }.set(it, runtime)
        }
    }

    @Test fun voiceAndForegroundTranslationRestoreTheirOwnConfigurations() = runBlocking {
        val runtime = Runtime()
        val client = client(runtime)
        client.initialize("translation", "cpu")
        assertEquals("audio", client.runVoiceModel(GemmaRuntimeClient.ModelConfig("audio", "cpu", ""), "dictate", "audio.wav"))
        assertEquals("translation", client.generateTranslation("日本語", "translate", "en"))
        assertEquals("en", runtime.target)
        assertEquals("audio", client.runVoiceModel(GemmaRuntimeClient.ModelConfig("audio", "cpu", ""), "dictate", "audio.wav"))
        assertEquals("translation", client.generateText("hello", "uppercase"))
    }

    @Test fun previewResumesAfterVoiceInsteadOfStayingOnItsModel() = runBlocking {
        val client = client(Runtime())
        client.initialize("translation", "cpu")
        client.runVoiceModel(GemmaRuntimeClient.ModelConfig("audio", "cpu", ""))
        assertEquals("translation", client.generatePreview("hello", "uppercase"))
    }

    @Test fun voiceCapabilityCheckRejectsTextOnlyModelAndLeavesTranslationRecoverable() = runBlocking {
        val client = client(Runtime())
        client.initialize("translation", "cpu")
        assertTrue(runCatching { client.runVoiceModel(GemmaRuntimeClient.ModelConfig("text-only", "cpu", "")) }.isFailure)
        assertEquals("translation", client.generateTranslation("hello", "translate", "ja"))
    }

    @Test fun translationCancellationDoesNotCancelVoiceRequest() = runBlocking {
        val runtime = Runtime().apply { holdAudio = true }
        val client = client(runtime)
        client.initialize("translation", "cpu")
        val voice = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { client.runVoiceModel(GemmaRuntimeClient.ModelConfig("audio", "cpu", ""), "dictate", "audio.wav") }
        }
        assertNotNull(runtime.pending)
        assertNull(client.generatePreview("hello"))
        client.cancelActive()
        assertEquals(0, runtime.cancelled)
        client.cancelVoice()
        assertTrue(voice.await().isFailure)
        assertEquals(1, runtime.cancelled)
        assertEquals("translation", client.generateText("hello", "uppercase"))
    }
}
