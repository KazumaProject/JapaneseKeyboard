package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.app.Activity
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
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
class SumireInputLifecycleTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val context = ContextThemeWrapper(activity, R.style.Theme_MarkdownKeyboard)
    private val service = spy(IMEService().also {
        Reflect.callInstanceMethod<Unit>(it, "attachBaseContext", ClassParameter.from(Context::class.java, context))
        AppPreference.init(context)
        it.appPreference = AppPreference
    }).also {
        doReturn(EditorInfo().apply { inputType = 1 }).`when`(it).getCurrentInputEditorInfo()
    }
    private val binding = MainLayoutBinding.inflate(LayoutInflater.from(context)).also {
        activity.setContentView(it.root)
        Reflect.setField(service, "mainLayoutBinding", it)
        Reflect.getField<MutableStateFlow<TenKeyQWERTYMode>>(service, "_tenKeyQWERTYMode").value = TenKeyQWERTYMode.Sumire
        Reflect.callInstanceMethod<Unit>(service, "setupCustomKeyboardListeners", ClassParameter.from(MainLayoutBinding::class.java, it))
        Reflect.callInstanceMethod<Unit>(service, "setSumireLayoutTo", ClassParameter.from(FlickKeyboardView::class.java, it.customLayoutDefault))
    }
    private val listener get() = Reflect.getField<FlickKeyboardView.OnKeyboardActionListener>(binding.customLayoutDefault, "listener")

    @Test fun tapAndLongPressCycleBothTheLayoutAndSessionInputMode() {
        for (longPress in listOf(false, true)) {
            Reflect.setField(service, "customKeyboardMode", KeyboardInputMode.HIRAGANA)
            for ((mode, session, label) in listOf(
                Triple(KeyboardInputMode.ENGLISH, InputMode.ModeEnglish, "ABC"),
                Triple(KeyboardInputMode.SYMBOLS, InputMode.ModeNumber, "1"),
                Triple(KeyboardInputMode.HIRAGANA, InputMode.ModeJapanese, "あ"))) {
                if (longPress) listener.onActionLongPress(KeyAction.ChangeInputMode)
                else listener.onAction(KeyAction.ChangeInputMode, false)
                assertEquals(mode, Reflect.getField<KeyboardInputMode>(service, "customKeyboardMode"))
                assertEquals(session, Reflect.getField<InputMode>(service, "currentInputModeForSession"))
                assertTrue("mode=$mode longPress=$longPress labels=${keys().map { it.label }}",
                    keys().any { it.label.lineSequence().first() == label })
            }
        }
    }

    @Test fun renderingCurrentSurfaceKeepsControllersAndAnActiveKeyPressed() {
        binding.customLayoutDefault.visibility = View.VISIBLE
        val key = binding.customLayoutDefault.getChildAt(0)
        key.isPressed = true
        val controllers = Reflect.getField<List<Any>>(binding.customLayoutDefault, "crossFlickControllers").toList()
        Reflect.callInstanceMethod<Unit>(service, "renderCurrentKeyboardSurface")
        assertTrue(key.isPressed)
        assertSame(key, binding.customLayoutDefault.getChildAt(0))
        assertEquals(controllers, Reflect.getField<List<Any>>(binding.customLayoutDefault, "crossFlickControllers"))
    }

    private fun keys() = Reflect.getField<List<Any>>(binding.customLayoutDefault, "keyInfos").map {
        Reflect.getField<KeyData>(it, "keyData")
    }
}
