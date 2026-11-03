package com.kazumaproject.markdownhelperkeyboard

import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
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
class PalmGestureEditorDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun await(message: String, predicate: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 30000
        while (!predicate()) { check(SystemClock.uptimeMillis() < end) { message }; SystemClock.sleep(100) }
    }
    private fun draw(pad: GesturePadView) {
        // Independent looped-x fixture, fitted uniformly into the actual practice surface.
        val raw = GesturePresets.path("0,0 5,5 8,3 11,3 12,5 10,7 7,7 5,5 0,10")
        val scale = minOf(pad.width / 16f, pad.height / 14f)
        val points = raw.map { GesturePoint(pad.width / 2f + (it.x - 6) * scale, pad.height / 2f + (it.y - 5) * scale) }
        val time = SystemClock.uptimeMillis()
        points.forEachIndexed { i, p -> MotionEvent.obtain(time, time + i * 16,
            if (i == 0) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_MOVE, p.x, p.y, 0)
            .also { pad.dispatchTouchEvent(it); it.recycle() } }
        MotionEvent.obtain(time, time + 200, MotionEvent.ACTION_UP, points.last().x, points.last().y, 0)
            .also { pad.dispatchTouchEvent(it); it.recycle() }
    }
    @Test fun searchPracticeReplayRegisterAndEditSamplesWithUndoAndRecreation() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ins.targetContext)
        val previous = prefs.getString(GesturePreferences.SETS, null)
        val store = GesturePreferences(ins.targetContext)
        prefs.edit().remove(GesturePreferences.SETS).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                await("Settings not ready") { var ready = false; scenario.onActivity { ready = it.isSettingsContentReady }; ready }
                scenario.onActivity { activity ->
                    (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment)
                        .navController.navigate(R.id.gestureSetEditorFragment, Bundle().apply { putString("setId", PalmGesturePresets.LATIN) })
                }
                fun click(vararg labels: String) = scenario.onActivity { activity ->
                    views(activity.window.decorView).filterIsInstance<Button>().first { it.text.toString() in labels }.performClick()
                }
                await("Guide missing") { var ready = false; scenario.onActivity { ready = views(it.window.decorView).any { v -> v is GestureGuideView } }; ready }
                scenario.onActivity { activity ->
                    val guide = views(activity.window.decorView).filterIsInstance<GestureGuideView>().single()
                    val all = views(guide)
                    all.filterIsInstance<EditText>().single().setText("x")
                    assertTrue(views(guide).first { it.contentDescription == "x" }.performClick())
                    val pad = all.filterIsInstance<GesturePadView>().first { it.captureAllowed() }
                    draw(pad)
                }
                click("Replay stroke", "書き順を再生")
                scenario.onActivity { activity -> assertTrue(views(activity.window.decorView).filterIsInstance<GesturePadView>().first { it.captureAllowed() }.animateGuide) }
                click("Save practice stroke", "練習した線を登録")
                await("Recorded draft missing") { var ready = false; scenario.onActivity { activity ->
                    ready = views(activity.window.decorView).filterIsInstance<Spinner>().any { it.count == 3 }
                }; ready }
                scenario.onActivity { activity -> views(activity.window.decorView).filterIsInstance<Spinner>().last().setSelection(2) }
                click("Remove selected sample", "選択した見本を削除")
                scenario.onActivity { activity -> assertEquals(2, views(activity.window.decorView).filterIsInstance<Spinner>().last().count) }
                click("Undo sample change", "見本の変更を取り消す")
                scenario.onActivity { activity -> assertEquals(3, views(activity.window.decorView).filterIsInstance<Spinner>().last().count) }
                click("Replace selected sample", "選択した見本を再録")
                scenario.onActivity { activity -> draw(views(activity.window.decorView).filterIsInstance<GesturePadView>().first { it.captureAllowed() }) }
                scenario.recreate()
                await("Draft not restored") { var ready = false; scenario.onActivity { activity ->
                    ready = views(activity.window.decorView).filterIsInstance<Spinner>().any { it.count == 3 }
                }; ready }
                click("Save", "保存")
                await("Samples not saved") { store.customSets().singleOrNull()?.entries?.firstOrNull { it.id == "x" }?.samples?.size == 3 }
                assertTrue(store.customSets().single().palm)
                assertFalse(store.customSets().single().builtIn)
            }
        } finally {
            prefs.edit().apply { if (previous == null) remove(GesturePreferences.SETS) else putString(GesturePreferences.SETS, previous) }.commit()
        }
    }
}
