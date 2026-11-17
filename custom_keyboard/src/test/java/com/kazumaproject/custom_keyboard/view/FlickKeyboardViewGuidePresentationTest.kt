package com.kazumaproject.custom_keyboard.view

import com.kazumaproject.custom_keyboard.data.KeyboardLayoutOrigin
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.PopupAction
import com.kazumaproject.custom_keyboard.data.PopupActionType
import com.kazumaproject.custom_keyboard.data.PopupBounds
import com.kazumaproject.custom_keyboard.data.PopupKeyConfig
import com.kazumaproject.custom_keyboard.data.PopupMap
import com.kazumaproject.custom_keyboard.data.PopupRegion
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlickKeyboardViewGuidePresentationTest {

    @Test
    fun sumireEnglishGuide_replacesGroupedLabelWithTapCharacter() {
        val view = createView()
        val layout = createSumireLayout(KeyboardInputMode.ENGLISH)

        view.setFlickGuideEnabled(enabled = false, allowMultiCharacterLabels = true)
        view.setKeyboard(layout, origin = KeyboardLayoutOrigin.BUILT_IN)
        assertNotNull(view.findInputButtonWithText("ABC", layout))
        assertNull(view.findInputButtonWithText("a", layout))

        view.setFlickGuideEnabled(enabled = true, allowMultiCharacterLabels = true)
        assertNotNull(view.findInputButtonWithText("a", layout))
        assertNull(view.findInputButtonWithText("ABC", layout))
    }

    @Test
    fun sumireNumberGuide_replacesGroupedLabelWithTapCharacter() {
        val view = createView()
        val layout = createSumireLayout(KeyboardInputMode.SYMBOLS)

        view.setFlickGuideEnabled(enabled = false, allowMultiCharacterLabels = true)
        view.setKeyboard(layout, origin = KeyboardLayoutOrigin.BUILT_IN)
        assertNotNull(view.findInputButtonWithText("1\n☆♪→", layout))

        view.setFlickGuideEnabled(enabled = true, allowMultiCharacterLabels = true)
        assertNotNull(view.findInputButtonWithText("1", layout))
        assertNull(view.findInputButtonWithText("1\n☆♪→", layout))
    }

    @Test
    fun customKeyboardPolicy_keepsGroupedLabelForBackwardCompatibility() {
        val view = createView()

        view.setFlickGuideEnabled(enabled = true, allowMultiCharacterLabels = false)
        val layout = createSumireLayout(KeyboardInputMode.ENGLISH)
        view.setKeyboard(layout, origin = KeyboardLayoutOrigin.BUILT_IN)

        assertNotNull(view.findInputButtonWithText("ABC", layout))
        assertNull(view.findInputButtonWithText("a", layout))
    }

    @Test
    fun customFreeFlickShowsStartMapGuidesWhenEnabled() {
        val start = PopupMap("start", "Start", "タップ", listOf(
            PopupRegion("far", PopupBounds(1.5f, -.5f, 2.5f, .5f), PopupAction(text = "遠い")),
            PopupRegion("switch", PopupBounds(.5f, -.5f, 1.5f, .5f),
                PopupAction(PopupActionType.SWITCH, targetMapId = "next"), label = "切り替え"),
            PopupRegion("diagonal", PopupBounds(-1.5f, -1.5f, -.5f, -.5f), PopupAction(text = "😀入力")),
            PopupRegion("empty", PopupBounds(-.5f, -1.5f, .5f, -.5f), PopupAction(), label = "未設定"),
            PopupRegion("back", PopupBounds(-.5f, .5f, .5f, 1.5f), PopupAction(PopupActionType.BACK))
        ))
        val next = PopupMap("next", "Next", "次", listOf(
            PopupRegion("text", PopupBounds(-1.5f, -.5f, -.5f, .5f), PopupAction(text = "左"))
        ))
        val key = KeyData("FREE", 0, 0, true, KeyAction.Text(start.tapText),
            keyType = KeyType.FREE_FLICK, popupMapConfig = PopupKeyConfig("free", start.id, listOf(start, next)))
        val layout = KeyboardLayout(listOf(key), emptyMap(), columnCount = 1, rowCount = 1)
        val view = createView()
        view.setKeyboard(layout, origin = KeyboardLayoutOrigin.CUSTOM)

        fun guides(): AutoSizeButton.FlickGuideLabels? = ReflectionHelpers.getField(
            requireNotNull(view.findInputButtonWithText("FREE", layout)), "flickGuideLabels"
        )

        assertNull(guides())
        view.setFlickGuideMaxCodePoints(2)
        view.setFlickGuideEnabled(true)
        assertEquals(AutoSizeButton.FlickGuideLabels(tap = "タッ", right = "切り", upLeft = "😀入"), guides())
        view.setFlickGuideEnabled(false)
        assertNull(guides())
    }

    private fun createView(): FlickKeyboardView {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val themedContext = ContextThemeWrapper(
            context,
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
        )
        return FlickKeyboardView(themedContext)
    }

    private fun createSumireLayout(mode: KeyboardInputMode) =
        KeyboardDefaultLayouts.createFinalLayout(
            mode = mode,
            dynamicKeyStates = emptyMap(),
            inputLayoutType = "flick",
            inputStyle = "default"
        )

    private fun FlickKeyboardView.findInputButtonWithText(text: String, layout: KeyboardLayout): AutoSizeButton? {
        for (index in 0 until childCount) {
            val key = (layout.items[index] as? KeyItem)?.keyData ?: continue
            if (key.isSpecialKey) continue
            val button = getChildAt(index) as? AutoSizeButton ?: continue
            if (button.text.toString() == text) return button
        }
        return null
    }
}
