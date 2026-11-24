package com.kazumaproject.custom_keyboard.controller

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.Button
import android.widget.FrameLayout
import com.kazumaproject.core.data.popup.TfbiPopupPresentationMode
import com.kazumaproject.core.domain.flick.GestureSessionConfig
import com.kazumaproject.core.domain.flick.GestureSessionConfigSource
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection.RIGHT
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection.TAP
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection.UP
import com.kazumaproject.custom_keyboard.view.TfbiInputController
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TfbiInputControllerLongPressTest {
    @Test
    fun returningToCenterAfterHoldingCommitsTheCenterCharacter() {
        for (presentation in TfbiPopupPresentationMode.entries) {
            assertEquals(presentation.name, "あ", flickAfterReturning(presentation, flickRight = false))
        }
    }

    @Test
    fun returningToCenterKeepsLongPressOnlyDirectionsAvailable() {
        for (presentation in TfbiPopupPresentationMode.entries) {
            assertEquals(presentation.name, "→", flickAfterReturning(presentation, flickRight = true))
        }
    }

    private fun flickAfterReturning(
        presentation: TfbiPopupPresentationMode,
        flickRight: Boolean
    ): String {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val key = Button(activity)
        activity.setContentView(FrameLayout(activity).apply {
            addView(key, FrameLayout.LayoutParams(240, 240))
        })
        shadowOf(Looper.getMainLooper()).idle()
        fun normal(first: TfbiFlickDirection, second: TfbiFlickDirection): String = when {
            first == TAP && second == TAP -> "あ"
            first == UP && (second == TAP || second == UP) -> "う"
            else -> ""
        }
        fun longPress(first: TfbiFlickDirection, second: TfbiFlickDirection): String =
            if (first == RIGHT && second == RIGHT) "→" else ""

        var committed = ""
        var selection: Triple<TfbiFlickDirection, TfbiFlickDirection, Boolean>? = null
        val controller = TfbiInputController(activity, GestureSessionConfigSource {
            GestureSessionConfig(0L, 100, 30f, 300L)
        }).apply {
            setPopupPresentationMode(presentation)
            listener = object : TfbiInputController.TfbiListener {
                override fun onPress(first: TfbiFlickDirection, second: TfbiFlickDirection) = Unit

                override fun onSelectionChanged(
                    first: TfbiFlickDirection,
                    second: TfbiFlickDirection,
                    isLongPress: Boolean
                ) {
                    selection = Triple(first, second, isLongPress)
                }

                override fun onLongPressFlick(first: TfbiFlickDirection, second: TfbiFlickDirection): Boolean {
                    val output = longPress(first, second)
                    if (output.isEmpty()) return false
                    committed = output
                    return true
                }

                override fun onFlick(first: TfbiFlickDirection, second: TfbiFlickDirection) {
                    committed = normal(first, second)
                }
            }
            attach(key, ::normal, ::longPress)
        }

        val downTime = SystemClock.uptimeMillis()
        fun touch(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            try {
                key.dispatchTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
        try {
            touch(MotionEvent.ACTION_DOWN, 120f, 120f)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(301))
            assertEquals(presentation.name, Triple(TAP, TAP, true), selection)
            touch(MotionEvent.ACTION_MOVE, 120f, 20f)
            assertEquals(presentation.name, Triple(UP, UP, true), selection)
            touch(MotionEvent.ACTION_MOVE, 120f, 120f)
            assertEquals(presentation.name, Triple(TAP, TAP, true), selection)
            if (flickRight) {
                touch(MotionEvent.ACTION_MOVE, 220f, 120f)
                assertEquals(Triple(RIGHT, RIGHT, true), selection)
            }
            touch(MotionEvent.ACTION_UP, if (flickRight) 220f else 120f, 120f)
        } finally {
            controller.cancel()
        }
        return committed
    }
}
