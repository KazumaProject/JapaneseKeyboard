package com.kazumaproject.markdownhelperkeyboard

import android.graphics.PointF
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.gesture_keyboard.GestureCanvasView
import com.kazumaproject.markdownhelperkeyboard.gesture.GesturePreferences
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GesturePadLayoutDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun await(message: String, predicate: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 30000
        while (!predicate()) { check(SystemClock.uptimeMillis() < end) { message }; SystemClock.sleep(100) }
    }
    private fun center(view: View) = IntArray(2).also(view::getLocationOnScreen).let { PointF(it[0] + view.width / 2f, it[1] + view.height / 2f) }
    private fun movePoint(view: View) = IntArray(2).also(view::getLocationOnScreen).let {
        PointF(it[0] + view.width * .2f, it[1] + view.height * .2f)
    }
    private fun drag(from: PointF, to: PointF) {
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            assertTrue(ins.uiAutomation.injectInputEvent(event, true)); event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, from.x, from.y)
        for (i in 1..12) { send(MotionEvent.ACTION_MOVE, from.x + (to.x - from.x) * i / 12, from.y + (to.y - from.y) * i / 12); SystemClock.sleep(25) }
        send(MotionEvent.ACTION_UP, to.x, to.y)
        ins.waitForIdleSync()
    }
    @Test fun selectAllCountsResizeMoveRejectOverlapUndoAndRecreate() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(ins.targetContext)
        val old = preferences.getString(GesturePreferences.SESSION, null)
        val oldLayout = preferences.getString(GesturePreferences.LAYOUT, null)
        val store = GesturePreferences(ins.targetContext)
        preferences.edit().remove(GesturePreferences.SESSION).remove(GesturePreferences.LAYOUT).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                await("Settings not ready") { var ready = false; scenario.onActivity { ready = it.isSettingsContentReady }; ready }
                scenario.onActivity { activity ->
                    (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment)
                        .navController.navigate(R.id.gestureLayoutFragment)
                }
                ins.waitForIdleSync(); SystemClock.sleep(400)
                for (count in 1..4) {
                    scenario.onActivity { activity -> views(activity.window.decorView).filterIsInstance<Spinner>().first().setSelection(count - 1) }
                    await("Count $count not saved") { store.session().pads.size == count }
                    store.session().validate()
                }
                var handle = PointF(); var dx = 0f; var dy = 0f
                scenario.onActivity { activity ->
                    val all = views(activity.window.decorView)
                    handle = center(all.first { it.contentDescription?.toString() in setOf("Resize area 1", "領域1のサイズ変更") })
                    val canvas = all.filterIsInstance<GestureCanvasView>().single()
                    dx = canvas.width * .2f; dy = canvas.height * .2f
                }
                drag(handle, PointF(handle.x - dx, handle.y - dy))
                await("Area did not resize") { store.session().pads[0].bounds.width < .4f }
                val resized = store.session()
                var from = PointF()
                scenario.onActivity { activity -> from = movePoint(views(activity.window.decorView).first {
                    it.contentDescription?.toString() in setOf("Move area 1", "領域1を移動") }) }
                drag(from, PointF(from.x + dx / 2, from.y + dy / 2))
                await("Area did not move") { store.session().pads[0].bounds.x > .05f }
                val moved = store.session()
                scenario.onActivity { activity -> from = movePoint(views(activity.window.decorView).first {
                    it.contentDescription?.toString() in setOf("Move area 1", "領域1を移動") }) }
                drag(from, PointF(from.x + dx * 2, from.y))
                assertEquals("Overlap must be rejected", moved, store.session())
                scenario.onActivity { activity -> views(activity.window.decorView).filterIsInstance<TextView>().first { it.text.toString() in setOf("Undo", "元に戻す") }.performClick() }
                await("Undo did not restore geometry") { store.session() == resized }
                scenario.recreate()
                await("Recreated editor lost geometry") {
                    var ready = false
                    scenario.onActivity { ready = views(it.window.decorView).filterIsInstance<GestureCanvasView>().singleOrNull()?.pads?.size == 4 }
                    ready
                }
                assertEquals(resized, store.session())
            }
        } finally { preferences.edit().apply { if (old == null) remove(GesturePreferences.SESSION) else putString(GesturePreferences.SESSION, old)
            if (oldLayout == null) remove(GesturePreferences.LAYOUT) else putString(GesturePreferences.LAYOUT, oldLayout) }.commit() }
    }
}
