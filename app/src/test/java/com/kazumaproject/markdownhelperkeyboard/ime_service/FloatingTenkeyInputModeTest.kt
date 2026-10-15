package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FloatingKeyboardLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers as Reflect
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** Exercises the floating TenKey's actual mode-change callback without loading dictionaries. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingTenkeyInputModeTest {
    private val context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_MarkdownKeyboard,
    )
    private lateinit var service: IMEService
    private lateinit var floating: FloatingKeyboardLayoutBinding
    // Floating mode never resizes the docked view, but the shared switch handler accepts its binding.
    private val main = mock<MainLayoutBinding>()

    @Before
    fun setup() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
        service = spy(IMEService().also {
            Reflect.callInstanceMethod<Unit>(it, "attachBaseContext", ClassParameter.from(Context::class.java, context))
            it.appPreference = AppPreference
            Reflect.setField(it, "runtimeInputSharedPreferences",
                PreferenceManager.getDefaultSharedPreferences(context))
        })
        doReturn(EditorInfo().apply { inputType = 1 }).whenever(service).getCurrentInputEditorInfo()
        floating = FloatingKeyboardLayoutBinding.inflate(LayoutInflater.from(context))
        Reflect.setField(service, "mainLayoutBinding", main)
        Reflect.setField(service, "floatingKeyboardBinding", floating)
        Reflect.setField(service, "isKeyboardFloatingMode", true)
        Reflect.setField(service, "isTablet", false)
        Reflect.setField(service, "tenkeyQWERTYSwitchNumber", true)
        configure()
        render()
    }

    @After
    fun cleanup() {
        floating.keyboardViewFloating.cancelTenKeyScope()
        listOf("scope", "ioScope").forEach { Reflect.getField<CoroutineScope>(service, it).cancel() }
        Reflect.getField<ExecutorCoroutineDispatcher>(service, "kanaKanjiConversionDispatcher").close()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @Test
    fun englishSwitchUsesQwertyAndKeepsTenkeyAsTheReturnTarget() {
        switchMode()

        assertMode(TenKeyQWERTYMode.TenKeyQWERTY, InputMode.ModeEnglish)
        assertEquals(View.VISIBLE, floating.qwertyViewFloating.visibility)
        assertEquals(View.GONE, floating.keyboardViewFloating.visibility)
        assertFalse(floating.qwertyViewFloating.snapshotUiState().romajiMode)
        assertEquals(RestartInputModeQwertyReturnSource.TenKeyDefault,
            Reflect.getField<RestartInputModeQwertyReturnSource>(service, "qwertySwitchNumberKeyReturnSource"))

        Reflect.callInstanceMethod<Unit>(service, "returnDefaultQwertyToTenkeyInputMode",
            ClassParameter.from(InputMode::class.java, InputMode.ModeJapanese),
            ClassParameter.from(MainLayoutBinding::class.java, main),
            ClassParameter.from(String::class.java, ""))
        render()

        assertMode(TenKeyQWERTYMode.Default, InputMode.ModeJapanese)
        assertEquals(InputMode.ModeJapanese, floating.keyboardViewFloating.currentInputMode.value)
        assertEquals(View.VISIBLE, floating.keyboardViewFloating.visibility)
        assertEquals(View.GONE, floating.qwertyViewFloating.visibility)
        // Reconfiguration when returning to flick must keep the callback installed.
        configure()
        switchMode()
        assertMode(TenKeyQWERTYMode.TenKeyQWERTY, InputMode.ModeEnglish)
    }

    @Test
    fun disabledPreferenceKeepsFlickAndSynchronizesAllThreeInputModes() {
        Reflect.setField(service, "tenkeyQWERTYSwitchNumber", false)
        for (input in listOf(InputMode.ModeEnglish, InputMode.ModeNumber, InputMode.ModeJapanese)) {
            switchMode()
            assertMode(TenKeyQWERTYMode.Default, input)
            assertEquals(View.VISIBLE, floating.keyboardViewFloating.visibility)
            assertEquals(View.GONE, floating.qwertyViewFloating.visibility)
        }
    }

    @Test
    fun twoStateKeyboardAlsoSwitchesEnglishToQwerty() {
        Reflect.setField(service, "tenkeyUseThreeStateKeyboard", false)
        configure()
        switchMode()
        assertMode(TenKeyQWERTYMode.TenKeyQWERTY, InputMode.ModeEnglish)
    }

    @Test
    fun tabletUsesItsOwnEnglishQwertyPreference() {
        Reflect.setField(service, "isTablet", true)
        Reflect.setField(service, "tabletTenkeyQwertySwitchEnglish", false)
        switchMode()
        assertMode(TenKeyQWERTYMode.Default, InputMode.ModeEnglish)

        switchMode() // Number
        switchMode() // Japanese
        Reflect.setField(service, "tenkeyQWERTYSwitchNumber", false)
        Reflect.setField(service, "tabletTenkeyQwertySwitchEnglish", true)
        switchMode()
        assertMode(TenKeyQWERTYMode.TenKeyQWERTY, InputMode.ModeEnglish)
    }

    private fun configure() = Reflect.callInstanceMethod<Unit>(service, "configureFloatingTenKeyView",
        ClassParameter.from(FloatingKeyboardLayoutBinding::class.java, floating))

    private fun render() = Reflect.callInstanceMethod<Unit>(service, "renderCurrentKeyboardStateOnActiveSurface")

    private fun switchMode() = Reflect.callInstanceMethod<Unit>(floating.keyboardViewFloating, "handleClickInputModeSwitch")

    private fun assertMode(layout: TenKeyQWERTYMode, input: InputMode) {
        assertEquals(layout, Reflect.getField<MutableStateFlow<TenKeyQWERTYMode>>(service, "_tenKeyQWERTYMode").value)
        assertEquals(input, Reflect.getField<InputMode>(service, "currentInputModeForSession"))
    }
}
