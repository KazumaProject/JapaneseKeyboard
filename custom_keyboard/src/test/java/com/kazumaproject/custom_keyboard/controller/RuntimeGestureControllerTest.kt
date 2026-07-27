package com.kazumaproject.custom_keyboard.controller

import android.app.Activity
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.flick.FlickThresholdShape
import com.kazumaproject.core.domain.flick.GestureSessionConfig
import com.kazumaproject.core.domain.flick.GestureSessionConfigSource
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.layout.SegmentedBackgroundDrawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RuntimeGestureControllerTest {

    @Test
    fun standardFlickUsesLatestConfigOnNextGestureButNotHalfwayThroughCurrentGesture() {
        var currentConfig = config(thresholdPx = 100f, timeoutMillis = 300L, revision = 1L)
        val controller = StandardFlickInputController(
            context = context(),
            gestureConfigSource = GestureSessionConfigSource { currentConfig }
        )
        val button = Button(context())
        val committed = mutableListOf<String>()
        controller.listener = object : StandardFlickInputController.StandardFlickListener {
            override fun onPress(character: String) = Unit
            override fun onFlick(character: String) {
                committed += character
            }
        }
        controller.attach(
            button,
            mapOf(
                FlickDirection.TAP to "tap",
                FlickDirection.UP_RIGHT_FAR to "right"
            ),
            SegmentedBackgroundDrawable(
                label = "a",
                baseColor = 0,
                highlightColor = 0,
                textColor = 0,
                cornerRadius = 0f,
                primaryTextSizePx = 20f,
                secondaryTextSizePx = 12f
            )
        )

        button.dispatch(MotionEvent.ACTION_DOWN, x = 10f, y = 10f, downTime = 1L)
        currentConfig = config(thresholdPx = 10f, timeoutMillis = 300L, revision = 2L)
        button.dispatch(MotionEvent.ACTION_UP, x = 60f, y = 10f, downTime = 1L)

        button.dispatch(MotionEvent.ACTION_DOWN, x = 10f, y = 10f, downTime = 10L)
        button.dispatch(MotionEvent.ACTION_UP, x = 60f, y = 10f, downTime = 10L)

        assertEquals(listOf("tap", "right"), committed)
    }

    @Test
    fun normalKeyLongPressUsesConfiguredTimeoutAndSnapshotsItAtDown() {
        var currentConfig = config(thresholdPx = 50f, timeoutMillis = 1_000L, revision = 1L)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val button = Button(activity)
        activity.setContentView(button)
        val events = mutableListOf<String>()
        val controller = TapLongPressInputController(
            GestureSessionConfigSource { currentConfig }
        )
        controller.attach(
            button,
            object : TapLongPressInputController.Listener {
                override fun onPress() {
                    events += "press"
                }

                override fun onTap() {
                    events += "tap"
                }

                override fun onLongPress() {
                    events += "long"
                }

                override fun onUpAfterLongPress() {
                    events += "upAfterLong"
                }

                override fun onLongPressCanceled() {
                    events += "cancelLong"
                }
            }
        )

        button.dispatch(MotionEvent.ACTION_DOWN, x = 10f, y = 10f, downTime = 1L)
        currentConfig = config(thresholdPx = 50f, timeoutMillis = 100L, revision = 2L)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(300L))
        assertFalse(events.contains("long"))
        button.dispatch(MotionEvent.ACTION_UP, x = 10f, y = 10f, downTime = 1L)
        assertTrue(events.contains("tap"))

        events.clear()
        button.dispatch(MotionEvent.ACTION_DOWN, x = 10f, y = 10f, downTime = 10L)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(99L))
        assertFalse(events.contains("long"))
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(2L))
        assertTrue(events.contains("long"))
        button.dispatch(MotionEvent.ACTION_UP, x = 10f, y = 10f, downTime = 10L)
        assertEquals(listOf("press", "long", "upAfterLong"), events)
    }

    @Test
    fun standardFlickUsesConfiguredThresholdShape() {
        var currentConfig = config(
            thresholdPx = 100f,
            timeoutMillis = 300L,
            revision = 1L,
            thresholdShape = FlickThresholdShape.Rectangular
        )
        val controller = StandardFlickInputController(
            context = context(),
            gestureConfigSource = GestureSessionConfigSource { currentConfig }
        )
        val button = Button(context())
        val committed = mutableListOf<String>()
        controller.listener = object : StandardFlickInputController.StandardFlickListener {
            override fun onPress(character: String) = Unit
            override fun onFlick(character: String) {
                committed += character
            }
        }
        controller.attach(
            button,
            mapOf(
                FlickDirection.TAP to "tap",
                FlickDirection.DOWN to "down"
            ),
            SegmentedBackgroundDrawable(
                label = "a",
                baseColor = 0,
                highlightColor = 0,
                textColor = 0,
                cornerRadius = 0f,
                primaryTextSizePx = 20f,
                secondaryTextSizePx = 12f
            )
        )

        button.dispatch(MotionEvent.ACTION_DOWN, x = 10f, y = 10f, downTime = 1L)
        button.dispatch(MotionEvent.ACTION_UP, x = 90f, y = 100f, downTime = 1L)

        currentConfig = currentConfig.copy(
            settingsRevision = 2L,
            flickThresholdShape = FlickThresholdShape.Radial
        )
        button.dispatch(MotionEvent.ACTION_DOWN, x = 10f, y = 10f, downTime = 10L)
        button.dispatch(MotionEvent.ACTION_UP, x = 90f, y = 100f, downTime = 10L)

        assertEquals(listOf("tap", "down"), committed)
    }

    private fun context(): Context {
        return ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
        )
    }

    private fun config(
        thresholdPx: Float,
        timeoutMillis: Long,
        revision: Long,
        thresholdShape: FlickThresholdShape = FlickThresholdShape.Radial
    ): GestureSessionConfig {
        return GestureSessionConfig(
            settingsRevision = revision,
            flickSensitivity = 100,
            flickThresholdPx = thresholdPx,
            longPressTimeoutMillis = timeoutMillis,
            flickThresholdShape = thresholdShape
        )
    }

    private fun View.dispatch(action: Int, x: Float, y: Float, downTime: Long) {
        val event = MotionEvent.obtain(
            downTime,
            downTime + 1L,
            action,
            x,
            y,
            0
        )
        dispatchTouchEvent(event)
        event.recycle()
    }
}
