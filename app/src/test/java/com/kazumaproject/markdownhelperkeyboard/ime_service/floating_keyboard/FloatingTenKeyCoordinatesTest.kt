package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_keyboard

import android.app.Activity
import android.view.MotionEvent
import android.widget.FrameLayout
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.tenkey.TenKey
import com.kazumaproject.core.domain.extensions.touchScreenCoordinates
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28, 30])
class FloatingTenKeyCoordinatesTest {
    @Test fun transformedTouchKeepsScreenCoordinates() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(R.style.Theme_MarkdownKeyboard)
        val parent = FrameLayout(activity).apply { scaleX = .6f; scaleY = .5f }
        val keyboard = TenKey(activity, Robolectric.buildAttributeSet().build())
        parent.addView(keyboard)
        activity.setContentView(parent)
        val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 120f, 220f, 0)
        // Dispatch into a transformed child changes local coordinates, not the raw screen position.
        event.offsetLocation(-50f, -100f)
        try {
            val point = keyboard.touchScreenCoordinates(event, 0)
            assertEquals(120f, point.first)
            assertEquals(220f, point.second)
        } finally {
            event.recycle()
            activity.finish()
        }
    }
}
