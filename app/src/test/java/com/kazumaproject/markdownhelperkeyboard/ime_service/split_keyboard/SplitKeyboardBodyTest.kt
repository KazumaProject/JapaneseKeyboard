package com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SplitKeyboardBodyTest {
    @Test fun independentlyScalesBothAxesAndMapsTouchesToOriginalBody() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val container = SplitKeyboardBody(activity, 240, 180)
        var x = -1f; var y = -1f
        val body = View(activity).apply { setOnTouchListener { _, event -> x = event.x; y = event.y; true } }
        container.addView(body)
        container.measure(View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY))
        container.layout(0, 0, 120, 80)
        assertEquals(240, body.width); assertEquals(180, body.height)
        assertEquals(.5f, body.scaleX, .0001f); assertEquals(80f / 180, body.scaleY, .0001f)
        val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 60f, 40f, 0)
        try { assertTrue(container.dispatchTouchEvent(event)) } finally { event.recycle() }
        assertEquals(120f, x, .001f); assertEquals(90f, y, .001f)
        activity.finish()
    }
}
