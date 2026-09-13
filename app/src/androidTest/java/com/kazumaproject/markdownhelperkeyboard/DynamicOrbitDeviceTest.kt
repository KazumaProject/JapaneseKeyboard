package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.BaseInputConnection
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.ime_service.dynamic_orbit.OrbitGeometry
import com.kazumaproject.markdownhelperkeyboard.ime_service.dynamic_orbit.OrbitGesture
import com.kazumaproject.markdownhelperkeyboard.ime_service.dynamic_orbit.OrbitPoint
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.*

/** Exercises screen-coordinate strokes through Android's real IME window, not direct View calls. */
@RunWith(AndroidJUnit4::class)
class DynamicOrbitDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ui get() = ins.uiAutomation
    private val context get() = ins.targetContext
    private val density get() = context.resources.displayMetrics.density
    private var captureCase = ""
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command))
        .bufferedReader().use { it.readText() }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            result += node
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        ui.windows.forEach { it.root?.let(::visit) }
        return result
    }
    private fun awaitBounds(id: String? = null, label: String? = null): Rect {
        val end = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < end) {
            nodes().firstOrNull { it.isVisibleToUser && it.packageName?.toString() == context.packageName &&
                (if (id != null) it.viewIdResourceName?.endsWith(":id/$id") == true else it.text?.toString() == label)
            }?.let { return Rect().also(it::getBoundsInScreen) }
            SystemClock.sleep(100)
        }
        error("Missing $id/$label: " + nodes().joinToString { "${it.viewIdResourceName}=${it.text}" })
    }
    private fun event(start: Long, action: Int, x: Float, y: Float) {
        MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x, y, 0).also {
            it.source = InputDevice.SOURCE_TOUCHSCREEN
            check(ui.injectInputEvent(it, true))
            it.recycle()
        }
    }
    private fun tap(bounds: Rect) {
        val start = SystemClock.uptimeMillis()
        event(start, MotionEvent.ACTION_DOWN, bounds.exactCenterX(), bounds.exactCenterY())
        event(start, MotionEvent.ACTION_UP, bounds.exactCenterX(), bounds.exactCenterY())
        SystemClock.sleep(100)
    }
    private fun tapId(id: String) = tap(awaitBounds(id))
    private fun tapLabel(resource: Int) = tap(awaitBounds(label = context.getString(resource)))
    private fun editor(scenario: ActivityScenario<FastInputHostActivity>): String {
        var result = ""
        scenario.onActivity { result = it.editText.text.toString() }
        return result
    }
    private fun awaitText(scenario: ActivityScenario<FastInputHostActivity>, expected: String) {
        val end = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < end && editor(scenario) != expected) SystemClock.sleep(50)
        assertEquals(expected, editor(scenario))
    }
    private fun screenshot(name: String) {
        val bitmap = ui.takeScreenshot() ?: return
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun stroke(text: String, corner: Int = -1, previewOnly: Boolean = false, capture: Boolean = false) {
        val area = awaitBounds("orbit_touch_area")
        val inset = 81 * density
        val x = when (corner) { 0, 2 -> area.left + inset; 1, 3 -> area.right - inset; else -> area.exactCenterX() }
        val y = when (corner) { 0, 1 -> area.top + inset; 2, 3 -> area.bottom - inset; else -> area.exactCenterY() }
        val start = SystemClock.uptimeMillis()
        event(start, MotionEvent.ACTION_DOWN, x, y)
        for (char in text) {
            val row = OrbitGeometry.rows.indexOfFirst { char in it }
            val vowel = OrbitGeometry.rows[row].indexOf(char)
            val a = row * 36f
            val b = OrbitGeometry.vowelAngles[vowel]
            fun move(radius: Float, angle: Float) {
                val point = OrbitGeometry.point(radius, angle)
                event(start, MotionEvent.ACTION_MOVE, x + point.x * density, y + point.y * density)
            }
            move(52f, a)
            val delta = (b - a + 540f) % 360f - 180f
            val steps = ceil(abs(delta) / 5).toInt().coerceAtLeast(1)
            for (i in 1..steps) move(52f, a + delta * i / steps)
            if (capture) screenshot("orbit-vowel-$captureCase-$char")
            if (!previewOnly) {
                move(76f, b)
                if (capture) screenshot("orbit-return-$captureCase-$char")
                event(start, MotionEvent.ACTION_MOVE, x, y)
            }
        }
        event(start, MotionEvent.ACTION_UP, x, y)
    }

    @Test fun gestureProcessingTimingOnDevice() {
        val gesture = OrbitGesture().apply { start() }
        val timings = mutableListOf<Long>()
        val expected = OrbitGeometry.rows.joinToString("").filter { it != ' ' }
        repeat(12) { pass ->
            val output = StringBuilder()
            fun move(point: OrbitPoint) {
                val start = System.nanoTime()
                val letters = gesture.move(point)
                val elapsed = System.nanoTime() - start
                if (pass >= 2) timings += elapsed
                letters.forEach(output::append)
            }
            OrbitGeometry.rows.forEachIndexed { row, chars -> chars.forEachIndexed { vowel, char ->
                if (char != ' ') {
                    val a = row * 36f
                    val b = OrbitGeometry.vowelAngles[vowel]
                    move(OrbitGeometry.point(52f, a))
                    val delta = (b - a + 540f) % 360f - 180f
                    val steps = ceil(abs(delta) / 5).toInt().coerceAtLeast(1)
                    for (i in 1..steps) move(OrbitGeometry.point(52f, a + delta * i / steps))
                    move(OrbitGeometry.point(76f, b))
                    move(OrbitPoint(0f, 0f))
                }
            } }
            assertEquals(expected, output.toString())
        }
        timings.sort()
        ins.sendStatus(0, android.os.Bundle().apply {
            putString("stream", "Orbit geometry MOVE: samples=${timings.size}, median=${timings[timings.size / 2] / 1000.0}us, " +
                "p95=${timings[timings.size * 95 / 100] / 1000.0}us, max=${timings.last() / 1000.0}us; excludes UI and conversion.\n")
        })
    }

    @Test fun realIme_continuousInputCommandsAndFloatingSurfaces() {
        check(context.packageName.startsWith("com.kazumaproject.dynamicorbittest")) { "Use investigation/dynamic-orbit.init.gradle" }
        val target = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val oldIme = shell("settings get secure default_input_method").trim()
        val wasEnabled = shell("ime list -s").lineSequence().any { it.trim() == target }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val saved = prefs.all.toMap()
        val oldInfo = ui.serviceInfo
        val oldFlags = oldInfo.flags
        ui.serviceInfo = oldInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        try {
            shell("ime enable $target"); shell("ime set $target")
            data class Case(val name: String, val floating: Boolean = false, val guide: Boolean = false,
                val skin: String = "default", val landscape: Boolean = false, val minimum: Boolean = false)
            val cases = listOf(Case("normal"), Case("floating", floating = true),
                Case("guides", guide = true), Case("light", skin = "cupertino_light"),
                Case("dark", guide = true, skin = "cupertino_dark"), Case("landscape", landscape = true),
                Case("minimum", minimum = true))
            for (testCase in cases) {
                val floating = testCase.floating
                captureCase = testCase.name
                ins.sendStatus(0, android.os.Bundle().apply { putString("stream", "Orbit case: ${testCase.name}\n") })
                check(prefs.edit()
                    .putString("keyboard_order_preference", "[\"DYNAMIC_ORBIT\",\"TENKEY\",\"QWERTY\",\"SUMIRE\"]")
                    .putBoolean("save_last_used_keyboard", false)
                    .putInt("save_last_used_keyboard_int", 0)
                    .putBoolean("keyboard_floating_preference", floating)
                    .putBoolean("flick_input_only_preference", false)
                    .putBoolean("live_conversion_preference", false)
                    .putBoolean("composing_guide_enabled", testCase.guide)
                    .putBoolean("composing_guide_text_enabled", testCase.guide)
                    .putString("composing_guide_display_mode", if (testCase.name == "dark") "integrated" else "separate")
                    .putString("keyboard_skin_preference", testCase.skin)
                    .putInt("keyboard_height_preference", if (testCase.minimum) 100 else 220)
                    .putInt("keyboard_width_preference", if (testCase.minimum) 40 else 100)
                    .commit())
                ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { scenario ->
                    scenario.onActivity { it.requestedOrientation = if (testCase.landscape)
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                    awaitBounds("orbit_touch_area"); SystemClock.sleep(800)
                    screenshot("orbit-idle-${testCase.name}")
                    stroke("すみれ", capture = testCase.name in listOf("normal", "floating", "guides", "light", "dark"))
                    awaitText(scenario, "すみれ")
                    stroke("あ", previewOnly = true)
                    awaitText(scenario, "すみれ")
                    tapId("orbit_delete"); awaitText(scenario, "すみ")
                    stroke("れ"); awaitText(scenario, "すみれ")
                    for (corner in 0..3) stroke("あ", corner)
                    awaitText(scenario, "すみれああああ")
                    val repeats = if (testCase.name in listOf("normal", "floating")) 20 else 3
                    stroke("か".repeat(repeats)); awaitText(scenario, "すみれああああ" + "か".repeat(repeats))
                    tapId("orbit_enter")
                    scenario.onActivity { assertEquals(-1, BaseInputConnection.getComposingSpanStart(it.editText.text)) }
                    scenario.onActivity { it.restartEditorInput(true) }
                    awaitBounds("orbit_touch_area"); SystemClock.sleep(300)
                    stroke("すみれ"); awaitText(scenario, "すみれ")
                    SystemClock.sleep(700)
                    tapId("orbit_space"); tapId("orbit_enter")
                    assertTrue(editor(scenario).isNotEmpty())
                    scenario.onActivity { assertEquals(-1, BaseInputConnection.getComposingSpanStart(it.editText.text)) }
                    screenshot("orbit-converted-${testCase.name}")
                    tapId("orbit_cursor"); tapLabel(R.string.orbit_left)
                    tapId("orbit_mode")
                    screenshot("orbit-mode-${testCase.name}")
                    tapLabel(R.string.orbit_english)
                    awaitBounds("key_a")
                    tapId("key_a")
                    // The existing QWERTY switch-default key returns to the Orbit proxy owner.
                    tapId("key_switch_default")
                    awaitBounds("orbit_touch_area")
                    tapId("orbit_mode"); tapLabel(R.string.orbit_symbols)
                    screenshot("orbit-symbols-${testCase.name}")
                    tapId("return_jp_keyboard_button")
                    awaitBounds("orbit_touch_area")
                    SystemClock.sleep(500) // Let the symbol panel resize animation finish before opening a menu.
                    tapId("orbit_mode"); tapLabel(R.string.orbit_number)
                    tapId("key_q") // The number layout relabels Q as 1.
                    tapId("switch_number_layout")
                    awaitBounds("orbit_touch_area")
                    scenario.onActivity { assertEquals(-1, BaseInputConnection.getComposingSpanStart(it.editText.text)) }
                    if (testCase.name == "normal") {
                        tapId("orbit_mode"); tapLabel(R.string.orbit_ime_picker)
                        shell("input keyevent 4")
                        awaitBounds("orbit_touch_area")
                        tapId("orbit_mode"); tapLabel(R.string.orbit_next_keyboard)
                        assertTrue(nodes().none { it.isVisibleToUser && it.viewIdResourceName?.endsWith(":id/orbit_touch_area") == true })
                    }
                    assertEquals(if (testCase.minimum) 100 else 220, prefs.getInt("keyboard_height_preference", -1))
                    assertEquals(if (testCase.minimum) 40 else 100, prefs.getInt("keyboard_width_preference", -1))
                }
            }
        } finally {
            val edit = prefs.edit().clear()
            saved.forEach { (key, value) -> when (value) {
                is Boolean -> edit.putBoolean(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Float -> edit.putFloat(key, value)
                is String -> edit.putString(key, value)
                is Set<*> -> @Suppress("UNCHECKED_CAST") edit.putStringSet(key, value as Set<String>)
            } }
            edit.commit()
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
            if (!wasEnabled) shell("ime disable $target")
            oldInfo.flags = oldFlags
            ui.serviceInfo = oldInfo
        }
    }
}
