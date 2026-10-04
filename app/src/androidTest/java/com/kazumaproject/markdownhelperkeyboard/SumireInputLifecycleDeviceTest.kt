package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.BaseInputConnection
import androidx.preference.PreferenceManager
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Run with investigation/custom-toggle.init.gradle and surface=normal, floating, or split. */
@RunWith(AndroidJUnit4::class)
class SumireInputLifecycleDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val automation get() = ins.uiAutomation
    private val context get() = ins.targetContext
    private val surface = InstrumentationRegistry.getArguments().getString("surface", "normal")
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    @Test fun firstModeSwitchAndInputSurviveReopenSettingsAndRotation() {
        assumeTrue("Use the isolated application ID from investigation/custom-toggle.init.gradle",
            context.packageName.startsWith("com.kazumaproject.customtoggletest"))
        check(surface in listOf("normal", "floating", "split"))
        val target = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val oldIme = shell("settings get secure default_input_method").trim()
        val wasEnabled = shell("ime list -s").lineSequence().any { it.trim() == target }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        try {
            check(prefs.edit().clear()
                .putString("keyboard_order_preference", if (surface == "split") "[\"SPLIT\",\"QWERTY\"]" else "[\"SUMIRE\",\"QWERTY\"]")
                .putBoolean("save_last_used_keyboard", false)
                .putBoolean("gojuon_keyboard_type_migrated_v1", true)
                .putBoolean("keyboard_floating_preference", surface == "floating")
                .putString("sumire_input_method_preference", "toggle")
                .putString("sumire_keyboard_style_preference", "default")
                .putBoolean("sumire_english_qwerty_preference", false)
                .putBoolean("sumire_restore_input_mode_on_restart_preference", false)
                .putBoolean("landscape_force_qwerty_preference", false)
                .putBoolean("live_conversion_preference", false)
                .putBoolean("learn_dictionary_preference", false)
                .putBoolean("flick_editor_preview_preference", false)
                .putBoolean("flick_input_only_preference", true)
                .putBoolean("independent_multi_touch_preference", true)
                .putBoolean("key_sound_preference", false)
                .putInt("long_press_timeout_preference", 300)
                .putString("split_keyboard_main_type", "SUMIRE")
                .putString("split_keyboard_sub_type", "SUMIRE")
                .putFloat("split_keyboard_main_portrait_width", 170f)
                .putFloat("split_keyboard_sub_portrait_width", 170f)
                .putFloat("split_keyboard_main_portrait_height", 200f)
                .putFloat("split_keyboard_sub_portrait_height", 200f)
                .commit())
            shell("ime enable $target")
            shell("ime set $target")
            // Launch through the system: waitForIdleSync can stall while an IME is rendering.
            shell("am start -n ${context.packageName}/${FastInputHostActivity::class.java.name}")
            awaitHost()
            try {
                key("あ")
                cycleAndType()
                if (surface == "split") {
                    // Each pane must preserve its own mode, including the first action in the other pane.
                    press(key("モード", rightmost = true))
                    key("ABC", rightmost = true)
                    press(key("ABC", rightmost = true))
                    awaitText("あa")
                }
                editor { it.restartEditorInput(clearText = true) }
                key("あ")
                press(key("モード"), holdMillis = 450)
                key("ABC")
                press(key("モード"))
                key("1") // A long press must advance once, and keep the next tap functional.
                press(key("モード"))
                key("あ")

                // Hide and show the same input hierarchy without editing any preferences.
                editor {
                    androidx.core.view.WindowCompat.getInsetsController(it.window, it.editText)
                        .hide(androidx.core.view.WindowInsetsCompat.Type.ime())
                }
                SystemClock.sleep(300)
                editor { it.requestImeForEditor() }
                key("あ")
                cycleAndType()

                shell("am start -n ${context.packageName}/${MainActivity::class.java.name}")
                val deadline = SystemClock.uptimeMillis() + 15000
                var settings: MainActivity? = null
                while (settings == null && SystemClock.uptimeMillis() < deadline) {
                    ins.runOnMainSync {
                        settings = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                            .filterIsInstance<MainActivity>().firstOrNull()
                    }
                    SystemClock.sleep(50)
                }
                checkNotNull(settings) { "Settings did not resume" }
                ins.runOnMainSync { settings!!.finish() }
                awaitHost()
                editor { it.restartEditorInput(clearText = true) }
                key("あ")
                cycleAndType()

                if (surface != "split") {
                    editor { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                    key("あ")
                    editor { it.restartEditorInput(clearText = true) }
                    key("あ")
                    cycleAndType()
                    editor { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                    key("あ")
                    editor { it.restartEditorInput(clearText = true) }
                    key("あ")
                }
                editor { it.restartEditorInput(clearText = true) }
                key("あ")
                // The globe action switches to QWERTY and returns to Sumire (or its split panes).
                val mode = key("モード")
                val a = key("あ"); val ta = key("た")
                press(Rect(mode).apply { offset(0, ta.centerY() - a.centerY()) })
                press(keyById("key_switch_default"))
                key("あ")
                cycleAndType()
                convertAndCommitJapanese()
            } finally {
                ins.runOnMainSync {
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<FastInputHostActivity>().forEach { it.finish() }
                }
            }
        } finally {
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
            if (!wasEnabled) shell("ime disable $target")
        }
    }

    private fun cycleAndType() {
        press(key("モード"))
        press(key("ABC"))
        awaitText("a")
        press(key("Del"))
        awaitText("")
        press(key("ABC"))
        press(key("確定"))
        awaitText("a")
        press(key("CursorMoveLeft"))
        press(key("空白"))
        awaitText(" a")
        press(key("Del"))
        awaitText("a")
        press(key("モード"))
        press(key("1"))
        awaitText("1a")
        press(key("モード"))
        key("あ")
        editor { it.restartEditorInput(clearText = true) }
        SystemClock.sleep(200)
        press(key("あ"))
        awaitText("あ")
    }

    private fun convertAndCommitJapanese() {
        editor { it.restartEditorInput(clearText = true) }
        key("か")
        SystemClock.sleep(200)
        press(key("か"))
        press(key("な"))
        awaitText("かな")
        press(key("変換"))
        SystemClock.sleep(500)
        var first = ""
        editor { first = it.editText.text.toString() }
        press(key("変換"))
        SystemClock.sleep(300)
        var second = ""
        editor { second = it.editText.text.toString() }
        assertTrue("$surface conversion has output", second.isNotEmpty())
        assertNotEquals("$surface conversion key advances the candidate", first, second)
        press(key("確定"))
        awaitText(second)
        editor { assertEquals(-1, BaseInputConnection.getComposingSpanStart(it.editText.text)) }
    }

    private fun awaitHost() {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            var resumed = false
            ins.runOnMainSync {
                resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .any { it is FastInputHostActivity }
            }
            if (resumed) return
            SystemClock.sleep(50)
        }
        error("Editor did not resume")
    }
    private fun editor(action: (FastInputHostActivity) -> Unit) {
        awaitHost()
        ins.runOnMainSync {
            val host = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<FastInputHostActivity>().single()
            action(host)
        }
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            result += node
            repeat(node.childCount) { node.getChild(it)?.let(::visit) }
        }
        automation.windows.forEach { it.root?.let(::visit) }
        return result.filter { it.packageName?.toString() == context.packageName && it.isVisibleToUser && it.isClickable }
    }
    private fun key(label: String, rightmost: Boolean = false): Rect = awaitKey {
        val labels = if (label == "モード") setOf("ABC", "123", "あいう") else setOf(label)
        val candidates = nodes().filter {
            it.text?.toString()?.lineSequence()?.firstOrNull()?.trim() in labels || it.contentDescription?.toString() in labels
        }.map { Rect().also(it::getBoundsInScreen) }.filter { !it.isEmpty }
        // The English character key is on the first row; the mode switch can now also say ABC.
        val inputCandidates = if (label == "ABC") candidates.filter { it.centerY() == candidates.minOfOrNull(Rect::centerY) } else candidates
        val ordered = inputCandidates.sortedBy { it.centerX() }
        if (rightmost) ordered.lastOrNull() else ordered.firstOrNull()
    }
    private fun keyById(id: String): Rect = awaitKey {
        nodes().firstOrNull { it.viewIdResourceName?.endsWith("/$id") == true }?.let { Rect().also(it::getBoundsInScreen) }
    }
    private fun awaitKey(find: () -> Rect?): Rect {
        val until = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < until) {
            find()?.let { return it }
            SystemClock.sleep(100)
        }
        error("Missing key on $surface: " + nodes().joinToString { "${it.text}/${it.contentDescription}/${it.viewIdResourceName}" })
    }
    private fun press(bounds: Rect, holdMillis: Long = 25) {
        val start = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            check(automation.injectInputEvent(event, true))
            event.recycle()
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(holdMillis)
        }
        SystemClock.sleep(180)
    }
    private fun awaitText(expected: String) {
        var actual = ""
        val until = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < until) {
            editor { actual = it.editText.text.toString() }
            if (actual == expected) return
            SystemClock.sleep(50)
        }
        assertEquals("surface=$surface", expected, actual)
    }
}
