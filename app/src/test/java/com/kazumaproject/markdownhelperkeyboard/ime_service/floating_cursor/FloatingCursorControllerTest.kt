package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_cursor

import android.content.Context
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CandidatePanelColors
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingCursorControllerTest {
    @Test fun interruptedTransferredGestureIsDrainedThroughItsRelease() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var cancellations = 0
        val controller = FloatingCursorController(context, { true }, { CandidatePanelColors.resolve(context) }, {}, {}, { cancellations++ })
        fun send(action: Int): Boolean {
            val event = MotionEvent.obtain(0, 0, action, 0f, 0f, 0)
            return try { controller.dispatchInputWindowTouch(event) } finally { event.recycle() }
        }
        try {
            ReflectionHelpers.setField(controller, "routingTouch", true)
            controller.stop()
            assertEquals(1, cancellations)
            assertTrue(send(MotionEvent.ACTION_MOVE))
            assertTrue(send(MotionEvent.ACTION_UP))
            assertFalse(send(MotionEvent.ACTION_MOVE))
        } finally { controller.destroy() }
    }
}
