package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import android.content.Context
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ComposingGuideInputWindowTouchTest {
    @Test fun transferredGestureUsesPanelCoordinatesAndCannotReachKeysAfterLeavingThePanel() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = ComposingGuideWindow(context, { true }, {},
            { CandidatePanelColors.resolve(context) }, { 48 })
        val panel = mock<ComposingGuideView>()
        whenever(panel.isAttachedToWindow).thenReturn(true)
        whenever(panel.isShown).thenReturn(true)
        whenever(panel.width).thenReturn(160)
        whenever(panel.height).thenReturn(200)
        doAnswer { invocation ->
            invocation.getArgument<IntArray>(0).apply { this[0] = 40; this[1] = 100 }
            null
        }.whenever(panel).getLocationOnScreen(any())
        val received = mutableListOf<Pair<Float, Float>>()
        whenever(panel.dispatchTouchEvent(any())).thenAnswer { invocation ->
            val event = invocation.getArgument<MotionEvent>(0)
            received += event.x to event.y
            false
        }
        ComposingGuideWindow::class.java.getDeclaredField("guideView").apply {
            isAccessible = true
            set(controller, panel)
        }
        fun send(action: Int, x: Float, y: Float): Boolean {
            val event = MotionEvent.obtain(0, 0, action, x, y, 0)
            return try { controller.dispatchInputWindowTouch(event) } finally { event.recycle() }
        }
        try {
            assertFalse(send(MotionEvent.ACTION_DOWN, 10f, 20f))
            assertTrue(send(MotionEvent.ACTION_DOWN, 60f, 120f))
            assertTrue(send(MotionEvent.ACTION_MOVE, 300f, 500f))
            assertTrue(send(MotionEvent.ACTION_UP, 300f, 500f))
            assertEquals(listOf(20f to 20f, 260f to 400f, 260f to 400f), received)
            assertFalse(send(MotionEvent.ACTION_DOWN, 10f, 20f))
        } finally { controller.destroy() }
    }
}
