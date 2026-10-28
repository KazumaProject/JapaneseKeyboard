package com.kazumaproject.markdownhelperkeyboard

import android.content.SharedPreferences
import android.graphics.Matrix
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.inspector.WindowInspector
import android.view.inputmethod.BaseInputConnection
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kazumaproject.qwerty_keyboard.ui.QWERTYKeyboardView
import com.kazumaproject.tenkey.TenKey
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class KeyboardGeometryDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun splitQwertyAcceptsInputAndCenterCandidatesCanBeSelected() = verify(floating = false, tenkey = false)
    @Test fun floatingSplitQwertyAcceptsInputAndCenterCandidatesCanBeSelected() = verify(floating = true, tenkey = false)
    @Test fun curvedTenKeyAcceptsInputAndSideCandidatesCanBeSelected() = verify(floating = false, tenkey = true)
    @Test fun floatingCurvedTenKeyAcceptsInputAndSideCandidatesCanBeSelected() = verify(floating = true, tenkey = true)

    @Test fun landscapeSplitQwertyAcceptsInputAndCenterCandidatesCanBeSelected() {
        val oldAuto = shell("settings get system accelerometer_rotation")
        val oldRotation = shell("settings get system user_rotation")
        try {
            shell("settings put system accelerometer_rotation 0")
            shell("settings put system user_rotation 1")
            SystemClock.sleep(1500)
            verify(floating = false, tenkey = false, landscape = true)
        } finally {
            if (oldRotation == "null") shell("settings delete system user_rotation") else shell("settings put system user_rotation $oldRotation")
            if (oldAuto == "null") shell("settings delete system accelerometer_rotation") else shell("settings put system accelerometer_rotation $oldAuto")
        }
    }

    private fun verify(floating: Boolean, tenkey: Boolean, landscape: Boolean = false) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val original = prefs.all.toMap()
        val originalIme = shell("settings get secure default_input_method")
        val targetIme = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val wasEnabled = shell("ime list -s").lineSequence().any { it.trim() == targetIme }
        val orientation = if (landscape) "landscape" else "portrait"
        var activity: FastInputHostActivity? = null
        try {
            check(prefs.edit().clear()
                .putString("keyboard_order_preference", if (tenkey) "[\"TENKEY\"]" else "[\"QWERTY\"]")
                .putBoolean("save_last_used_keyboard", false)
                .putBoolean("keyboard_floating_preference", floating)
                .putBoolean("qwerty_glide_input_preference", true)
                .putBoolean("learn_dictionary_preference", false)
                .putInt("qwerty_keyboard_width_preference", 100)
                .putInt("qwerty_keyboard_height_preference", 220)
                .putInt("qwerty_keyboard_width_landscape_preference", 100)
                .putInt("qwerty_keyboard_height_landscape_preference", 200)
                .putInt("keyboard_geometry_qwerty_${orientation}_split", 40)
                .putInt("keyboard_geometry_qwerty_${orientation}_curve", 20)
                .putString("keyboard_geometry_qwerty_${orientation}_candidates", "CENTER")
                .putBoolean("keyboard_geometry_qwerty_${orientation}_auto_height", true)
                .putInt("keyboard_geometry_tenkey_portrait_curve", 20)
                .putString("keyboard_geometry_tenkey_portrait_candidates", "LEFT")
                .putBoolean("flick_input_only_preference", true)
                .commit())
            shell("ime enable $targetIme")
            shell("am start -n ${context.packageName}/${FastInputHostActivity::class.java.name}")
            await {
                instrumentation.runOnMainSync {
                    activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<FastInputHostActivity>().firstOrNull()?.takeIf { it.hasWindowFocus() }
                }
                activity != null
            }
            shell("ime set $targetIme")
            val host = requireNotNull(activity)
            val editor = host.editText
            instrumentation.runOnMainSync {
                host.restartEditorInput(clearText = true)
                host.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                    .showSoftInput(editor, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
            if (tenkey) {
                val keyboard = awaitView<TenKey>(if (floating) R.id.keyboard_view_floating else R.id.keyboard_view) {
                    (it.findViewById<View>(com.kazumaproject.tenkey.R.id.key_1)?.rotation ?: 0f) != 0f
                }
                tap(keyboard.findViewById(com.kazumaproject.tenkey.R.id.key_1))
            } else {
                val keyboard = awaitView<QWERTYKeyboardView>(if (floating) R.id.qwerty_view_floating else R.id.qwerty_view) {
                    it.effectiveSplitGapPx() > 96f * context.resources.displayMetrics.density
                }
                for (id in listOf(com.kazumaproject.qwerty_keyboard.R.id.key_h,
                    com.kazumaproject.qwerty_keyboard.R.id.key_e,
                    com.kazumaproject.qwerty_keyboard.R.id.key_l,
                    com.kazumaproject.qwerty_keyboard.R.id.key_l,
                    com.kazumaproject.qwerty_keyboard.R.id.key_o)) {
                    tap(keyboard.findViewById(id))
                }
            }
            await { var text = ""; instrumentation.runOnMainSync { text = editor.text.toString() }; text.isNotEmpty() }
            SystemClock.sleep(1200)
            var committed = false
            // Suggestions can be replaced asynchronously as dictionaries finish loading.
            // Reacquire the visible item if a pending update replaces it during a tap.
            repeat(5) {
                if (!committed) {
                    val candidates = awaitView<RecyclerView>(R.id.suggestion_recycler_view) {
                        (it.layoutManager as? LinearLayoutManager)?.orientation == RecyclerView.VERTICAL && it.childCount > 0
                    }
                    var first: View? = null
                    instrumentation.runOnMainSync { first = candidates.getChildAt(0)?.takeIf { it.isShown && it.isAttachedToWindow } }
                    first?.let { tap(it) }
                    val end = SystemClock.uptimeMillis() + 2000
                    while (!committed && SystemClock.uptimeMillis() < end) {
                        instrumentation.runOnMainSync { committed = BaseInputConnection.getComposingSpanStart(editor.text) < 0 }
                        if (!committed) SystemClock.sleep(100)
                    }
                }
            }
            assertTrue("A candidate in the relocated list should commit the composition", committed)
            assertTrue(prefs.getBoolean("qwerty_glide_input_preference", false))
        } finally {
            instrumentation.runOnMainSync { activity?.finish() }
            restore(prefs, original)
            if (originalIme.isNotBlank() && originalIme != "null") shell("ime set $originalIme")
            if (!wasEnabled) shell("ime disable $targetIme")
        }
    }

    private inline fun <reified T : View> awaitView(id: Int, crossinline predicate: (T) -> Boolean): T {
        var found: T? = null
        await {
            instrumentation.runOnMainSync {
                found = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull {
                    it.findViewById<T>(id)?.takeIf { view -> view.isShown && view.width > 0 && predicate(view) }
                }
            }
            found != null
        }
        return requireNotNull(found)
    }

    private fun tap(view: View) {
        val point = floatArrayOf(0f, 0f)
        var previous = Pair(Float.NaN, Float.NaN)
        var stableSince = 0L
        await {
            instrumentation.runOnMainSync {
                val position = IntArray(2).also(view::getLocationOnScreen)
                val points = floatArrayOf(0f, 0f, view.width / 2f, view.height / 2f)
                val transform = Matrix(); view.transformMatrixToGlobal(transform); transform.mapPoints(points)
                point[0] = position[0] + points[2] - points[0]
                point[1] = position[1] + points[3] - points[1]
            }
            val current = point[0] to point[1]
            if (current != previous) { previous = current; stableSince = SystemClock.uptimeMillis() }
            SystemClock.uptimeMillis() - stableSince >= 400
        }
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, point[0], point[1], 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { check(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
            SystemClock.sleep(50)
        }
        SystemClock.sleep(200)
    }

    private fun await(predicate: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 30000
        while (SystemClock.uptimeMillis() < end) {
            if (predicate()) return
            SystemClock.sleep(100)
        }
        error("Timed out waiting for the IME to update")
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText().trim() }

    private fun restore(prefs: SharedPreferences, values: Map<String, *>) {
        val edit = prefs.edit().clear()
        values.forEach { (key, value) -> when (value) {
            is String -> edit.putString(key, value)
            is Boolean -> edit.putBoolean(key, value)
            is Int -> edit.putInt(key, value)
            is Long -> edit.putLong(key, value)
            is Float -> edit.putFloat(key, value)
            is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
        } }
        check(edit.commit())
    }
}
