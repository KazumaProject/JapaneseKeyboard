package com.kazumaproject.custom_keyboard.view

import android.graphics.Color
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.custom_keyboard.data.TfbiGuideFingerPosition
import com.kazumaproject.custom_keyboard.data.TfbiGuidePopupState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TfbiGuidePopupViewTest {

    @Test
    fun popupStyleBackgroundAndTextOverrideKeyboardThemeColors() {
        val view = createView()

        view.setColors(
            backgroundColor = Color.RED,
            highlightedBackgroundColor = Color.BLUE,
            textColor = Color.WHITE
        )
        view.applyPopupViewStyle(
            PopupViewStyle(
                sizeScalePercent = 100,
                textSizeSp = 20f,
                backgroundColor = Color.GREEN,
                textColor = Color.BLACK
            )
        )

        assertEquals(Color.GREEN, privateColor(view, "popupBackgroundColor"))
        assertEquals(Color.BLUE, privateColor(view, "activeColor"))
        assertEquals(Color.BLACK, privateColor(view, "popupTextColor"))
    }

    @Test
    fun keyboardThemeColorsAreUsedWhenPopupStyleDoesNotSpecifyColors() {
        val view = createView()

        view.setColors(
            backgroundColor = Color.RED,
            highlightedBackgroundColor = Color.BLUE,
            textColor = Color.WHITE
        )
        view.applyPopupViewStyle(PopupViewStyle(100, 20f))

        assertEquals(Color.RED, privateColor(view, "popupBackgroundColor"))
        assertEquals(Color.BLUE, privateColor(view, "activeColor"))
        assertEquals(Color.WHITE, privateColor(view, "popupTextColor"))
    }

    @Test
    fun guideStoresTheCurrentPointerPositionInItsRenderState() {
        val view = createView()
        val position = TfbiGuideFingerPosition(x = 0.8f, y = 0.2f)

        view.setState(
            TfbiGuidePopupState(
                currentText = "あ",
                currentSlot = TfbiFlickDirection.TAP,
                fingerPosition = position
            )
        )

        val state = TfbiGuidePopupView::class.java.getDeclaredField("state").apply {
            isAccessible = true
        }.get(view) as TfbiGuidePopupState
        assertEquals(position, state.fingerPosition)
    }

    private fun createView(): TfbiGuidePopupView = TfbiGuidePopupView(
        ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
        )
    )

    private fun privateColor(view: TfbiGuidePopupView, name: String): Int {
        return TfbiGuidePopupView::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }.get(view) as Int
    }
}
