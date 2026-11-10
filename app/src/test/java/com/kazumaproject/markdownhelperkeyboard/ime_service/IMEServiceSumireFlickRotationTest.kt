package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.content.SharedPreferences
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.SumireFlickInputMode
import com.kazumaproject.custom_keyboard.data.TfbiFlickNode
import com.kazumaproject.custom_keyboard.data.TfbiFlickRotation
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FloatingKeyboardLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard.SplitKeyboardController
import com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard.SplitKeyboardSelection
import com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard.SplitSlot
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IMEServiceSumireFlickRotationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences get() = PreferenceManager.getDefaultSharedPreferences(context)
    private lateinit var service: IMEService

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        AppPreference.init(context)
        service = IMEService().apply { appPreference = AppPreference }
        ReflectionHelpers.callInstanceMethod<Unit>(service, "attachBaseContext",
            ClassParameter.from(Context::class.java, context))
    }

    @After
    fun clearPreferences() {
        preferences.edit().clear().commit()
    }

    @Test
    fun rotationPreferenceListenerUpdatesBothBuiltInTfbiStyles() {
        for (style in listOf("second-flick", "third-flick")) {
            ReflectionHelpers.setField(service, "sumireInputStyle", style)
            for ((rotation, direction) in listOf(
                TfbiFlickRotation.DEFAULT to TfbiFlickDirection.DOWN_LEFT,
                TfbiFlickRotation.CLOCKWISE to TfbiFlickDirection.UP_LEFT,
                TfbiFlickRotation.COUNTERCLOCKWISE to TfbiFlickDirection.DOWN_LEFT,
            )) {
                AppPreference.sumire_flick_rotation_preference = rotation
                notifyPreferenceChanged()
                val layout = ReflectionHelpers.callInstanceMethod<KeyboardLayout>(
                    service, "createSumireKeyboardLayout")
                val input = if (style == "second-flick") {
                    layout.twoStepFlickKeyMaps.getValue("か")
                        .getValue(TfbiFlickDirection.LEFT).getValue(direction)
                } else {
                    val submenu = layout.hierarchicalFlickMaps.getValue("か")
                        .normalMap.getValue(TfbiFlickDirection.LEFT) as TfbiFlickNode.SubMenu
                    (submenu.nextMap.getValue(direction) as TfbiFlickNode.Input).char
                }
                assertEquals("$style $rotation", "ぎ", input)
            }
        }
    }

    @Test
    fun unknownRestoredRotationReturnsRuntimeLayoutToDefault() {
        ReflectionHelpers.setField(service, "sumireInputStyle", "second-flick")
        AppPreference.sumire_flick_rotation_preference = TfbiFlickRotation.CLOCKWISE
        notifyPreferenceChanged()
        preferences.edit().putString(AppPreference.SUMIRE_FLICK_ROTATION_KEY, "unknown").commit()
        notifyPreferenceChanged()
        val layout = ReflectionHelpers.callInstanceMethod<KeyboardLayout>(
            service, "createSumireKeyboardLayout")
        assertEquals("ぎ", layout.twoStepFlickKeyMaps.getValue("か")
            .getValue(TfbiFlickDirection.LEFT).getValue(TfbiFlickDirection.DOWN_LEFT))
    }

    @Test
    fun inputModePreferenceListenerSelectsContractedVoicedOrAllCharacters() {
        ReflectionHelpers.setField(service, "sumireInputStyle", "third-flick")
        for (mode in SumireFlickInputMode.entries) {
            AppPreference.sumire_flick_input_mode_preference = mode
            notifyPreferenceChanged(AppPreference.SUMIRE_FLICK_INPUT_MODE_KEY)
            val layout = ReflectionHelpers.callInstanceMethod<KeyboardLayout>(
                service, "createSumireKeyboardLayout")
            val outputs = layout.hierarchicalFlickMaps.getValue("か").outputs()
            assertTrue(outputs.containsAll(listOf("か", "き", "く", "け", "こ")))
            assertEquals(mode != SumireFlickInputMode.CONTRACTED, "ぎ" in outputs)
            assertEquals(mode != SumireFlickInputMode.VOICED, "きゅ" in outputs)
            assertEquals(mode != SumireFlickInputMode.VOICED, "きゅう" in outputs)
        }
    }

    @Test
    fun changingInputModeOnFloatingSumireAlsoUpdatesCachedDockedLayout() {
        val (main, floating) = installSumireSurfaces("third-flick")
        ReflectionHelpers.setField(service, "isKeyboardFloatingMode", true)
        AppPreference.sumire_flick_input_mode_preference = SumireFlickInputMode.CONTRACTED
        notifyPreferenceChanged(AppPreference.SUMIRE_FLICK_INPUT_MODE_KEY)
        for (view in listOf(main.customLayoutDefault, floating.customLayoutFloating)) {
            val outputs = layoutOf(view).hierarchicalFlickMaps.getValue("か").outputs()
            assertTrue("Contracted sounds remain available", "きゅう" in outputs)
            assertFalse("Voiced kana are removed from the contracted preset", "ぎ" in outputs)
        }
        AppPreference.sumire_flick_input_mode_preference = SumireFlickInputMode.VOICED
        notifyPreferenceChanged(AppPreference.SUMIRE_FLICK_INPUT_MODE_KEY)
        for (view in listOf(main.customLayoutDefault, floating.customLayoutFloating)) {
            val outputs = layoutOf(view).hierarchicalFlickMaps.getValue("か").outputs()
            assertTrue("Voiced kana remain available", "ぎ" in outputs)
            assertFalse("Contracted sounds are removed from the voiced preset", "きゅう" in outputs)
        }
    }

    @Test
    fun changingRotationOnFloatingSumireAlsoUpdatesCachedDockedLayout() {
        val (main, floating) = installSumireSurfaces()
        assertKaIColumn(main.customLayoutDefault, TfbiFlickDirection.DOWN_LEFT)
        assertKaIColumn(floating.customLayoutFloating, TfbiFlickDirection.DOWN_LEFT)
        ReflectionHelpers.setField(service, "isKeyboardFloatingMode", true)

        AppPreference.sumire_flick_rotation_preference = TfbiFlickRotation.CLOCKWISE
        notifyPreferenceChanged()
        assertKaIColumn(floating.customLayoutFloating, TfbiFlickDirection.UP_LEFT)

        // Floating mode OFF renders the cached main surface without generating a new layout.
        ReflectionHelpers.setField(service, "isKeyboardFloatingMode", false)
        ReflectionHelpers.callInstanceMethod<Unit>(service, "renderCurrentKeyboardSurface")
        assertKaIColumn(main.customLayoutDefault, TfbiFlickDirection.UP_LEFT)
    }

    @Test
    fun changingRotationDoesNotReplaceAnActiveCustomLayout() {
        val (main, floating) = installSumireSurfaces()
        setPresentedMode(TenKeyQWERTYMode.Custom)
        main.customLayoutDefault.setKeyboard(KeyboardDefaultLayouts.defaultLayout())
        floating.customLayoutFloating.setKeyboard(KeyboardDefaultLayouts.defaultLayout())
        val mainLayout = layoutOf(main.customLayoutDefault)
        val floatingLayout = layoutOf(floating.customLayoutFloating)

        AppPreference.sumire_flick_rotation_preference = TfbiFlickRotation.CLOCKWISE
        notifyPreferenceChanged()

        assertSame(mainLayout, layoutOf(main.customLayoutDefault))
        assertSame(floatingLayout, layoutOf(floating.customLayoutFloating))
    }

    @Test
    fun splitRotationRefreshDefersTouchingKanaPaneAndKeepsOtherPaneAndInputSource() {
        val (main, floating) = installSumireSurfaces()
        val themed = main.root.context
        val kana = FloatingKeyboardLayoutBinding.inflate(LayoutInflater.from(themed))
        setSumireLayout(kana.customLayoutFloating)
        floating.customLayoutFloating.setKeyboard(KeyboardDefaultLayouts.defaultLayout())
        ReflectionHelpers.setField(service, "splitController",
            SplitKeyboardController(themed, View(themed), {}, {}, {}))
        val kanaState = installSplitInput(SplitSlot.MAIN, kana, KeyboardType.SUMIRE)
        val customState = installSplitInput(SplitSlot.SUB, floating, KeyboardType.CUSTOM)
        ReflectionHelpers.setField(kanaState, "touching", true)
        ReflectionHelpers.setField(customState, "mode", TenKeyQWERTYMode.Custom)
        ReflectionHelpers.setField(customState, "presentedMode", TenKeyQWERTYMode.Custom)
        ReflectionHelpers.setField(service, "activeSplitSlot", SplitSlot.SUB)
        ReflectionHelpers.setField(service, "floatingKeyboardBinding", floating)
        setPresentedMode(TenKeyQWERTYMode.Custom)
        val customLayout = layoutOf(floating.customLayoutFloating)
        val dockedLayout = layoutOf(main.customLayoutDefault)

        AppPreference.sumire_flick_rotation_preference = TfbiFlickRotation.CLOCKWISE
        notifyPreferenceChanged()

        assertKaIColumn(kana.customLayoutFloating, TfbiFlickDirection.DOWN_LEFT)
        val pending = ReflectionHelpers.getField<(() -> Unit)?>(kanaState, "pendingLayout")
        assertNotNull(pending)
        ReflectionHelpers.setField(kanaState, "touching", false)
        pending!!.invoke()
        assertKaIColumn(kana.customLayoutFloating, TfbiFlickDirection.UP_LEFT)
        assertSame(customLayout, layoutOf(floating.customLayoutFloating))
        assertSame(dockedLayout, layoutOf(main.customLayoutDefault))
        assertEquals(SplitSlot.SUB, ReflectionHelpers.getField<SplitSlot>(service, "activeSplitSlot"))
    }

    @Test
    fun highlightPreferenceRefreshesDockedAndFloatingSurfacesInEveryMode() {
        val (main, floating) = installSumireSurfaces("third-flick")
        ReflectionHelpers.setField(service, "sumireInputKeyLayoutType", "flick")
        ReflectionHelpers.setField(service, "isKeyboardFloatingMode", true)
        for (mode in KeyboardInputMode.entries) {
            ReflectionHelpers.setField(service, "customKeyboardMode", mode)
            for (enabled in listOf(true, false)) {
                AppPreference.sumire_mode_key_highlight_preference = enabled
                notifyPreferenceChanged(AppPreference.SUMIRE_MODE_KEY_HIGHLIGHT_KEY)
                for (view in listOf(main.customLayoutDefault, floating.customLayoutFloating)) {
                    assertEquals(if (enabled) 1 else 0, layoutOf(view).keys.count { it.isHiLighted })
                }
            }
        }
    }

    @Test
    fun splitHighlightRefreshIncludesEnglishAndNumbersAndDefersTouchingPane() {
        val (_, floating) = installSumireSurfaces("third-flick")
        ReflectionHelpers.setField(service, "sumireInputKeyLayoutType", "flick")
        ReflectionHelpers.setField(service, "splitController",
            SplitKeyboardController(floating.root.context, View(floating.root.context), {}, {}, {}))
        val state = installSplitInput(SplitSlot.MAIN, floating, KeyboardType.SUMIRE)
        for (mode in KeyboardInputMode.entries) {
            ReflectionHelpers.setField(state, "customMode", mode)
            for (enabled in listOf(true, false)) {
                ReflectionHelpers.setField(state, "touching", true)
                AppPreference.sumire_mode_key_highlight_preference = enabled
                notifyPreferenceChanged(AppPreference.SUMIRE_MODE_KEY_HIGHLIGHT_KEY)
                val pending = ReflectionHelpers.getField<(() -> Unit)?>(state, "pendingLayout")
                assertNotNull(pending)
                ReflectionHelpers.setField(state, "touching", false)
                pending!!.invoke()
                ReflectionHelpers.setField(state, "pendingLayout", null)
                assertEquals(if (enabled) 1 else 0,
                    layoutOf(floating.customLayoutFloating).keys.count { it.isHiLighted })
            }
        }
    }

    private fun installSumireSurfaces(
        style: String = "second-flick"
    ): Pair<MainLayoutBinding, FloatingKeyboardLayoutBinding> {
        val themed = ContextThemeWrapper(context, R.style.Theme_MarkdownKeyboard)
        val main = MainLayoutBinding.inflate(LayoutInflater.from(themed))
        val floating = FloatingKeyboardLayoutBinding.inflate(LayoutInflater.from(themed))
        ReflectionHelpers.setField(service, "mainLayoutBinding", main)
        ReflectionHelpers.setField(service, "floatingKeyboardBinding", floating)
        ReflectionHelpers.setField(service, "sumireInputStyle", style)
        setPresentedMode(TenKeyQWERTYMode.Sumire)
        setSumireLayout(main.customLayoutDefault)
        setSumireLayout(floating.customLayoutFloating)
        return main to floating
    }

    private fun setPresentedMode(mode: TenKeyQWERTYMode) {
        ReflectionHelpers.getField<MutableStateFlow<TenKeyQWERTYMode>>(
            service, "_tenKeyQWERTYMode").value = mode
    }

    private fun setSumireLayout(view: FlickKeyboardView) {
        ReflectionHelpers.callInstanceMethod<Unit>(service, "setSumireLayoutTo",
            ClassParameter.from(FlickKeyboardView::class.java, view))
    }

    private fun layoutOf(view: FlickKeyboardView): KeyboardLayout =
        ReflectionHelpers.getField(view, "currentLayout")

    private fun assertKaIColumn(view: FlickKeyboardView, direction: TfbiFlickDirection) {
        assertEquals("ぎ", layoutOf(view).twoStepFlickKeyMaps.getValue("か")
            .getValue(TfbiFlickDirection.LEFT).getValue(direction))
    }

    private fun installSplitInput(
        slot: SplitSlot,
        binding: FloatingKeyboardLayoutBinding,
        type: KeyboardType
    ): Any {
        val stateType = Class.forName(IMEService::class.java.name + "\$SplitInputState")
        val constructor = stateType.declaredConstructors.single {
            it.parameterTypes.last().name.endsWith("DefaultConstructorMarker")
        }.apply { isAccessible = true }
        val args = constructor.parameterTypes.map<Class<*>, Any?> {
            when (it) {
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                else -> null
            }
        }.toTypedArray()
        arrayOf(slot, SplitKeyboardSelection(type), binding, SuggestionAdapter(),
            TenKeyQWERTYMode.Sumire, InputMode.ModeJapanese, false).copyInto(args)
        args[args.lastIndex - 1] = -128
        return constructor.newInstance(*args).also { state ->
            ReflectionHelpers.getField<MutableMap<SplitSlot, Any>>(service, "splitInputs")[slot] = state
        }
    }

    private fun TfbiFlickNode.outputs(): Set<String> = when (this) {
        is TfbiFlickNode.Input -> setOf(char)
        is TfbiFlickNode.SubMenu -> nextMap.values.flatMap { it.outputs() }.toSet()
        is TfbiFlickNode.StatefulKey -> listOfNotNull(normalMap, dakutenMap, handakutenMap)
            .flatMap { map -> map.values.flatMap { it.outputs() } }.toSet()
    }

    private fun notifyPreferenceChanged(key: String = AppPreference.SUMIRE_FLICK_ROTATION_KEY) {
        val listener = ReflectionHelpers.getField<SharedPreferences.OnSharedPreferenceChangeListener>(
            service, "runtimeInputPreferenceListener")
        listener.onSharedPreferenceChanged(preferences, key)
    }
}
