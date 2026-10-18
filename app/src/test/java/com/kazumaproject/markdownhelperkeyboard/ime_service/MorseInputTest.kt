package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.app.Activity
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana.RomajiKanaConverter
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.morse_keyboard.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers as Reflect
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MorseInputTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val context = ContextThemeWrapper(activity, R.style.Theme_MarkdownKeyboard)
    private val connection = mock(InputConnection::class.java)
    private val service = spy(IMEService().also {
        Reflect.callInstanceMethod<Unit>(it, "attachBaseContext", ClassParameter.from(Context::class.java, context))
        AppPreference.init(context); it.appPreference = AppPreference
    }).also {
        doReturn(EditorInfo().apply { inputType = 1 }).`when`(it).getCurrentInputEditorInfo()
        doReturn(connection).`when`(it).getCurrentInputConnection()
        `when`(connection.commitText(any(), anyInt())).thenReturn(true)
        `when`(connection.setComposingText(any(), anyInt())).thenReturn(true)
    }
    private val binding = MainLayoutBinding.inflate(LayoutInflater.from(context)).also {
        activity.setContentView(it.root)
        Reflect.setField(service, "mainLayoutBinding", it)
        Reflect.getField<MutableStateFlow<TenKeyQWERTYMode>>(service, "_tenKeyQWERTYMode").value = TenKeyQWERTYMode.Morse
        Reflect.setField(service, "isDefaultRomajiHenkanMap", true)
        Reflect.setField(service, "romajiConverter", RomajiKanaConverter(mapOf("ka" to ("か" to 2), "a" to ("あ" to 1))))
        Reflect.callInstanceMethod<Unit>(service, "configureMorseView", ClassParameter.from(MorseKeyboardView::class.java, it.morseView))
    }
    private val input get() = Reflect.getField<MutableStateFlow<String>>(service, "_inputString")
    private fun send(signal: String): Boolean {
        signal.forEach(binding.morseView::appendSignal)
        return binding.morseView.finishSignal()
    }
    @Test fun wabunBoundariesAccumulateComposingReadingAndModifiersDoNotCommit() {
        assertTrue(send(".-..")); assertEquals("か", input.value)
        assertTrue(send("..")); assertEquals("が", input.value)
        assertTrue(send(".--")); assertEquals("がや", input.value)
        binding.morseView.onCommand(MorseCommand.SMALL_KANA)
        assertEquals("がゃ", input.value)
        verify(connection, never()).commitText(any(), anyInt())
    }
    @Test fun romajiUsesExistingConverterAndPreservesIntermediateConsonant() {
        binding.morseView.selectMap(MorsePresets.ROMAJI)
        assertTrue(send("-.-")); assertEquals("k", input.value)
        assertTrue(send(".-")); assertEquals("か", input.value)
        verify(connection, never()).commitText(any(), anyInt())
    }
    @Test fun literalDeleteIsTextWhereasCommandDeleteEditsReading() {
        val map = MorseMap("test", "Test", listOf(
            MorseEntry(".", MorseOutput.LiteralText("削除")),
            MorseEntry("-", MorseOutput.Command(MorseCommand.DELETE))))
        binding.morseView.configure(MorseSettings(sound = false), listOf(map), map.id)
        assertTrue(send(".")); verify(connection).commitText("削除", 1)
        input.value = "かき"
        assertTrue(send("-")); assertEquals("か", input.value)
    }
    @Test fun deletePrefersSignalThenReadingAndUnknownSignalRemains() {
        input.value = "かき"
        binding.morseView.appendSignal('.')
        assertTrue(binding.morseView.deleteSignal()); assertEquals("かき", input.value)
        binding.morseView.onCommand(MorseCommand.DELETE); assertEquals("か", input.value)
        assertFalse(send("........")); assertEquals("........", binding.morseView.signal)
        assertEquals("か", input.value)
    }
    @Test fun changingMapReinterpretsPendingSignalWithoutDeletingIt() {
        ".-".forEach(binding.morseView::appendSignal)
        binding.morseView.selectMap(MorsePresets.ENGLISH)
        assertEquals(".-", binding.morseView.signal)
        assertTrue(binding.morseView.finishSignal())
        verify(connection).commitText("a", 1)
    }
}
