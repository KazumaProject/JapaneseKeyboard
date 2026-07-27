package com.kazumaproject.custom_keyboard.view

import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
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
        assertNotNull(view.findButtonWithText("ABC"))
        assertNull(view.findButtonWithText("a"))

        view.setFlickGuideEnabled(enabled = true, allowMultiCharacterLabels = true)
        assertNotNull(view.findButtonWithText("a"))
        assertNull(view.findButtonWithText("ABC"))
    }

    @Test
    fun sumireNumberGuide_replacesGroupedLabelWithTapCharacter() {
        val view = createView()
        val layout = createSumireLayout(KeyboardInputMode.SYMBOLS)

        view.setFlickGuideEnabled(enabled = false, allowMultiCharacterLabels = true)
        view.setKeyboard(layout)
        assertNotNull(view.findButtonWithText("1\n☆♪→"))

        view.setFlickGuideEnabled(enabled = true, allowMultiCharacterLabels = true)
        assertNotNull(view.findButtonWithText("1"))
        assertNull(view.findButtonWithText("1\n☆♪→"))
    }

    @Test
    fun customKeyboardPolicy_keepsGroupedLabelForBackwardCompatibility() {
        val view = createView()

        view.setFlickGuideEnabled(enabled = true, allowMultiCharacterLabels = false)
        view.setKeyboard(createSumireLayout(KeyboardInputMode.ENGLISH))

        assertNotNull(view.findButtonWithText("ABC"))
        assertNull(view.findButtonWithText("a"))
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

    private fun FlickKeyboardView.findButtonWithText(text: String): AutoSizeButton? {
        for (index in 0 until childCount) {
            val button = getChildAt(index) as? AutoSizeButton ?: continue
            if (button.text.toString() == text) return button
        }
        return null
    }
}
