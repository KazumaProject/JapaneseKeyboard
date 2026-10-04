package com.kazumaproject.custom_keyboard.view

import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlickKeyboardViewGuidePresentationTest {

    @Test
    fun sumireEnglishGuide_replacesGroupedLabelWithTapCharacter() {
        val view = createView()
        val layout = createSumireLayout(KeyboardInputMode.ENGLISH)

        view.setFlickGuideEnabled(enabled = false, allowMultiCharacterLabels = true)
        view.setKeyboard(layout)
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
        view.setKeyboard(layout)
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
        view.setKeyboard(layout)

        assertNotNull(view.findInputButtonWithText("ABC", layout))
        assertNull(view.findInputButtonWithText("a", layout))
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
