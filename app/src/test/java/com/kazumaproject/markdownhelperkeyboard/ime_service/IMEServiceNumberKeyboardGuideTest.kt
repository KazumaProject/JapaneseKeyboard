package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.KeyboardLayoutUsageMode
import com.kazumaproject.custom_keyboard.view.AutoSizeButton
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.databinding.FloatingKeyboardLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard.SplitKeyboardController
import com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard.SplitKeyboardSelection
import com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard.SplitSlot
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IMEServiceNumberKeyboardGuideTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences get() = PreferenceManager.getDefaultSharedPreferences(context)
    private lateinit var service: IMEService
    private lateinit var main: MainLayoutBinding
    private lateinit var floating: FloatingKeyboardLayoutBinding
    private val selected = CustomKeyboardLayout(
        layoutId = 1292,
        name = "Number custom layout",
        columnCount = 1,
        rowCount = 1,
        usageMode = KeyboardLayoutUsageMode.Number
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        preferences.edit().clear().commit()
        AppPreference.init(context)
        service = IMEService().apply { appPreference = AppPreference }
        ReflectionHelpers.callInstanceMethod<Unit>(service, "attachBaseContext",
            ClassParameter.from(Context::class.java, context))
        ReflectionHelpers.getField<MutableStateFlow<TenKeyQWERTYMode>>(
            service, "_tenKeyQWERTYMode").value = TenKeyQWERTYMode.Number
        ReflectionHelpers.setField(service, "customLayouts", listOf(selected))
        val layout = KeyboardLayout(
            keys = listOf(KeyData("あ", 0, 0, false, keyId = "kana",
                keyType = KeyType.TWO_STEP_FLICK)),
            flickKeyMaps = emptyMap(),
            columnCount = 1,
            rowCount = 1,
            twoStepFlickKeyMaps = mapOf("kana" to mapOf(
                TfbiFlickDirection.TAP to mapOf(TfbiFlickDirection.TAP to "あ"),
                TfbiFlickDirection.LEFT to mapOf(TfbiFlickDirection.TAP to "い"),
                TfbiFlickDirection.UP to mapOf(TfbiFlickDirection.TAP to "う")
            ))
        )
        service.keyboardRepository = spy(KeyboardRepository(mock())).also {
            doReturn(flowOf(layout)).whenever(it).getFullLayout(selected.layoutId)
        }
        val inflater = LayoutInflater.from(ContextThemeWrapper(context, R.style.Theme_MarkdownKeyboard))
        main = MainLayoutBinding.inflate(inflater)
        floating = FloatingKeyboardLayoutBinding.inflate(inflater)
        ReflectionHelpers.setField(service, "mainLayoutBinding", main)
        ReflectionHelpers.setField(service, "floatingKeyboardBinding", floating)
    }

    @After
    fun tearDown() {
        ReflectionHelpers.getField<CoroutineScope>(service, "scope").cancel()
        preferences.edit().clear().commit()
        Dispatchers.resetMain()
    }

    @Test
    fun numberCustomLayoutShowsConfiguredGuidesOnDockedAndFloatingViews() = runTest {
        ReflectionHelpers.setField(service, "customKeymapGuidePreference", true)
        for (view in views()) {
            renderNumberLayout(view)
            assertNotNull(guides(view))
            assertEquals("い", guides(view)?.left)
            assertEquals("う", guides(view)?.up)
        }
    }

    @Test
    fun numberCustomLayoutHonorsGuidePreferenceChangesWhileDisplayed() = runTest {
        for (view in views()) renderNumberLayout(view)
        views().forEach { assertNull(guides(it)) }

        for (enabled in listOf(true, false, true)) {
            AppPreference.flick_keymap_guide_layout = enabled
            ReflectionHelpers.callInstanceMethod<Unit>(service, "syncRuntimeInputPreferences")
            views().forEach { assertEquals(enabled, guides(it) != null) }
        }
    }

    @Test
    fun numberCustomLayoutHonorsGuidePreferenceChangesInBothSplitPanes() = runTest {
        val secondary = FloatingKeyboardLayoutBinding.inflate(LayoutInflater.from(floating.root.context))
        val panes = mapOf(SplitSlot.MAIN to floating, SplitSlot.SUB to secondary)
        ReflectionHelpers.setField(service, "splitController",
            SplitKeyboardController(floating.root.context, floating.root, {}, {}, {}))
        ReflectionHelpers.setField(service, "activeSplitSlot", SplitSlot.MAIN)

        for ((slot, binding) in panes) {
            val state = installNumberSplitInput(slot, binding)
            renderNumberLayout(binding.customLayoutFloating, state)
            assertNull(guides(binding.customLayoutFloating))
        }

        for (enabled in listOf(true, false)) {
            AppPreference.flick_keymap_guide_layout = enabled
            ReflectionHelpers.callInstanceMethod<Unit>(service, "syncRuntimeInputPreferences")
            for ((slot, binding) in panes) {
                assertEquals(slot.name, if (enabled) "い" else null, guides(binding.customLayoutFloating)?.left)
            }
        }
    }

    @Test
    fun builtInNumberLayoutClearsCustomGuidesAfterAssignmentIsRemoved() = runTest {
        ReflectionHelpers.setField(service, "customKeymapGuidePreference", true)
        for (view in views()) renderNumberLayout(view)

        ReflectionHelpers.setField(service, "customLayouts", emptyList<CustomKeyboardLayout>())
        for (view in views()) {
            renderNumberLayout(view)
            assertFalse(ReflectionHelpers.getField(view, "flickGuideEnabled"))
        }
    }

    private fun views() = listOf(main.customLayoutDefault, floating.customLayoutFloating)

    private suspend fun renderNumberLayout(view: FlickKeyboardView, splitState: Any? = null) {
        ReflectionHelpers.callInstanceMethod<Unit>(service, "setNumberLayoutTo",
            ClassParameter.from(FlickKeyboardView::class.java, view))
        val job = if (splitState == null) {
            ReflectionHelpers.getField<Job?>(service, "numberKeyboardRenderJob")
        } else {
            ReflectionHelpers.getField<Job?>(splitState, "layoutJob")
        }
        job?.join()
    }

    private fun installNumberSplitInput(slot: SplitSlot, binding: FloatingKeyboardLayoutBinding): Any {
        val type = Class.forName(IMEService::class.java.name + "\$SplitInputState")
        val constructor = type.declaredConstructors.single {
            it.parameterTypes.last().name.endsWith("DefaultConstructorMarker")
        }.apply { isAccessible = true }
        val args = constructor.parameterTypes.map<Class<*>, Any?> {
            when (it) {
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                else -> null
            }
        }.toTypedArray()
        arrayOf(slot, SplitKeyboardSelection(), binding, SuggestionAdapter(),
            TenKeyQWERTYMode.Number, InputMode.ModeNumber, false).copyInto(args)
        args[args.lastIndex - 1] = -128 // Use the defaults after the first seven arguments.
        return constructor.newInstance(*args).also { state ->
            ReflectionHelpers.getField<MutableMap<SplitSlot, Any>>(service, "splitInputs")[slot] = state
        }
    }

    private fun guides(view: FlickKeyboardView): AutoSizeButton.FlickGuideLabels? =
        ReflectionHelpers.getField(view.getChildAt(0), "flickGuideLabels")
}
