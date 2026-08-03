package com.kazumaproject.custom_keyboard.controller

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import android.widget.PopupWindow
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyboardPopupOverlayTest {

    @Test
    fun crossFlickControllerDoesNotRetainPopupWindowState() {
        val hasPopupWindowField = CrossFlickInputController::class.java.declaredFields.any {
            PopupWindow::class.java.isAssignableFrom(it.type)
        }

        assertFalse(hasPopupWindowField)
    }

    @Test
    fun visiblePopupIsPlacedAgainInTheSamePreDrawAsAnchorMovement() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val host = FrameLayout(activity)
        val anchor = View(activity)
        val popup = View(activity)
        activity.setContentView(host)
        host.addView(anchor, FrameLayout.LayoutParams(100, 60))
        host.measure(exactly(500), exactly(500))
        host.layout(0, 0, 500, 500)
        anchor.layout(40, 300, 140, 360)

        lateinit var overlay: KeyboardPopupOverlay
        overlay = KeyboardPopupOverlay {
            overlay.place(
                popupView = popup,
                left = anchor.left,
                top = anchor.top - 60,
                width = 100,
                height = 60
            )
        }

        assertTrue(
            overlay.show(
                anchor = anchor,
                preferredHost = host,
                popupView = popup,
                width = 100,
                height = 60
            )
        )
        assertEquals(240, popup.top)

        anchor.layout(40, 205, 140, 265)
        host.viewTreeObserver.dispatchOnPreDraw()

        assertEquals(145, popup.top)
        overlay.dismiss(popup)
        assertFalse(overlay.isShowing(popup))
    }

    private fun exactly(size: Int): Int =
        View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
}
