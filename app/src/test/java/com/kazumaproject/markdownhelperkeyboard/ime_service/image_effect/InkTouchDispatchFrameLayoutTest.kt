package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InkTouchDispatchFrameLayoutTest {

    @Test
    fun listenerReceivesEventBeforeNormalDispatchContinues() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = InkTouchDispatchFrameLayout(context)
        val child = RecordingChildView(context)
        val calls = mutableListOf<String>()
        root.addView(
            child,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        layoutRoot(root, child)
        root.inkMotionEventListener = {
            calls.add("listener")
        }
        child.onDispatch = {
            calls.add("child")
        }

        val handled = root.dispatchTouchEvent(event())

        assertTrue(handled)
        assertEquals(listOf("listener", "child"), calls)
    }

    @Test
    fun listenerFailureDoesNotStopNormalDispatch() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = InkTouchDispatchFrameLayout(context)
        val child = RecordingChildView(context)
        root.addView(
            child,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        layoutRoot(root, child)
        root.inkMotionEventListener = {
            error("effect failed")
        }

        val handled = root.dispatchTouchEvent(event())

        assertTrue(handled)
        assertTrue(child.received)
    }

    @Test
    fun excludedOverlayUsesCurrentRootCoordinatesForTheWholeGesture() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = InkTouchDispatchFrameLayout(context)
        val keyboard = RecordingChildView(context)
        val candidate = RecordingChildView(context)
        val actions = mutableListOf<Int>()
        root.addView(keyboard, FrameLayout.LayoutParams(100, 100))
        root.addView(candidate, FrameLayout.LayoutParams(20, 20).apply { leftMargin = 40; topMargin = 40 })
        root.measure(View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 100, 100)
        root.fallbackTouchTargetProvider = { keyboard }
        root.fallbackTouchExclusion = { x, y -> x in 40f..60f && y in 40f..60f }
        candidate.onEvent = { actions += it.actionMasked }
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(0L, 1L, action, 50f, 50f, 0)
            // Raw coordinates stay at (50, 50), while local coordinates belong to a stale layout.
            event.offsetLocation(100f, 100f)
            try { assertTrue(root.dispatchTouchEvent(event)) } finally { event.recycle() }
        }
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), actions)
        assertTrue(!keyboard.received)
    }

    private fun event(): MotionEvent {
        return MotionEvent.obtain(
            0L,
            0L,
            MotionEvent.ACTION_DOWN,
            10f,
            10f,
            0
        )
    }

    private fun layoutRoot(root: InkTouchDispatchFrameLayout, child: View) {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY)
        root.measure(widthSpec, heightSpec)
        root.layout(0, 0, 100, 100)
        child.layout(0, 0, 100, 100)
    }

    private class RecordingChildView(context: Context) : View(context) {
        var received = false
        var onDispatch: (() -> Unit)? = null
        var onEvent: ((MotionEvent) -> Unit)? = null

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            received = true
            onDispatch?.invoke()
            onEvent?.invoke(event)
            return true
        }
    }
}
