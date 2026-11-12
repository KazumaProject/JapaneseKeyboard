package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.listener.QWERTYKeyListener
import com.kazumaproject.core.domain.qwerty.QWERTYKey
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingDestinationType
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingDestinations
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingSearchIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QwertyRomajiInitialInputModeTest {
    private val context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_MarkdownKeyboard
    )
    private lateinit var service: IMEService
    private lateinit var main: MainLayoutBinding

    @Before
    fun setUp() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit().clear().commit()
        AppPreference.init(context)
        service = spy(IMEService().also {
            Reflect.callInstanceMethod<Unit>(it, "attachBaseContext",
                ClassParameter.from(Context::class.java, context))
            it.appPreference = AppPreference
            it.englishEngine = mock()
            Reflect.setField(it, "runtimeInputSharedPreferences", preferences)
        })
        doReturn(EditorInfo().apply { inputType = 1 }).whenever(service).getCurrentInputEditorInfo()
        main = MainLayoutBinding.inflate(LayoutInflater.from(context))
        Reflect.setField(service, "mainLayoutBinding", main)
        Reflect.setField(service, "keyboardOrder", listOf(KeyboardType.ROMAJI, KeyboardType.QWERTY))
        Reflect.setField(service, "qwertyShowSwitchRomajiEnglishPreference", true)
        Reflect.callInstanceMethod<Unit>(service, "setQWERTYKeyboard",
            ClassParameter.from(MainLayoutBinding::class.java, main))
    }

    @After
    fun tearDown() {
        main.keyboardView.cancelTenKeyScope()
        listOf("scope", "ioScope").forEach { Reflect.getField<CoroutineScope>(service, it).cancel() }
        Reflect.getField<ExecutorCoroutineDispatcher>(service, "kanaKanjiConversionDispatcher").close()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @Test
    fun unsetAndUnknownPreferencesStartInJapanese() {
        assertEquals("japanese", AppPreference.qwerty_romaji_initial_input_mode_preference)
        show(KeyboardType.ROMAJI)
        assertRomajiMode(InputMode.ModeJapanese)

        AppPreference.qwerty_romaji_initial_input_mode_preference = "unknown"
        show(KeyboardType.ROMAJI)
        assertRomajiMode(InputMode.ModeJapanese)
    }

    @Test
    fun englishPreferencePersistsAndSwitchKeyCanStillSelectJapanese() {
        AppPreference.qwerty_romaji_initial_input_mode_preference = "english"
        AppPreference.init(context)
        assertEquals("english", AppPreference.qwerty_romaji_initial_input_mode_preference)

        show(KeyboardType.ROMAJI)
        assertRomajiMode(InputMode.ModeEnglish)
        val listener = Reflect.getField<QWERTYKeyListener>(main.qwertyView, "qwertyKeyListener")
        listener.onReleasedQWERTYKey(QWERTYKey.QWERTYKeySwitchRomajiEnglish, null, null)
        assertRomajiMode(InputMode.ModeJapanese)
        listener.onReleasedQWERTYKey(QWERTYKey.QWERTYKeySwitchRomajiEnglish, null, null)
        assertRomajiMode(InputMode.ModeEnglish)

        show(KeyboardType.ROMAJI)
        assertRomajiMode(InputMode.ModeEnglish)
    }

    @Test
    fun changedPreferenceAppliesOnNextDisplayAndEnglishQwertyStaysEnglish() {
        AppPreference.qwerty_romaji_initial_input_mode_preference = "english"
        show(KeyboardType.ROMAJI)
        assertRomajiMode(InputMode.ModeEnglish)

        AppPreference.qwerty_romaji_initial_input_mode_preference = "japanese"
        show(KeyboardType.ROMAJI)
        assertRomajiMode(InputMode.ModeJapanese)
        show(KeyboardType.QWERTY)
        assertEquals(InputMode.ModeEnglish, Reflect.getField<InputMode>(service, "currentInputModeForSession"))
        assertFalse(main.qwertyView.snapshotUiState().romajiMode)
        assertFalse(main.qwertyView.snapshotUiState().showRomajiEnglishSwitchKey)
    }

    @Test
    fun legacyAndNewSettingsExposeTheSameJapaneseDefaultAndChoices() {
        for (items in listOf(SettingSearchIndex.searchable(context), SettingSearchIndex.legacySearchable(context))) {
            val item = items.single { it.key == "qwerty_romaji_initial_input_mode_preference" }
            assertEquals(R.id.qwertyRomajiPreferenceFragment, SettingDestinations.destinationId(item.destination))
        }
        val destination = SettingSearchIndex.searchable(context)
            .single { it.key == "qwerty_romaji_initial_input_mode_preference" }
            .destination as SettingDestinationType.ListPreference
        assertEquals("japanese", destination.defaultValue)
        assertEquals(listOf("japanese", "english"),
            context.resources.getStringArray(destination.entryValuesResId).toList())
        assertEquals(listOf("Japanese", "English"),
            context.resources.getStringArray(destination.entriesResId).toList())
    }

    private fun show(type: KeyboardType) = Reflect.callInstanceMethod<Unit>(service, "showResolvedKeyboard",
        ClassParameter.from(KeyboardType::class.java, type))

    private fun assertRomajiMode(input: InputMode) {
        val japanese = input == InputMode.ModeJapanese
        assertEquals(TenKeyQWERTYMode.TenKeyQWERTYRomaji,
            Reflect.getField<MutableStateFlow<TenKeyQWERTYMode>>(service, "_tenKeyQWERTYMode").value)
        assertEquals(input, Reflect.getField<InputMode>(service, "currentInputModeForSession"))
        assertEquals(japanese, Reflect.getField<Boolean>(service, "currentQwertyRomajiModeForSession"))
        val ui = main.qwertyView.snapshotUiState()
        assertEquals(japanese, ui.romajiMode)
        assertTrue(ui.showRomajiEnglishSwitchKey)
    }
}
