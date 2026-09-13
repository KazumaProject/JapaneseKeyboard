package com.kazumaproject.markdownhelperkeyboard.ime_service.dynamic_orbit

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import com.kazumaproject.markdownhelperkeyboard.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DynamicOrbitViewTest {
    @Test fun downOwnershipHistoryReleaseAndHiddenViewCannotLeakInput() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val view = DynamicOrbitView(controller.get())
        controller.get().setContentView(view)
        val density = view.resources.displayMetrics.density
        val width = (320 * density).toInt(); val height = (280 * density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        val area = view.findViewById<View>(R.id.orbit_touch_area)
        val output = mutableListOf<Char>()
        view.onLetter = { output += it }
        fun event(action: Int, x: Float, y: Float) {
            MotionEvent.obtain(1, 2, action, x * density, y * density, 0).also { area.dispatchTouchEvent(it); it.recycle() }
        }
        event(MotionEvent.ACTION_DOWN, 2f, 2f)
        event(MotionEvent.ACTION_MOVE, 160f, 30f)
        event(MotionEvent.ACTION_UP, 160f, 30f)
        assertTrue(output.isEmpty())
        event(MotionEvent.ACTION_DOWN, 160f, 110f)
        event(MotionEvent.ACTION_MOVE, 160f, 60f)
        event(MotionEvent.ACTION_UP, 160f, 20f) // UP is never a commit gesture
        assertTrue(output.isEmpty())
        event(MotionEvent.ACTION_DOWN, 160f, 110f)
        val history = MotionEvent.obtain(1, 2, MotionEvent.ACTION_MOVE, 160f * density, 30f * density, 0)
        history.addBatch(3, 160f * density, 110f * density, 1f, 1f, 0)
        history.addBatch(4, 160f * density, 30f * density, 1f, 1f, 0)
        area.dispatchTouchEvent(history); history.recycle()
        assertEquals(listOf('う', 'う'), output)
        view.visibility = View.GONE
        event(MotionEvent.ACTION_MOVE, 160f, 110f)
        event(MotionEvent.ACTION_MOVE, 160f, 20f)
        assertEquals(listOf('う', 'う'), output)
        view.visibility = View.VISIBLE
        event(MotionEvent.ACTION_DOWN, 160f, 110f)
        fun multi(action: Int, ids: IntArray, ys: FloatArray) {
            val properties = ids.map { id -> MotionEvent.PointerProperties().apply {
                this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER
            } }.toTypedArray()
            val coordinates = ys.map { y -> MotionEvent.PointerCoords().apply {
                x = 160f * density; this.y = y * density; pressure = 1f; size = 1f
            } }.toTypedArray()
            MotionEvent.obtain(1, 5, action, ids.size, properties, coordinates,
                0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0).also {
                area.dispatchTouchEvent(it); it.recycle()
            }
        }
        multi(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            intArrayOf(0, 1), floatArrayOf(110f, 100f))
        multi(MotionEvent.ACTION_MOVE, intArrayOf(0, 1), floatArrayOf(110f, 20f))
        assertEquals(listOf('う', 'う'), output) // Secondary pointer cannot enter a character.
        multi(MotionEvent.ACTION_POINTER_UP, intArrayOf(0, 1), floatArrayOf(110f, 20f))
        multi(MotionEvent.ACTION_MOVE, intArrayOf(1), floatArrayOf(110f))
        multi(MotionEvent.ACTION_MOVE, intArrayOf(1), floatArrayOf(20f))
        multi(MotionEvent.ACTION_UP, intArrayOf(1), floatArrayOf(20f))
        assertEquals(listOf('う', 'う'), output) // The remaining pointer cannot inherit ownership.
        event(MotionEvent.ACTION_DOWN, 160f, 110f)
        event(MotionEvent.ACTION_MOVE, 160f, 60f)
        event(MotionEvent.ACTION_CANCEL, 160f, 20f)
        event(MotionEvent.ACTION_MOVE, 160f, 20f)
        assertEquals(listOf('う', 'う'), output)
        controller.pause().stop().destroy()
    }
}
