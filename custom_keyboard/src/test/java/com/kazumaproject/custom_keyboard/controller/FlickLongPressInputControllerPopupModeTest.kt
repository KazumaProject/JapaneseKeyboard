package com.kazumaproject.custom_keyboard.controller

import android.app.Activity
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.widget.Button
import android.widget.FrameLayout
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.core.domain.flick.GestureSessionConfig
import com.kazumaproject.core.domain.flick.GestureSessionConfigSource
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.custom_keyboard.view.TfbiFlickPopupView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlickLongPressInputControllerPopupModeTest {

    @Test
    fun switchesToLongPressPanelOnlyAfterHoldAndBackToFlickWhenDirectionChanges() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val context = ContextThemeWrapper(
            activity,
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
        )
        val root = FrameLayout(context)
        activity.setContentView(root)
        activity.window.decorView.layout(0, 0, 1080, 2400)
        root.measure(exactly(1080), exactly(2400))
        root.layout(0, 0, 1080, 2400)
        val button = Button(context)
        root.addView(button, FrameLayout.LayoutParams(180, 150))
        button.layout(300, 1500, 480, 1650)

        val controller = FlickLongPressInputController(
            context,
            GestureSessionConfigSource {
                GestureSessionConfig(
                    settingsRevision = 1L,
                    flickSensitivity = 100,
                    flickThresholdPx = 20f,
                    longPressTimeoutMillis = 300L
                )
            }
        )
        controller.applyPopupViewStyle(PopupViewStyle(100, 20f, skinId = KeyboardSkinId.CUPERTINO_CLASSIC))
        controller.attach(
            button,
            normalMap = mapOf(TfbiFlickDirection.TAP to ".", TfbiFlickDirection.RIGHT to "う"),
            longPressMap = mapOf(TfbiFlickDirection.TAP to "hold")
        )

        button.dispatch(MotionEvent.ACTION_DOWN, 40f, 40f)
        assertEquals(TfbiFlickPopupView.PresentationMode.FLICK, presentationMode(controller))

        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(350))
        assertEquals(TfbiFlickPopupView.PresentationMode.LONG_PRESS, presentationMode(controller))

        button.dispatch(MotionEvent.ACTION_MOVE, 140f, 40f)
        assertEquals(TfbiFlickPopupView.PresentationMode.FLICK, presentationMode(controller))

        button.dispatch(MotionEvent.ACTION_UP, 140f, 40f)
        controller.cancel()
        activity.finish()
    }

    private fun presentationMode(controller: FlickLongPressInputController): TfbiFlickPopupView.PresentationMode {
        val popup = FlickLongPressInputController::class.java.getDeclaredField("popupView")
            .apply { isAccessible = true }
            .get(controller) as TfbiFlickPopupView
        return TfbiFlickPopupView::class.java.getDeclaredField("presentationMode")
            .apply { isAccessible = true }
            .get(popup) as TfbiFlickPopupView.PresentationMode
    }

    private fun exactly(size: Int) = android.view.View.MeasureSpec.makeMeasureSpec(size, android.view.View.MeasureSpec.EXACTLY)

    private fun android.view.View.dispatch(action: Int, x: Float, y: Float) {
        val now = android.os.SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now + 1, action, x, y, 0)
        dispatchTouchEvent(event)
        event.recycle()
    }
}
