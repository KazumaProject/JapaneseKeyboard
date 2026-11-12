package com.kazumaproject.custom_keyboard.view

import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlickKeyboardViewReuseTest {
    @Test
    fun equivalentLayoutReusesExistingKeyViews() {
        val view = createView()
        view.setKeyboard(createLayout())
        val firstKey = view.getChildAt(0)

        view.setKeyboard(createLayout())

        assertSame(firstKey, view.getChildAt(0))
    }

    @Test
    fun renderingPreferenceChangeStillRebuildsKeyViews() {
        val view = createView()
        view.setKeyboard(createLayout())
        val firstKey = view.getChildAt(0)

        view.setFlickGuideEnabled(enabled = true, allowMultiCharacterLabels = true)

        assertNotSame(firstKey, view.getChildAt(0))
    }

    private fun createView(): FlickKeyboardView {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return FlickKeyboardView(
            ContextThemeWrapper(
                context,
                com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar,
            ),
        )
    }

    private fun createLayout() = KeyboardDefaultLayouts.createFinalLayout(
        mode = KeyboardInputMode.HIRAGANA,
        dynamicKeyStates = emptyMap(),
        inputLayoutType = "toggle",
        inputStyle = "default",
    )
}
