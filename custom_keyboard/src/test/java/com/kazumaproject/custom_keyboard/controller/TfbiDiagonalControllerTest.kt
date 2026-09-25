package com.kazumaproject.custom_keyboard.controller

import android.app.Activity
import android.view.MotionEvent
import android.widget.Button
import com.kazumaproject.core.domain.flick.GestureSessionConfig
import com.kazumaproject.core.domain.flick.GestureSessionConfigSource
import com.kazumaproject.core.domain.flick.TfbiDiagonalRecognitionMode
import com.kazumaproject.custom_keyboard.data.TfbiFlickNode
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.custom_keyboard.view.TfbiInputController
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TfbiDiagonalControllerTest {
    @Test
    fun twoStepRequiresPerpendicularTravelOnlyInStableMode() {
        assertEquals("え", typeTwoStep(TfbiDiagonalRecognitionMode.STABLE, 68f))
        assertEquals("ぇ", typeTwoStep(TfbiDiagonalRecognitionMode.STABLE, 75f))
        assertEquals("ぇ", typeTwoStep(TfbiDiagonalRecognitionMode.LEGACY, 68f))
    }



    @Test
    fun stickyTwoStepUsesTheSameTurnRequirement() {
        assertEquals("え", typeStickyTwoStep(TfbiDiagonalRecognitionMode.STABLE, 68f))
        assertEquals("ぇ", typeStickyTwoStep(TfbiDiagonalRecognitionMode.STABLE, 75f))
        assertEquals("ぇ", typeStickyTwoStep(TfbiDiagonalRecognitionMode.LEGACY, 68f))
    }

    private fun typeTwoStep(mode: TfbiDiagonalRecognitionMode, finalY: Float): String {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val button = Button(activity)
        activity.setContentView(button)
        var output = ""
        val controller = TfbiInputController(activity, config(mode)).apply {
            listener = object : TfbiInputController.TfbiListener {
                override fun onPress(first: TfbiFlickDirection, second: TfbiFlickDirection) = Unit
                override fun onFlick(first: TfbiFlickDirection, second: TfbiFlickDirection) {
                    output = when (second) {
                        TfbiFlickDirection.RIGHT -> "え"
                        TfbiFlickDirection.DOWN_RIGHT -> "ぇ"
                        else -> ""
                    }
                }
            }
            attach(button, provider = { first, second ->
                if (first != TfbiFlickDirection.RIGHT) "" else when (second) {
                    TfbiFlickDirection.TAP, TfbiFlickDirection.RIGHT -> "え"
                    TfbiFlickDirection.DOWN_RIGHT -> "ぇ"
                    else -> ""
                }
            })
        }
        try {
            button.dispatch(MotionEvent.ACTION_DOWN, 50f, 50f, 0L)
            button.dispatch(MotionEvent.ACTION_MOVE, 75f, 50f, 10L)
            button.dispatch(MotionEvent.ACTION_MOVE, 125f, finalY, 20L)
            button.dispatch(MotionEvent.ACTION_UP, 125f, finalY, 30L)
        } finally {
            controller.cancel()
        }
        return output
    }



    private fun typeStickyTwoStep(mode: TfbiDiagonalRecognitionMode, finalY: Float): String {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val button = Button(activity)
        activity.setContentView(button)
        var output = ""
        val controller = TfbiStickyFlickController(activity, config(mode)).apply {
            listener = object : TfbiStickyFlickController.TfbiListener {
                override fun onPress(first: TfbiFlickDirection, second: TfbiFlickDirection) = Unit
                override fun onFlick(first: TfbiFlickDirection, second: TfbiFlickDirection) {
                    output = when (second) {
                        TfbiFlickDirection.RIGHT -> "え"
                        TfbiFlickDirection.DOWN_RIGHT -> "ぇ"
                        else -> ""
                    }
                }
            }
            attach(button) { first, second ->
                if (first != TfbiFlickDirection.RIGHT) "" else when (second) {
                    TfbiFlickDirection.TAP, TfbiFlickDirection.RIGHT -> "え"
                    TfbiFlickDirection.DOWN_RIGHT -> "ぇ"
                    else -> ""
                }
            }
        }
        try {
            button.dispatch(MotionEvent.ACTION_DOWN, 50f, 50f, 0L)
            button.dispatch(MotionEvent.ACTION_MOVE, 75f, 50f, 10L)
            button.dispatch(MotionEvent.ACTION_MOVE, 125f, finalY, 20L)
            button.dispatch(MotionEvent.ACTION_UP, 125f, finalY, 30L)
        } finally {
            controller.cancel()
        }
        return output
    }

    private fun config(mode: TfbiDiagonalRecognitionMode) = GestureSessionConfigSource {
        GestureSessionConfig(
            settingsRevision = 0L,
            flickSensitivity = 100,
            flickThresholdPx = 20f,
            longPressTimeoutMillis = 300L,
            tfbiDiagonalRecognitionMode = mode
        )
    }

    private fun Button.dispatch(action: Int, x: Float, y: Float, time: Long) {
        val event = MotionEvent.obtain(0L, time, action, x, y, 0)
        try {
            dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }
}
