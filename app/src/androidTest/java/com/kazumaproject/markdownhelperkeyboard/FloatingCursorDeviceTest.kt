package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.BaseInputConnection
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.AppModule
import com.kazumaproject.markdownhelperkeyboard.ime_service.floating_cursor.FloatingCursorSettings
import com.kazumaproject.markdownhelperkeyboard.repository.ShortcutRepository
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real editor, IME subwindow and injected touch events; requires an isolated QA application ID. */
@RunWith(AndroidJUnit4::class)
class FloatingCursorDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ui get() = ins.uiAutomation
    private val context get() = ins.targetContext
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command))
        .bufferedReader().use { it.readText().trim() }
    private fun nodes(): List<AccessibilityNodeInfo> {
        if (android.os.Build.VERSION.SDK_INT >= 33) ui.clearCache()
        return buildList {
        fun visit(node: AccessibilityNodeInfo) {
            if (node.isVisibleToUser) add(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        ui.windows.forEach { it.root?.let(::visit) }
        }
    }
    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (!condition()) { check(SystemClock.uptimeMillis() < deadline) { message }; SystemClock.sleep(50) }
    }
    private fun node(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        var found: AccessibilityNodeInfo? = null
        await("Missing cursor control") { found = nodes().firstOrNull(predicate); found != null }
        return requireNotNull(found)
    }
    private fun bounds(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)
    private fun description(resource: Int) = node { it.contentDescription == context.getString(resource) &&
        (resource != R.string.floating_cursor_title || it.className == "android.widget.FrameLayout") }
    private fun key(id: String) = node { it.viewIdResourceName?.endsWith(":id/$id") == true }
    private fun send(down: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        check(ui.injectInputEvent(event, false)); event.recycle()
    }
    private fun touch(rect: Rect, hold: Long = 40, dx: Float = 0f, dy: Float = 0f, settle: Long = 150) {
        val down = SystemClock.uptimeMillis(); val x = rect.exactCenterX(); val y = rect.exactCenterY()
        send(down, MotionEvent.ACTION_DOWN, x, y); SystemClock.sleep(hold)
        if (dx != 0f || dy != 0f) { send(down, MotionEvent.ACTION_MOVE, x + dx, y + dy); SystemClock.sleep(40) }
        send(down, MotionEvent.ACTION_UP, x + dx, y + dy); SystemClock.sleep(settle)
    }

    @Test @SdkSuppress(minSdkVersion = 31) fun selectionTrackpadGeometryAndFollowingInput() {
        org.junit.Assume.assumeTrue("Requires an isolated QA application ID", context.packageName.startsWith("com.kazumaproject.floatingqa"))
        val args = InstrumentationRegistry.getArguments()
        val surface = args.getString("surface") ?: "normal"
        val keyboard = args.getString("keyboard") ?: "ROMAJI"
        val landscape = args.getString("rotation") == "landscape"
        val oldIme = shell("settings get secure default_input_method")
        val oldRotation = shell("settings get system user_rotation")
        val oldAutoRotate = shell("settings get system accelerometer_rotation")
        val target = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val wasEnabled = shell("settings get secure enabled_input_methods").split(':').any { it.substringBefore(';') == target }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear().putBoolean("save_last_used_keyboard", false)
            .putString("keyboard_order_preference", "[\"${if (surface == "split") "SPLIT" else keyboard}\"]")
            .putString("split_keyboard_main_type", keyboard).putString("split_keyboard_sub_type", "TENKEY")
            .putBoolean("keyboard_floating_preference", false)
            .putBoolean("landscape_force_qwerty_preference", false)
            .putBoolean("allow_fullscreen_mode_preference", false)
            .putBoolean("learn_dictionary_preference", false)
            .putBoolean("live_conversion_preference", false)
            .putBoolean("shortcut_toolbar_visibility_preference", true)
            .putBoolean("shortcut_toolbar_integrated_in_suggestion_preference", surface != "normal").commit()
        val settings = FloatingCursorSettings(prefs)
        assertFalse(settings.enabled)
        val db = AppModule.providesLearnDatabase(context)
        runBlocking { ShortcutRepository(db.shortcutDao()).updateShortcuts(listOf(ShortcutType.FLOATING_CURSOR) + if (surface == "floating") listOf(ShortcutType.KEYBOARD_FLOATING_TOGGLE) else emptyList()) }
        ui.serviceInfo = ui.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        try {
            shell("input keyevent WAKEUP"); shell("wm dismiss-keyguard")
            ui.setRotation(if (landscape) android.app.UiAutomation.ROTATION_FREEZE_90 else android.app.UiAutomation.ROTATION_FREEZE_0)
            ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use {
                var host: FastInputHostActivity? = null
                ins.runOnMainSync { host = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<FastInputHostActivity>().single() }
                fun editor(action: (FastInputHostActivity) -> Unit) = ins.runOnMainSync { action(requireNotNull(host)) }
                fun selection() : Pair<Int, Int> { var value = -1 to -1; editor { value = minOf(it.editText.selectionStart, it.editText.selectionEnd) to maxOf(it.editText.selectionStart, it.editText.selectionEnd) }; return value }
                fun value(): String { var result = ""; editor { result = it.editText.text.toString() }; return result }
                fun setText(text: String, cursor: Int) {
                    editor { it.editText.setText(text); it.restartEditorInput(false); it.editText.setSelection(cursor) }
                    SystemClock.sleep(500)
                }
                fun icon() = node { it.contentDescription == ShortcutType.FLOATING_CURSOR.description && it.className == "android.widget.LinearLayout" }
                fun arrow(resource: Int) = touch(bounds(description(resource)))
                try {
                    shell("ime enable $target"); shell("ime set $target")
                    editor { it.restartEditorInput(true) }
                    SystemClock.sleep(600)
                    if (surface == "floating") {
                        check(node { it.contentDescription == ShortcutType.KEYBOARD_FLOATING_TOGGLE.description }.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                        SystemClock.sleep(800)
                    }
                    check(icon().performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    await("Toolbar did not enable the setting") { settings.enabled }
                    description(R.string.floating_cursor_title)
                    assertFalse(nodes().any { it.text?.toString() == context.getString(R.string.floating_cursor_title) })
                    val density = context.resources.displayMetrics.density
                    listOf(R.string.floating_cursor_left, R.string.floating_cursor_right, R.string.floating_cursor_up, R.string.floating_cursor_down).forEach {
                        val b = bounds(description(it)); assertTrue(b.width() >= 48 * density - 1); assertTrue(b.height() >= 48 * density - 1)
                    }
                    setText("abcdef", 3)
                    val labels = nodes().filter { it.viewIdResourceName?.contains(":id/key_") == true }.associate { it.viewIdResourceName to (it.text?.toString() to bounds(it)) }
                    touch(bounds(description(R.string.floating_cursor_selection_off)))
                    arrow(R.string.floating_cursor_left); await("First selection") { selection() == 2 to 3 }
                    arrow(R.string.floating_cursor_left); await("Extend selection") { selection() == 1 to 3 }
                    arrow(R.string.floating_cursor_right); await("Shrink selection") { selection() == 2 to 3 }
                    arrow(R.string.floating_cursor_right); await("Collapse at anchor") { selection() == 3 to 3 }
                    arrow(R.string.floating_cursor_right); await("Reverse selection") { selection() == 3 to 4 }
                    val afterLabels = nodes().filter { it.viewIdResourceName in labels.keys }.associate { it.viewIdResourceName to (it.text?.toString() to bounds(it)) }
                    assertEquals("Cursor selection changed character keys", labels, afterLabels)
                    touch(bounds(description(R.string.floating_cursor_selection_on)))
                    arrow(R.string.floating_cursor_left); await("Off must collapse the range") { selection() == 3 to 3 }
                    arrow(R.string.floating_cursor_left); await("Movement after collapse") { selection() == 2 to 2 }
                    touch(bounds(description(R.string.floating_cursor_trackpad)), dx = 42 * density)
                    await("Trackpad steps") { selection() == 4 to 4 }
                    setText("X👨‍👩‍👧‍👦e\u0301🇯🇵Y", 12)
                    arrow(R.string.floating_cursor_left); await("Emoji family boundary") { selection() == 1 to 1 }
                    setText("abcd\nefgh\nijkl", 12)
                    touch(bounds(description(R.string.floating_cursor_selection_off)))
                    arrow(R.string.floating_cursor_up)
                    await("Native vertical selection") { selection().first != selection().second }
                    touch(bounds(description(R.string.floating_cursor_selection_on)))

                    if (keyboard == "ROMAJI" || keyboard == "QWERTY") {
                        val expected = if (keyboard == "ROMAJI") "あ" else "a"
                        repeat(20) {
                            setText("", 0)
                            touch(bounds(description(R.string.floating_cursor_selection_off)))
                            touch(bounds(description(R.string.floating_cursor_trackpad)), dx = -30 * density, settle = 0)
                            touch(bounds(key("key_a")), settle = 0)
                            await("Input lost after trackpad trial $it") { value() == expected }
                            description(R.string.floating_cursor_selection_unavailable)
                            touch(bounds(key("key_return")))
                            await("Commit lost after trackpad trial $it") { value() == expected }
                        }
                        setText("", 0)
                        touch(bounds(key("key_a")))
                        await("Initial preedit") { value() == expected }
                        editor { BaseInputConnection.removeComposingSpans(it.editText.text); it.editText.setSelection(0) }
                        touch(bounds(key("key_a")), settle = 0)
                        await("External caret then fresh input was lost") { value() == expected + expected }
                        touch(bounds(key("key_return")))
                    }
                    setText("abcdef", 2)
                    val before = bounds(description(R.string.floating_cursor_title))
                    touch(bounds(description(R.string.composing_guide_move)), dx = -30 * density, dy = 15 * density)
                    assertNotEquals(before, bounds(description(R.string.floating_cursor_title)))
                    touch(bounds(description(R.string.composing_guide_edit)))
                    touch(bounds(description(R.string.composing_guide_resize_bottom)), dy = 20 * density)
                    touch(bounds(description(R.string.composing_guide_done)))
                    val saved = bounds(description(R.string.floating_cursor_title))
                    touch(bounds(description(R.string.floating_dictionary_hide)))
                    await("Close did not disable the setting") { !settings.enabled }
                    touch(bounds(icon()))
                    val restored = bounds(description(R.string.floating_cursor_title))
                    assertTrue(kotlin.math.abs(saved.width() - restored.width()) <= 2)
                    assertTrue(kotlin.math.abs(saved.height() - restored.height()) <= 2)
                    assertTrue(kotlin.math.abs(saved.left - restored.left) <= 2)
                    val screenshot = requireNotNull(ui.takeScreenshot())
                    val directory = File(context.filesDir, "floating-cursor-results").apply { mkdirs() }
                    File(directory, "$surface-$keyboard-${if (landscape) "landscape" else "portrait"}.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    screenshot.recycle()
                    ins.runOnMainSync { settings.resetPlacement() }
                    await("Reset did not restore placement") { bounds(description(R.string.floating_cursor_title)) != restored }
                    assertTrue(settings.enabled)
                    // Input while a held arrow is still down must cancel its repeat and release tap.
                    setText("abcdef", 2)
                    val held = bounds(description(R.string.floating_cursor_right))
                    val down = SystemClock.uptimeMillis()
                    send(down, MotionEvent.ACTION_DOWN, held.exactCenterX(), held.exactCenterY())
                    SystemClock.sleep(650)
                    val beforeInput = selection().second
                    val whenKey = SystemClock.uptimeMillis()
                    ui.injectInputEvent(KeyEvent(whenKey, whenKey, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0), false)
                    ui.injectInputEvent(KeyEvent(whenKey, whenKey, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A, 0), false)
                    await("Physical input while holding arrow") { value().length > 6 }
                    val afterInput = selection()
                    SystemClock.sleep(300)
                    send(down, MotionEvent.ACTION_UP, held.exactCenterX(), held.exactCenterY())
                    SystemClock.sleep(200)
                    assertEquals("Cancelled repeat/release moved fresh input", afterInput, selection())
                    assertTrue(beforeInput >= 2)

                } catch (failure: Throwable) {
                    ui.takeScreenshot()?.let { bitmap ->
                        File(context.filesDir, "cursor-failure.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        bitmap.recycle()
                    }
                    android.util.Log.e("FloatingCursorTest", "Cursor UI test failed", failure); throw failure
                } finally {
                    if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
                    editor { it.finish() }
                }
            }
        } finally {
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
            if (!wasEnabled) shell("ime disable $target")
            ui.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE)
            if (oldRotation != "null") shell("settings put system user_rotation $oldRotation")
            if (oldAutoRotate != "null") shell("settings put system accelerometer_rotation $oldAutoRotate")
        }
    }
}
