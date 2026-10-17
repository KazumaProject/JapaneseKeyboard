package com.kazumaproject.markdownhelperkeyboard.ime_service.voice_input

import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoiceInputRecognitionTest {
    @Test
    fun partialAndFinalCallbacksUseTheSameRecognitionSessionSettings() {
        val service = IMEService()
        setField(service, "activeVoiceInputLanguage", "ja-JP")
        setField(service, "activeVoiceInputSettings", VoiceInputSettings(enabled = true))
        setField(service, "isListening", true)
        val listener = listener(service)
        listener.onPartialResults(results("あ い"))
        assertEquals("あい", input(service))
        // Later preference changes must not change an in-flight recognition result.
        setField(service, "voiceInputSettings", VoiceInputSettings(true, VoiceInputSpaceReplacement.COMMA))
        listener.onResults(results("あ い う"))
        assertEquals("あいう", input(service))
        assertFalse(getField(service, "isListening") as Boolean)
    }

    @Test
    fun callbacksPreserveEnglishAndDisabledJapaneseAndIgnoreMissingMatches() {
        val service = IMEService()
        val listener = listener(service)
        setField(service, "activeVoiceInputLanguage", "en-CA")
        setField(service, "activeVoiceInputSettings", VoiceInputSettings(true, VoiceInputSpaceReplacement.NEWLINE))
        listener.onPartialResults(results("hello world"))
        listener.onResults(results("hello world again"))
        assertEquals("hello world again", input(service))
        listener.onPartialResults(null)
        listener.onResults(Bundle())
        assertEquals("hello world again", input(service))
        setField(service, "activeVoiceInputLanguage", "ja-JP")
        setField(service, "activeVoiceInputSettings", VoiceInputSettings())
        listener.onPartialResults(results("あ い"))
        listener.onResults(results("あ い う"))
        assertEquals("あ い う", input(service))
    }

    private fun listener(service: IMEService) = IMEService::class.java
        .getDeclaredMethod("createVoiceRecognitionListener").apply { isAccessible = true }
        .invoke(service) as RecognitionListener

    private fun results(text: String) = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
    }

    private fun input(service: IMEService) = (getField(service, "_inputString") as StateFlow<*>).value

    private fun getField(service: IMEService, name: String): Any? = IMEService::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(service)

    private fun setField(service: IMEService, name: String, value: Any) {
        IMEService::class.java.getDeclaredField(name).apply { isAccessible = true }.set(service, value)
    }
}
