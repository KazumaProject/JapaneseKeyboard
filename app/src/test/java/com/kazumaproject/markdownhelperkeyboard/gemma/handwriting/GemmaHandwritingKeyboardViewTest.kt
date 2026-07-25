package com.kazumaproject.markdownhelperkeyboard.gemma.handwriting

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GemmaHandwritingKeyboardViewTest {
    @Test
    fun canvasUsesFullAvailableWidthAndControlsUseOneRow() {
        val view = createView()
        val width = 360
        val height = 280
        view.measure(exactly(width), exactly(height))
        view.layout(0, 0, width, height)

        val canvas = view.getChildAt(1) as HandwritingCanvasView
        val controls = view.getChildAt(2) as LinearLayout

        assertEquals(width - view.paddingLeft - view.paddingRight, canvas.width)
        assertEquals(LinearLayout.HORIZONTAL, controls.orientation)
        assertEquals(8, controls.childCount)
        assertTrue(controls.top >= canvas.bottom)
    }

    @Test
    fun cursorButtonsTapHorizontallyAndFlickVertically() {
        val view = createView()
        val cursorEvents = mutableListOf<Int>()
        view.onCursorKey = { cursorEvents += it }
        val controls = view.getChildAt(2) as LinearLayout
        val leftButton = controls.children().single {
            it.contentDescription == view.context.getString(
                R.string.gemma_handwriting_cursor_left,
            )
        }

        leftButton.performClick()
        dispatchVerticalFlick(leftButton, deltaY = -200f)
        dispatchVerticalFlick(leftButton, deltaY = 200f)

        assertEquals(
            listOf(
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
            ),
            cursorEvents,
        )
    }

    @Test
    fun recognitionRenderDoesNotDiscardStrokeCurrentlyBeingWritten() {
        val view = createView()
        val width = 360
        val height = 280
        view.measure(exactly(width), exactly(height))
        view.layout(0, 0, width, height)
        val canvas = view.getChildAt(1) as HandwritingCanvasView
        val store = HandwritingStrokeStore()
        var startedCount = 0
        var committedCount = 0
        view.onStrokeStarted = { startedCount += 1 }
        view.onStrokeCommitted = { committedCount += 1 }
        view.bindStore(store)

        dispatchTouch(canvas, MotionEvent.ACTION_DOWN, x = 30f, y = 40f)
        dispatchTouch(canvas, MotionEvent.ACTION_MOVE, x = 100f, y = 70f)

        // This is the same sequence the controller uses when recognition starts.
        view.bindStore(store)
        view.showRecognizing()
        view.refreshCanvas()

        dispatchTouch(canvas, MotionEvent.ACTION_MOVE, x = 220f, y = 100f)
        dispatchTouch(canvas, MotionEvent.ACTION_UP, x = 320f, y = 120f)

        assertEquals(1, startedCount)
        assertEquals(1, committedCount)
        assertEquals(1, store.strokes.size)
        val points = store.strokes.single().points
        assertTrue(points.size >= 4)
        assertTrue(points.first().x < 0.1f)
        assertTrue(points.last().x > 0.9f)
    }

    private fun createView(): GemmaHandwritingKeyboardView {
        val applicationContext = ApplicationProvider.getApplicationContext<Context>()
        val themedContext = ContextThemeWrapper(
            applicationContext,
            R.style.Theme_MarkdownKeyboard,
        )
        return GemmaHandwritingKeyboardView(themedContext)
    }

    private fun dispatchVerticalFlick(view: View, deltaY: Float) {
        val downTime = 1L
        MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            20f,
            20f,
            0,
        ).also { event ->
            view.dispatchTouchEvent(event)
            event.recycle()
        }
        MotionEvent.obtain(
            downTime,
            downTime + 20L,
            MotionEvent.ACTION_UP,
            20f,
            20f + deltaY,
            0,
        ).also { event ->
            view.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    private fun dispatchTouch(view: View, action: Int, x: Float, y: Float) {
        MotionEvent.obtain(
            1L,
            2L,
            action,
            x,
            y,
            0,
        ).also { event ->
            view.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    private fun LinearLayout.children(): Sequence<View> {
        return (0 until childCount).asSequence().map(::getChildAt)
    }

    private fun exactly(size: Int): Int {
        return View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
    }
}
