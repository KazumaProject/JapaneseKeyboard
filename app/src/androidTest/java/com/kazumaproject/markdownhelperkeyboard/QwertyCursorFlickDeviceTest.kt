package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real QWERTY touch events through the IME into a multiline editor. */
@RunWith(AndroidJUnit4::class)
class QwertyCursorFlickDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val ui get() = instrumentation.uiAutomation
    private val context get() = instrumentation.targetContext

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command))
            .bufferedReader().use { it.readText().trim() }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < deadline) { message }
            SystemClock.sleep(50)
        }
    }

    private fun keyBounds(id: String): Rect {
        val resource = context.resources.getIdentifier(id, "id", context.packageName)
        check(resource != 0) { "Missing key resource $id" }
        val name = context.resources.getResourceName(resource)
        var found: AccessibilityNodeInfo? = null
        await("Missing QWERTY key $id") {
            if (android.os.Build.VERSION.SDK_INT >= 33) ui.clearCache()
            found = ui.windows.asSequence().mapNotNull { it.root }
                .flatMap { it.findAccessibilityNodeInfosByViewId(name).asSequence() }
                .firstOrNull { it.isVisibleToUser }
            found != null
        }
        return Rect().also { requireNotNull(found).getBoundsInScreen(it) }
    }

    private fun flick(rect: Rect, dy: Float) {
        val downTime = SystemClock.uptimeMillis()
        val x = rect.exactCenterX()
        val y = rect.exactCenterY()
        fun send(action: Int, touchY: Float) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, touchY, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { check(ui.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, y)
        SystemClock.sleep(40)
        send(MotionEvent.ACTION_MOVE, y + dy)
        SystemClock.sleep(40)
        send(MotionEvent.ACTION_UP, y + dy)
    }

    @Test
    fun bothCursorKeysFlickUpAndDownInMultilineEditor() {
        assumeTrue("Requires an isolated QA application ID", context.packageName.startsWith("com.kazumaproject.cursorflickqa"))
        val args = InstrumentationRegistry.getArguments()
        val oldIme = shell("settings get secure default_input_method")
        val target = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val wasEnabled = shell("settings get secure enabled_input_methods").split(':')
            .any { it.substringBefore(';') == target }
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear()
            .putBoolean("save_last_used_keyboard", false)
            .putString("keyboard_order_preference", "[\"${args.getString("keyboard") ?: "QWERTY"}\"]")
            .putBoolean("qwerty_show_cursor_buttons_preference", true)
            .putBoolean("qwerty_show_popup_window_preference", false)
            .putBoolean("qwerty_enable_flick_up_preference", args.getString("letter_flick") == "on")
            .putBoolean("qwerty_enable_flick_down_preference", args.getString("letter_flick") == "on")
            .putBoolean("qwerty_glide_input_preference", args.getString("glide") == "on")
            .putBoolean("independent_multi_touch_preference", args.getString("multitouch") == "independent")
            // Slow emulator rendering must not turn this flick test into a long-press test.
            .putInt("long_press_timeout_preference", 2000)
            .putBoolean("keyboard_floating_preference", false)
            .putBoolean("landscape_force_qwerty_preference", false)
            .putBoolean("allow_fullscreen_mode_preference", false)
            .putBoolean("learn_dictionary_preference", false)
            .putBoolean("live_conversion_preference", false)
            .commit()
        ui.serviceInfo = ui.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        try {
            shell("input keyevent WAKEUP")
            shell("wm dismiss-keyguard")
            shell("ime enable $target")
            shell("ime set $target")
            ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { scenario ->
                scenario.onActivity { it.restartEditorInput(true) }
                keyBounds("cursor_left")
                // Accessibility nodes can appear while the IME is still animating into place.
                SystemClock.sleep(800)
                val text = "abcde\nabcde\nabcde"
                val caret = 8
                for (key in listOf("cursor_left", "cursor_right")) {
                    for (lineDelta in listOf(-1, 1)) {
                        scenario.onActivity { host ->
                            host.editText.setText(text)
                            host.editText.setSelection(caret)
                        }
                        SystemClock.sleep(300)
                        var expected = -1
                        scenario.onActivity { host ->
                            val layout = host.editText.layout
                            val line = layout.getLineForOffset(caret) + lineDelta
                            expected = layout.getOffsetForHorizontal(line, layout.getPrimaryHorizontal(caret))
                        }
                        val bounds = keyBounds(key)
                        flick(bounds, lineDelta * 24f * context.resources.displayMetrics.density)
                        await("$key flick $lineDelta did not move to $expected") {
                            var position = -1
                            scenario.onActivity { position = it.editText.selectionStart }
                            position == expected
                        }
                        SystemClock.sleep(200)
                        scenario.onActivity { host ->
                            assertEquals("Unexpected text after $key flick $lineDelta", text, host.editText.text.toString())
                            assertEquals(expected, host.editText.selectionStart)
                            assertEquals(expected, host.editText.selectionEnd)
                        }
                    }
                }
            }
        } finally {
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
            if (!wasEnabled) shell("ime disable $target")
        }
    }
}
