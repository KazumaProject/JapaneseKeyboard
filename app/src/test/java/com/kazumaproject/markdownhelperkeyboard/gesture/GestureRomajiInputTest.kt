package com.kazumaproject.markdownhelperkeyboard.gesture

import android.app.Activity
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.gesture_keyboard.GestureKeyboardView
import com.kazumaproject.gesture_keyboard.GestureOutput
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService
import com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana.CustomRomajiScreenConverter
import com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana.RomajiKanaConverter
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers as Reflect
import org.robolectric.util.ReflectionHelpers.ClassParameter
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GestureRomajiInputTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val context = ContextThemeWrapper(activity, R.style.Theme_MarkdownKeyboard)
    private val connection = mock(InputConnection::class.java)
    private val service = spy(IMEService().also {
        Reflect.callInstanceMethod<Unit>(it, "attachBaseContext", ClassParameter.from(Context::class.java, context))
        AppPreference.init(context); it.appPreference = AppPreference
    }).also {
        doReturn(EditorInfo().apply { inputType = 1 }).`when`(it).getCurrentInputEditorInfo()
        doReturn(connection).`when`(it).getCurrentInputConnection()
    }
    private val binding = MainLayoutBinding.inflate(LayoutInflater.from(context)).also {
        activity.setContentView(it.root)
        Reflect.setField(service, "mainLayoutBinding", it)
        Reflect.getField<MutableStateFlow<TenKeyQWERTYMode>>(service, "_tenKeyQWERTYMode").value = TenKeyQWERTYMode.Gesture
        Reflect.setField(service, "romajiConverter", RomajiKanaConverter(mapOf("ka" to ("か" to 2))))
        Reflect.setField(service, "isDefaultRomajiHenkanMap", true)
    }
    private val input get() = Reflect.getField<MutableStateFlow<String>>(service, "_inputString")
    private suspend fun output(value: GestureOutput): Boolean = suspendCoroutineUninterceptedOrReturn { continuation ->
        Reflect.callInstanceMethod<Any>(service, "dispatchGestureOutput",
            ClassParameter.from(GestureKeyboardView::class.java, binding.gestureView),
            ClassParameter.from(GestureOutput::class.java, value),
            ClassParameter.from(Continuation::class.java, continuation))
    }
    @Test fun standardRomajiComposesAndPendingNFlushesBeforeKana() = runBlocking {
        output(GestureOutput.Romaji("k")); assertEquals("k", input.value)
        output(GestureOutput.Romaji("a")); assertEquals("か", input.value)
        output(GestureOutput.Romaji("n")); assertEquals("かn", input.value)
        output(GestureOutput.Reading("あ")); assertEquals("かんあ", input.value)
    }
    @Test fun activeCustomRomajiRulesAreUsed() = runBlocking {
        Reflect.setField(service, "isDefaultRomajiHenkanMap", false)
        Reflect.setField(service, "customRomajiScreenConverter", CustomRomajiScreenConverter(mapOf("ka" to ("き" to 2))))
        output(GestureOutput.Romaji("k")); output(GestureOutput.Romaji("a"))
        assertEquals("き", input.value)
    }
    @Test fun endingAnEditorSessionCannotFlushItsPendingNIntoTheNextEditor() = runBlocking {
        output(GestureOutput.Romaji("n")); assertEquals("n", input.value)
        Reflect.callInstanceMethod<Unit>(service, "cancelGestureInputs")
        input.value = ""
        Reflect.callInstanceMethod<Unit>(service, "flushGestureRomaji")
        assertEquals("", input.value)
    }
}
