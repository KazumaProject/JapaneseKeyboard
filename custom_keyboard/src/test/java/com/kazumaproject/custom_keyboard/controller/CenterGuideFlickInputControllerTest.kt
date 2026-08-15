package com.kazumaproject.custom_keyboard.controller

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.widget.Button
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.flick.FlickThresholdShape
import com.kazumaproject.core.domain.flick.GestureSessionConfig
import com.kazumaproject.core.domain.flick.GestureSessionConfigSource
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CenterGuideFlickInputControllerTest {

    @Test
    fun edgeTouchStillUsesMovementFromTheVirtualCenterForDirectionSelection() {
        val controller = CenterGuideFlickInputController(
            context = context(),
            gestureConfigSource = GestureSessionConfigSource {
                config(thresholdPx = 20f)
            }
        )
        val button = Button(context())
        val commits = mutableListOf<Pair<String, Boolean>>()
        controller.listener = object : CenterGuideFlickInputController.Listener {
            override fun onPress(character: String) = Unit

            override fun onCommit(character: String, isFlick: Boolean) {
                commits += character to isFlick
            }
        }
        controller.attach(
            view = button,
            map = mapOf(
                com.kazumaproject.custom_keyboard.data.FlickDirection.TAP to "tap",
                com.kazumaproject.custom_keyboard.data.FlickDirection.UP_RIGHT_FAR to "right"
            )
        )
        button.layout(0, 0, 100, 80)

        dispatch(button, MotionEvent.ACTION_DOWN, 5f, 40f, 1L)
        dispatch(button, MotionEvent.ACTION_MOVE, 35f, 40f, 1L)
        dispatch(button, MotionEvent.ACTION_UP, 35f, 40f, 1L)

        assertEquals(listOf("right" to true), commits)
    }

    @Test
    fun fingerPositionAlwaysStartsAtTheKeyCenter() {
        assertEquals(
            0.5f,
            resolveCenterGuideFingerPosition(100, 80, 0f, 0f)!!.x,
            0.001f
        )
        assertEquals(
            0.5f,
            resolveCenterGuideFingerPosition(100, 80, 0f, 0f)!!.y,
            0.001f
        )
    }

    @Test
    fun fingerPositionFollowsDisplacementAndClampsToTheKeyBounds() {
        val position = resolveCenterGuideFingerPosition(100, 80, 30f, -20f)
        assertNotNull(position)
        assertEquals(0.8f, position!!.x, 0.001f)
        assertEquals(0.25f, position.y, 0.001f)

        val clamped = resolveCenterGuideFingerPosition(100, 80, 200f, -200f)
        assertEquals(1f, clamped!!.x, 0.001f)
        assertEquals(0f, clamped.y, 0.001f)
    }

    @Test
    fun directionUsesTheSharedThresholdAndDominantAxis() {
        val config = config(thresholdPx = 30f)

        assertEquals(
            TfbiFlickDirection.TAP,
            resolveCenterGuideDirection(20f, 0f, config)
        )
        assertEquals(
            TfbiFlickDirection.RIGHT,
            resolveCenterGuideDirection(40f, 10f, config)
        )
        assertEquals(
            TfbiFlickDirection.UP,
            resolveCenterGuideDirection(10f, -40f, config)
        )
    }

    @Test
    fun rectangularThresholdMatchesOtherFlickControllers() {
        val config = config(
            thresholdPx = 30f,
            thresholdShape = FlickThresholdShape.Rectangular
        )

        assertEquals(
            TfbiFlickDirection.DOWN,
            resolveCenterGuideDirection(0f, 31f, config)
        )
    }

    private fun config(
        thresholdPx: Float,
        thresholdShape: FlickThresholdShape = FlickThresholdShape.Radial
    ) = GestureSessionConfig(
        settingsRevision = 1L,
        flickSensitivity = 100,
        flickThresholdPx = thresholdPx,
        longPressTimeoutMillis = 500L,
        flickThresholdShape = thresholdShape
    )

    private fun context(): Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
    )

    private fun dispatch(
        button: Button,
        action: Int,
        x: Float,
        y: Float,
        downTime: Long
    ) {
        val event = MotionEvent.obtain(downTime, downTime + 1L, action, x, y, 0)
        button.dispatchTouchEvent(event)
        event.recycle()
    }
}
