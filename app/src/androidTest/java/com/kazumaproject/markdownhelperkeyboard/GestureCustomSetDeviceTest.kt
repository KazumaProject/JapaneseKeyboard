package com.kazumaproject.markdownhelperkeyboard

import android.graphics.PointF
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
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
class GestureCustomSetDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun await(message: String, predicate: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 30000
        while (!predicate()) { check(SystemClock.uptimeMillis() < end) { message }; SystemClock.sleep(100) }
    }
    @Test fun recordDraftRecreateSaveAndRecognizeCustomKana() {
        val context = ins.targetContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val previous = prefs.getString(GesturePreferences.SETS, null)
        val store = GesturePreferences(context)
        prefs.edit().remove(GesturePreferences.SETS).commit()
        store.saveSet(GestureSet("device-test", "Device test", emptyList()))
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                await("Settings not ready") { var ready = false; scenario.onActivity { ready = it.isSettingsContentReady }; ready }
                scenario.onActivity { activity ->
                    (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment)
                        .navController.navigate(R.id.gestureSetEditorFragment, Bundle().apply { putString("setId", "device-test") })
                }
                fun click(labels: Set<String>) = scenario.onActivity { activity ->
                    views(activity.window.decorView).filterIsInstance<Button>().first { it.text.toString() in labels }.performClick()
                }
                await("Add gesture missing") {
                    var found = false; scenario.onActivity { activity ->
                        found = views(activity.window.decorView).filterIsInstance<Button>().any { it.text.toString() in setOf("Add gesture", "ジェスチャーを追加") }
                    }; found
                }
                click(setOf("Add gesture", "ジェスチャーを追加"))
                var origin = PointF(); var width = 0f; var height = 0f
                await("Capture editor missing") {
                    var found = false; scenario.onActivity { activity ->
                        val all = views(activity.window.decorView)
                        val field = all.filterIsInstance<EditText>().firstOrNull()
                        val pad = all.filterIsInstance<GesturePadView>().firstOrNull()
                        if (field != null && pad != null && pad.width > 0) {
                            field.setText("あ")
                            val location = IntArray(2).also { pad.getLocationOnScreen(it) }
                            origin = PointF(location[0].toFloat(), location[1].toFloat())
                            width = pad.width.toFloat(); height = pad.height.toFloat(); found = true
                        }
                    }; found
                }
                val down = SystemClock.uptimeMillis()
                val points = listOf(PointF(width * .2f, height * .2f), PointF(width * .6f, height * .75f), PointF(width * .8f, height * .25f))
                points.forEachIndexed { i, p ->
                    val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), if (i == 0) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_MOVE,
                        origin.x + p.x, origin.y + p.y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
                    assertTrue(ins.uiAutomation.injectInputEvent(event, true)); event.recycle(); SystemClock.sleep(30)
                }
                val up = MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,
                    origin.x + points.last().x, origin.y + points.last().y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
                assertTrue(ins.uiAutomation.injectInputEvent(up, true)); up.recycle()
                scenario.recreate()
                await("Draft was not restored") {
                    var restored = false; scenario.onActivity { activity ->
                        val all = views(activity.window.decorView)
                        restored = all.filterIsInstance<EditText>().any { it.text.toString() == "あ" } &&
                            all.filterIsInstance<GesturePadView>().any { !it.guide.isNullOrEmpty() }
                    }; restored
                }
                click(setOf("Save", "保存"))
                await("Custom gesture not saved") { store.customSets().single().entries.size == 1 }
                val set = store.customSets().single()
                val entry = set.entries.single()
                assertEquals(GestureOutput.Reading("あ"), entry.output)
                assertEquals(entry.output, GestureRecognizer.Prepared(set).recognize(entry.samples.single())?.entry?.output)
            }
        } finally {
            prefs.edit().apply { if (previous == null) remove(GesturePreferences.SETS) else putString(GesturePreferences.SETS, previous) }.commit()
        }
    }
}
