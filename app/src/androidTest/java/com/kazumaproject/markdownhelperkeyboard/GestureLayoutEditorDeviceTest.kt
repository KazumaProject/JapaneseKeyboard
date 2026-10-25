package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.PointF
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.gesture_keyboard.*
import com.kazumaproject.markdownhelperkeyboard.gesture.GesturePreferences
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GestureLayoutEditorDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ui get() = ins.uiAutomation
    private fun await(message: String, predicate: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 30000
        while (!predicate()) { check(SystemClock.uptimeMillis() < end) { message }; SystemClock.sleep(100) }
    }
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo) { if (node.isVisibleToUser) result.add(node); for (i in 0 until node.childCount) node.getChild(i)?.let(::walk) }
        ui.windows.forEach { it.root?.let(::walk) }; return result
    }
    private fun node(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        var value: AccessibilityNodeInfo? = null
        await("Editor node not found") { value = nodes().firstOrNull(predicate); value != null }
        return value!!
    }
    private fun point(view: View) = IntArray(2).also { view.getLocationOnScreen(it) }.let { PointF(it[0] + view.width / 2f, it[1] + view.height / 2f) }
    private fun drag(from: PointF, to: PointF) {
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            assertTrue(ui.injectInputEvent(event, true)); event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, from.x, from.y); SystemClock.sleep(700)
        for (i in 1..12) { send(MotionEvent.ACTION_MOVE, from.x + (to.x - from.x) * i / 12, from.y + (to.y - from.y) * i / 12); SystemClock.sleep(30) }
        send(MotionEvent.ACTION_UP, to.x, to.y)
    }
    @Test fun dragIntoEmptyColumnEditDeleteUndoAndRecreate() {
        val context = ins.targetContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val old = prefs.getString(GesturePreferences.LAYOUT, null)
        val store = GesturePreferences(context)
        prefs.edit().remove(GesturePreferences.LAYOUT).commit()
        store.saveLayout(GestureControlLayout(listOf(GestureControlKey("a", GestureCommand.DELETE, "A"),
            GestureControlKey("b", GestureCommand.SPACE, "B")), emptyList()))
        ui.serviceInfo = ui.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                await("Settings did not initialize") { var ready = false; scenario.onActivity { ready = it.isSettingsContentReady }; ready }
                scenario.onActivity { activity ->
                    activity.window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
                    (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                        .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
                    (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment)
                        .navController.navigate(R.id.gestureLayoutFragment)
                }
                ins.waitForIdleSync()
                // The navigation/inset transition must finish before capturing screen coordinates.
                SystemClock.sleep(400)
                var from = PointF(); var to = PointF()
                await("Layout preview did not appear") {
                    var ready = false
                    scenario.onActivity { activity ->
                        val preview = views(activity.window.decorView).filterIsInstance<GestureKeyboardView>().firstOrNull()
                        if (preview != null && preview.width > 0) {
                            val key = views(preview).filterIsInstance<Button>().first { it.text == "A" }
                            val right = (preview.getChildAt(1) as ViewGroup).getChildAt(2)
                            from = point(key)
                            val location = IntArray(2).also { right.getLocationOnScreen(it) }
                            to = PointF(location[0] + right.width / 2f, location[1] + 28f)
                            ready = true
                        }
                    }; ready
                }
                drag(from, to)
                await("Drag did not move key into empty right column") { store.layout().rightKeys.map { it.id } == listOf("a") }
                assertEquals(listOf("b"), store.layout().leftKeys.map { it.id })
                fun click(label: Set<String>) = scenario.onActivity { activity ->
                    views(activity.window.decorView).filterIsInstance<Button>().first { it.text.toString() in label }.performClick()
                }
                click(setOf("A")); click(setOf("Edit", "編集"))
                node { it.className?.toString() == "android.widget.Spinner" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                node { it.text?.toString() == "Enter" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                node { it.className?.toString() == "android.widget.EditText" }.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Edited") })
                node { it.viewIdResourceName == "android:id/button1" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                await("Edited key was not saved") { store.layout().rightKeys.singleOrNull()?.labelOverride == "Edited" }
                assertEquals("a", store.layout().rightKeys.single().id)
                assertEquals(GestureCommand.ENTER, store.layout().rightKeys.single().action)
                click(setOf("Edited")); click(setOf("Delete", "削除"))
                await("Delete did not preserve the empty right column") { store.layout().rightKeys.isEmpty() }
                click(setOf("Undo", "元に戻す"))
                await("Undo did not restore key") { store.layout().rightKeys.singleOrNull()?.id == "a" }
                scenario.recreate()
                await("Recreated preview did not retain layout") {
                    var restored = false
                    scenario.onActivity { restored = views(it.window.decorView).filterIsInstance<Button>().any { b -> b.text == "Edited" } }
                    restored
                }
            }
        } finally {
            prefs.edit().apply { if (old == null) remove(GesturePreferences.LAYOUT) else putString(GesturePreferences.LAYOUT, old) }.commit()
        }
    }
}
