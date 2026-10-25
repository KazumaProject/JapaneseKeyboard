package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
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
import com.kazumaproject.gesture_keyboard.*
import com.kazumaproject.markdownhelperkeyboard.gesture.GesturePreferences
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real MotionEvents -> IME recognizer -> Japanese conversion -> editor InputConnection. */
@RunWith(AndroidJUnit4::class)
class GestureInputDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ui get() = ins.uiAutomation
    private val context get() = ins.targetContext
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command))
        .bufferedReader().use { it.readText().trim() }
    private fun await(message: String, predicate: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 45000
        while (!predicate()) {
            check(SystemClock.uptimeMillis() < end) { message }
            SystemClock.sleep(100)
        }
    }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val found = mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo) {
            if (node.isVisibleToUser) found.add(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let(::walk)
        }
        ui.windows.forEach { it.root?.let(::walk) }
        return found
    }
    private fun node(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        var result: AccessibilityNodeInfo? = null
        await("Missing gesture UI node") { result = nodes().firstOrNull(predicate); result != null }
        return result!!
    }
    private fun pad(side: GestureSide): Rect {
        val suffix = if (side == GestureSide.LEFT) "gesture_pad_left" else "gesture_pad_right"
        var previous: Rect? = null
        var stableSince = SystemClock.uptimeMillis()
        var result = Rect()
        // IME animations and candidate-strip resizing change screen coordinates after the
        // first visible accessibility frame. Capture only once the drawing surface settles.
        await("Gesture pad did not settle") {
            val current = nodes().firstOrNull { it.viewIdResourceName?.endsWith(":id/" + suffix) == true }
            if (current == null) false else {
                val bounds = Rect().also(current::getBoundsInScreen)
                if (previous != bounds) {
                    android.util.Log.d("GestureDeviceTest", "pad=" + side + " bounds=" + bounds)
                    previous = Rect(bounds); stableSince = SystemClock.uptimeMillis()
                }
                result = bounds
                bounds.width() > 0 && bounds.height() > 0 && SystemClock.uptimeMillis() - stableSince >= 450
            }
        }
        return result
    }
    private fun draw(side: GestureSide, entry: GestureEntry, cancel: Boolean = false) {
        val box = pad(side)
        val points = entry.samples.first()
        val minX = points.minOf { it.x }; val minY = points.minOf { it.y }
        val width = points.maxOf { it.x } - minX; val height = points.maxOf { it.y } - minY
        val scale = minOf(box.width() * .65f / width.coerceAtLeast(.01f), box.height() * .65f / height.coerceAtLeast(.01f))
        val down = SystemClock.uptimeMillis()
        points.forEachIndexed { i, p ->
            val x = box.exactCenterX() + (p.x - minX - width / 2) * scale
            val y = box.exactCenterY() + (p.y - minY - height / 2) * scale
            val action = if (i == 0) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_MOVE
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            assertTrue(ui.injectInputEvent(event, true)); event.recycle(); SystemClock.sleep(16)
        }
        val last = points.last()
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP,
            box.exactCenterX() + (last.x - minX - width / 2) * scale,
            box.exactCenterY() + (last.y - minY - height / 2) * scale, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        assertTrue(ui.injectInputEvent(event, true)); event.recycle()
    }
    private fun click(command: GestureCommand) {
        val labels = setOf(GestureLabels.command(command, true), GestureLabels.command(command, false))
        assertTrue(node { it.isClickable && it.contentDescription?.toString() in labels }.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    @Test fun normalInputConvertsKanaAndCommitsDigits() = exercise(false)
    @Test fun floatingInputConvertsKanaAndCommitsDigits() = exercise(true)
    private fun exercise(floating: Boolean) {
        val oldIme = shell("settings get secure default_input_method")
        val target = context.packageName + "/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val enabled = shell("settings get secure enabled_input_methods").split(':').any { it.substringBefore(';') == target }
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        AppPreference.init(context)
        val backup = AppPreference.exportAllToJson()
        ui.serviceInfo = ui.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        try {
            shell("input keyevent WAKEUP"); shell("wm dismiss-keyguard")
            preferences.edit().putString("keyboard_order_preference", """["GESTURE"]""")
                .putBoolean("save_last_used_keyboard", false).putBoolean("keyboard_floating_preference", floating)
                .putBoolean("landscape_force_qwerty_preference", false).putBoolean("landscape_force_qwerty_romaji_preference", false)
                .putBoolean("live_conversion_preference", false).putBoolean("learn_dictionary_preference", false)
                .putInt("keyboard_height_preference", 280).putInt("keyboard_width_preference", 100)
                .remove(GesturePreferences.SESSION).remove(GesturePreferences.LAYOUT).remove(GesturePreferences.SETS).commit()
            shell("ime enable " + target); shell("ime set " + target)
            ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { host ->
                host.onActivity { activity ->
                    activity.editText.requestFocus()
                    activity.editText.post {
                        (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                            .showSoftInput(activity.editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                    }
                }
                fun text(): String { var result = ""; host.onActivity { result = it.editText.text.toString() }; return result }
                val kana = GesturePresets.sets().first()
                var reading = ""
                for (character in listOf("に", "ほ", "ん")) {
                    draw(GestureSide.LEFT, kana.entries.single { it.id == character })
                    reading += character
                    await("Kana was not composed: " + reading + " / " + text()) { text() == reading }
                }
                host.onActivity { assertTrue(BaseInputConnection.getComposingSpanStart(it.editText.text) >= 0) }
                click(GestureCommand.CONVERT)
                await("Kana did not convert: " + text()) { text() != reading && text().isNotEmpty() }
                val preview = text()
                click(GestureCommand.ENTER)
                await("Conversion did not commit") {
                    var committed = false
                    host.onActivity { committed = BaseInputConnection.getComposingSpanStart(it.editText.text) == -1 }
                    committed
                }
                val converted = text()
                android.util.Log.i("GestureDeviceTest", "floating=" + floating + " preview=" + preview + " committed=" + converted)
                assertEquals("Committed candidate must match the preview", preview, converted)
                draw(GestureSide.RIGHT, GesturePresets.sets().last().entries.single { it.id == "7" })
                try { await("Digit was not committed") { text() == converted + "7" } }
                catch (error: Exception) { throw AssertionError("Expected [" + converted + "7], actual [" + text() + "]", error) }
                click(GestureCommand.DELETE)
                await("Delete did not remove digit") { text() == converted }
                draw(GestureSide.LEFT, kana.entries.single { it.id == "か" }, cancel = true)
                SystemClock.sleep(400)
                assertEquals(converted, text())
                // Real IME window dialog: verify its token and selecting the alphabet set.
                val selector = node { it.viewIdResourceName?.endsWith(":id/gesture_set_right") == true }
                assertTrue(selector.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                assertTrue(node { it.text?.toString() in setOf("Alphabet", "英字") }.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                draw(GestureSide.RIGHT, GesturePresets.sets()[1].entries.single { it.id == "a" })
                await("Alphabet set switch did not apply") { text() == converted + "a" }
                assertTrue(node { it.isClickable && it.text?.toString() == "a" }.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                draw(GestureSide.RIGHT, GesturePresets.sets()[1].entries.single { it.id == "b" })
                await("Uppercase setting did not apply") { text() == converted + "aB" }
            }
        } finally {
            AppPreference.importAllFromJson(backup)
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set " + oldIme)
            if (!enabled) shell("ime disable " + target)
        }
    }
}
