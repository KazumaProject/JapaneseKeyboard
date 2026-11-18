package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.updateKeyDataKeepingPlacement
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.view.AutoSizeButton
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IMEServiceCustomKeyboardGuideTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private lateinit var service: IMEService
    private lateinit var binding: MainLayoutBinding

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        preferences.edit().clear().commit()
        AppPreference.init(context)
        service = IMEService().apply { appPreference = AppPreference }
        Reflect.callInstanceMethod<Unit>(service, "attachBaseContext", ClassParameter.from(Context::class.java, context))
        Reflect.setField(service, "runtimeInputSharedPreferences", preferences)
        val inflater = LayoutInflater.from(ContextThemeWrapper(context, R.style.Theme_MarkdownKeyboard))
        binding = MainLayoutBinding.inflate(inflater)
        Reflect.setField(service, "mainLayoutBinding", binding)

        val template = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        val keyId = requireNotNull(template.keys.single { it.label == "q" }.keyId)
        val flickMap = mapOf(FlickDirection.TAP to FlickAction.Input("q"), FlickDirection.UP to FlickAction.Input("1"))
        val layout = template.updateKeyDataKeepingPlacement(keyId) {
            it.copy(keyType = KeyType.PETAL_FLICK, isFlickable = true, qwertyConfig = null)
        }.copy(flickKeyMaps = mapOf(keyId to listOf(flickMap)))
        val selected = CustomKeyboardLayout(
            layoutId = 1, name = "Custom QWERTY", columnCount = layout.columnCount, rowCount = layout.rowCount
        )
        service.keyboardRepository = spy(KeyboardRepository(mock())).also {
            doReturn(flowOf(layout)).whenever(it).getFullLayout(selected.layoutId)
        }
        Reflect.setField(service, "customLayouts", listOf(selected))
    }

    @After
    fun tearDown() {
        binding.keyboardView.cancelTenKeyScope()
        listOf("scope", "ioScope").forEach { Reflect.getField<CoroutineScope>(service, it).cancel() }
        Reflect.getField<ExecutorCoroutineDispatcher>(service, "kanaKanjiConversionDispatcher").close()
        preferences.edit().clear().commit()
        Dispatchers.resetMain()
    }

    @Test
    fun returningFromSumireRestoresCustomGuides() = runTest {
        showCustomKeyboardAlongside(KeyboardType.SUMIRE)

        service.switchNextKeyboard()
        assertMode(TenKeyQWERTYMode.Sumire)
        service.switchNextKeyboard()
        awaitCustomLayout()

        assertMode(TenKeyQWERTYMode.Custom)
        assertEquals("1", upGuide())

        AppPreference.flick_keymap_guide_layout = false
        AppPreference.sumire_keymap_guide_japanese = true
        syncRuntimeInputPreferences()
        service.switchNextKeyboard()
        service.switchNextKeyboard()
        awaitCustomLayout()
        assertNull(upGuide())
    }

    @Test
    fun returningFromQwertyRestoresCustomGuides() = runTest {
        showCustomKeyboardAlongside(KeyboardType.QWERTY)

        service.switchNextKeyboard()
        assertMode(TenKeyQWERTYMode.TenKeyQWERTY)
        service.switchNextKeyboard()
        awaitCustomLayout()
        assertEquals("1", upGuide())

        service.switchNextKeyboard()
        assertMode(TenKeyQWERTYMode.TenKeyQWERTY)
        // The IME also syncs preferences when the input view reopens on QWERTY.
        syncRuntimeInputPreferences()
        service.switchNextKeyboard()
        awaitCustomLayout()

        assertMode(TenKeyQWERTYMode.Custom)
        assertEquals("1", upGuide())
    }

    @Test
    fun pendingCustomLayoutDoesNotReplaceSumireAfterAnotherSwitch() = runTest {
        showCustomKeyboardAlongside(KeyboardType.SUMIRE)

        service.switchNextKeyboard()
        service.switchNextKeyboard()
        service.switchNextKeyboard()
        assertMode(TenKeyQWERTYMode.Sumire)
        val sumireKey = binding.customLayoutDefault.getChildAt(0)

        awaitCustomLayout()
        assertSame(sumireKey, binding.customLayoutDefault.getChildAt(0))
    }

    private suspend fun showCustomKeyboardAlongside(other: KeyboardType) {
        Reflect.setField(service, "keyboardOrder", listOf(KeyboardType.CUSTOM, other))
        Reflect.callInstanceMethod<Unit>(service, "showResolvedKeyboard",
            ClassParameter.from(KeyboardType::class.java, KeyboardType.CUSTOM))
        awaitCustomLayout()
        assertNull(upGuide())
        AppPreference.flick_keymap_guide_layout = true
        syncRuntimeInputPreferences()
        assertEquals("1", upGuide())
    }

    private fun syncRuntimeInputPreferences() =
        Reflect.callInstanceMethod<Unit>(service, "syncRuntimeInputPreferences")

    private suspend fun awaitCustomLayout() {
        Reflect.getField<Job>(service, "customKeyboardRenderJob").join()
    }

    private fun assertMode(expected: TenKeyQWERTYMode) {
        assertEquals(expected, Reflect.getField<MutableStateFlow<TenKeyQWERTYMode>>(service, "_tenKeyQWERTYMode").value)
    }

    private fun upGuide(): String? {
        val keyboard = binding.customLayoutDefault
        val q = (0 until keyboard.childCount).asSequence().map { keyboard.getChildAt(it) }
            .filterIsInstance<AutoSizeButton>().single { it.text.toString() == "q" }
        return Reflect.getField<AutoSizeButton.FlickGuideLabels?>(q, "flickGuideLabels")?.up
    }
}
