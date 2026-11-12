package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
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

    private suspend fun renderNumberLayout(view: FlickKeyboardView) {
        ReflectionHelpers.callInstanceMethod<Unit>(service, "setNumberLayoutTo",
            ClassParameter.from(FlickKeyboardView::class.java, view))
        ReflectionHelpers.getField<Job?>(service, "numberKeyboardRenderJob")?.join()
    }

    private fun guides(view: FlickKeyboardView): AutoSizeButton.FlickGuideLabels? =
        ReflectionHelpers.getField(view.getChildAt(0), "flickGuideLabels")
}
