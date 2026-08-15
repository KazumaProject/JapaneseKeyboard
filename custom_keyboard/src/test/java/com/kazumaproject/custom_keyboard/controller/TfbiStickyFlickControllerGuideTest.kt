package com.kazumaproject.custom_keyboard.controller

import android.app.Activity
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.widget.Button
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TfbiStickyFlickControllerGuideTest {

    @Test
    fun guideUsesTheSameGridForStickySecondStage() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val button = Button(
            ContextThemeWrapper(
                activity,
                com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
            )
        )
        activity.setContentView(button)
        button.measure(exactly(212), exactly(134))
        button.layout(0, 0, 212, 134)

        var result: Pair<TfbiFlickDirection, TfbiFlickDirection>? = null
        val controller = TfbiStickyFlickController(activity, flickSensitivity = 10f).apply {
            setPopupPresentationMode(
                com.kazumaproject.core.data.popup.TfbiPopupPresentationMode.GUIDE_ABOVE_KEY
            )
            listener = object : TfbiStickyFlickController.TfbiListener {
                override fun onPress(
                    first: TfbiFlickDirection,
                    second: TfbiFlickDirection
                ) = Unit

                override fun onFlick(
                    first: TfbiFlickDirection,
                    second: TfbiFlickDirection
                ) {
                    result = first to second
                }
            }
            attach(button) { first, second ->
                when (first to second) {
                    TfbiFlickDirection.LEFT to TfbiFlickDirection.TAP -> "し"
                    TfbiFlickDirection.LEFT to TfbiFlickDirection.DOWN -> "しょ"
                    TfbiFlickDirection.LEFT to TfbiFlickDirection.DOWN_LEFT -> "じ"
                    else -> ""
                }
            }
        }

        val centerX = button.width / 2f
        val centerY = button.height / 2f
        val bottomCenterX = button.width * 0.65f
        val bottomCenterY = button.height.toFloat()
        val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, centerX, centerY, 0)
        val firstMove = MotionEvent.obtain(0L, 10L, MotionEvent.ACTION_MOVE, 0f, centerY, 0)
        val secondMove = MotionEvent.obtain(
            0L,
            20L,
            MotionEvent.ACTION_MOVE,
            bottomCenterX,
            bottomCenterY,
            0
        )
        val up = MotionEvent.obtain(
            0L,
            30L,
            MotionEvent.ACTION_UP,
            bottomCenterX,
            bottomCenterY,
            0
        )
        try {
            assertNotNull(button.dispatchTouchEvent(down))
            assertNotNull(button.dispatchTouchEvent(firstMove))
            assertNotNull(button.dispatchTouchEvent(secondMove))
            assertNotNull(button.dispatchTouchEvent(up))
        } finally {
            down.recycle()
            firstMove.recycle()
            secondMove.recycle()
            up.recycle()
            controller.cancel()
        }

        assertEquals(
            TfbiFlickDirection.LEFT to TfbiFlickDirection.DOWN,
            result
        )
    }

    private fun exactly(size: Int): Int =
        android.view.View.MeasureSpec.makeMeasureSpec(size, android.view.View.MeasureSpec.EXACTLY)
}
