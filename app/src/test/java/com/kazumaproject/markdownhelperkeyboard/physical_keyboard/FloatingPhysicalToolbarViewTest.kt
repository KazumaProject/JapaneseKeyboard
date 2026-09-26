package com.kazumaproject.markdownhelperkeyboard.physical_keyboard

import android.view.MotionEvent
import android.view.View
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingPhysicalToolbarViewTest {
    private var downTime = 0L
    @Test
    fun tapInvokesButtonWithoutDragging() {
        val view = makeView()
        assertTrue(view.getChildAt(0).width > 24)
        assertTrue(view.getChildAt(0).height > 24)
        assertTrue(view.getChildAt(0).isClickable)
        var clicks = 0
        var drags = 0
        view.onModeClick = { clicks++ }
        view.onDrag = { _, _, _ -> drags++ }

        assertTrue(send(view, MotionEvent.ACTION_DOWN, 24f, 24f))
        assertTrue(view.getChildAt(0).isPressed)
        assertTrue(send(view, MotionEvent.ACTION_UP, 24f, 24f))
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, clicks)
        assertEquals(0, drags)
    }

    @Test
    fun dragCancelsButtonClick() {
        val view = makeView()
        var clicks = 0
        var dragStarted = 0
        var dragFinished = 0
        view.onModeClick = { clicks++ }
        view.onDragStart = { dragStarted++ }
        view.onDrag = { _, _, finished -> if (finished) dragFinished++ }

        send(view, MotionEvent.ACTION_DOWN, 24f, 24f)
        send(view, MotionEvent.ACTION_MOVE, 54f, 24f)
        send(view, MotionEvent.ACTION_UP, 54f, 24f)

        assertEquals(0, clicks)
        assertEquals(1, dragStarted)
        assertEquals(1, dragFinished)
    }

    @Test
    fun touchSlopUsesDistance() {
        assertFalse(isBeyondTouchSlop(6f, 8f, 10))
        assertTrue(isBeyondTouchSlop(7f, 8f, 10))
    }

    private fun makeView(): FloatingPhysicalToolbarView {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return FloatingPhysicalToolbarView(context).apply {
            render(PhysicalToolbarSettings(true, true, false, false), "あ")
            measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            layout(0, 0, measuredWidth, measuredHeight)
            Robolectric.buildActivity(Activity::class.java).setup().get().setContentView(this)
        }
    }

    private fun send(view: View, action: Int, x: Float, y: Float): Boolean {
        if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        val handled = view.dispatchTouchEvent(event)
        event.recycle()
        return handled
    }
}
