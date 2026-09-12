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
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Run with -I investigation/composing-guide.init.gradle on a dedicated emulator. */
@RunWith(AndroidJUnit4::class)
class ComposingGuideDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val automation get() = ins.uiAutomation
    private val context get() = ins.targetContext
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
        .bufferedReader().use { it.readText() }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val found = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            found += node
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        automation.windows.forEach { it.root?.let(::visit) }
        return found
    }
    private fun inGuide(node: AccessibilityNodeInfo): Boolean {
        var parent: AccessibilityNodeInfo? = node
        while (parent != null) {
            if (parent.viewIdResourceName?.endsWith(":id/composing_guide_root") == true) return true
            parent = parent.parent
        }
        return false
    }
    private fun displayed(node: AccessibilityNodeInfo): Boolean {
        if (node.isVisibleToUser) return true
        // Accessibility can retain old visibility bounds after moving an IME child. Touch and saved
        // geometry assertions below independently verify these nodes' actual on-screen positions.
        if (!inGuide(node)) return false
        val metrics = context.resources.displayMetrics
        val bounds = Rect().also(node::getBoundsInScreen)
        return !bounds.isEmpty && Rect(0, 0, metrics.widthPixels, metrics.heightPixels).contains(bounds)
    }
    private fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): Rect {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            nodes().firstOrNull { it.refresh(); displayed(it) && predicate(it) }?.let { return Rect().also(it::getBoundsInScreen) }
            SystemClock.sleep(100)
        }
        screenshot("failure")
        error("Missing node: " + nodes().filter { it.contentDescription != null || it.text != null }
            .joinToString { "${it.text}/${it.contentDescription} window=${it.window?.title} bounds=${Rect().also(it::getBoundsInScreen)}" })
    }
    private fun key() = awaitNode { !inGuide(it) && it.isClickable && it.text?.toString()?.lineSequence()?.firstOrNull()?.trim() == "あ" }
    private fun guide(description: Int) = awaitNode { inGuide(it) && it.contentDescription?.toString() == context.getString(description) }
    private fun shortcut(description: Int) = awaitNode { !inGuide(it) && it.contentDescription?.toString() == context.getString(description) }
    private fun assertGuideHidden() {
        SystemClock.sleep(250)
        assertFalse(nodes().any { inGuide(it) })
    }
    private fun gesture(rect: Rect, dx: Float = 0f, dy: Float = 0f) {
        val start = SystemClock.uptimeMillis()
        for (step in 0..6) {
            val action = when (step) { 0 -> MotionEvent.ACTION_DOWN; 6 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action,
                rect.exactCenterX() + dx * step / 6, rect.exactCenterY() + dy * step / 6, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            check(automation.injectInputEvent(event, true))
            event.recycle()
            SystemClock.sleep(20)
        }
        SystemClock.sleep(200)
    }
    private fun twoFingerResize(first: Rect, second: Rect, dx1: Float, dy1: Float, dx2: Float, dy2: Float) {
        val down = SystemClock.uptimeMillis()
        fun inject(action: Int, count: Int, fraction: Float) {
            val properties = Array(count) { index -> MotionEvent.PointerProperties().apply {
                id = if (index == 0) 7 else 19; toolType = MotionEvent.TOOL_TYPE_FINGER
            } }
            val coords = Array(count) { index -> MotionEvent.PointerCoords().apply {
                val rect = if (index == 0) first else second
                x = rect.exactCenterX() + fraction * if (index == 0) dx1 else dx2
                y = rect.exactCenterY() + fraction * if (index == 0) dy1 else dy2
                pressure = 1f; size = 1f
            } }
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, count,
                properties, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            check(automation.injectInputEvent(event, true)); event.recycle(); SystemClock.sleep(40)
        }
        inject(MotionEvent.ACTION_DOWN, 1, 0f)
        inject(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, 0f)
        for (step in 1..6) inject(MotionEvent.ACTION_MOVE, 2, step / 6f)
        inject(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, 1f)
        inject(MotionEvent.ACTION_MOVE, 1, 1f)
        inject(MotionEvent.ACTION_UP, 1, 1f)
        SystemClock.sleep(250)
    }
    private fun screenshot(name: String) {
        automation.takeScreenshot()?.let { bitmap ->
            java.io.File(context.getExternalFilesDir(null), "guide-$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }

    @Test fun editingHandlesVisibilityAndExistingModes() {
        check(context.packageName.startsWith("com.kazumaproject.composingguidetest"))
        val target = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val oldIme = shell("settings get secure default_input_method").trim()
        val alreadyEnabled = shell("ime list -s").lineSequence().any { it.trim() == target }
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        fun pref(key: String, value: Boolean) = ins.runOnMainSync { preferences.edit().putBoolean(key, value).commit(); Unit }
        try {
            preferences.edit().clear().putBoolean("live_conversion_preference", false)
                .putString("keyboard_order_preference", "[\"TENKEY\"]").commit()
            shell("ime enable $target"); shell("ime set $target")
            ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { scenario ->
                key(); SystemClock.sleep(1500)
                gesture(key())
                val originalKey = key()
                var editorBounds = Rect()
                scenario.onActivity { it.editText.getGlobalVisibleRect(editorBounds) }
                assertGuideHidden()
                pref("composing_guide_enabled", true)
                val edit = guide(R.string.composing_guide_edit)
                assertEquals(originalKey, key())
                scenario.onActivity {
                    val now = Rect(); it.editText.getGlobalVisibleRect(now)
                    assertEquals(editorBounds, now)
                }
                assertFalse(nodes().any { inGuide(it) && it.contentDescription?.toString() == context.getString(R.string.composing_guide_move) })
                val text = awaitNode { inGuide(it) && it.text?.toString() == "あ" }
                gesture(text, 40f, -30f)
                assertEquals("Normal mode must be locked", edit, guide(R.string.composing_guide_edit))
                screenshot("normal")

                gesture(edit)
                val move = guide(R.string.composing_guide_move)
                gesture(move, -35f, -90f)
                assertTrue(guide(R.string.composing_guide_move).top < move.top)
                for ((edge, delta) in listOf(
                    R.string.composing_guide_resize_left to (-20f to 0f),
                    R.string.composing_guide_resize_right to (20f to 0f),
                    R.string.composing_guide_resize_top to (0f to -20f),
                    R.string.composing_guide_resize_bottom to (0f to 20f))) {
                    gesture(guide(edge), delta.first, delta.second)
                }
                val width = preferences.getFloat("composing_guide_portrait_width", 0f)
                twoFingerResize(guide(R.string.composing_guide_resize_left), guide(R.string.composing_guide_resize_right), -15f, 0f, 15f, 0f)
                assertTrue(preferences.getFloat("composing_guide_portrait_width", 0f) > width)
                val height = preferences.getFloat("composing_guide_portrait_height", 0f)
                twoFingerResize(guide(R.string.composing_guide_resize_top), guide(R.string.composing_guide_resize_right), 0f, -15f, 10f, 0f)
                assertTrue(preferences.getFloat("composing_guide_portrait_height", 0f) > height)
                gesture(guide(R.string.composing_guide_text_size))
                assertTrue(preferences.getFloat("composing_guide_text_size", 28f) > 28f)
                screenshot("editing")
                gesture(guide(R.string.composing_guide_done))
                val beforeRestart = guide(R.string.composing_guide_edit)
                scenario.onActivity { assertTrue(it.editText.hasWindowFocus()); it.restartEditorInput(true) }
                assertEquals(beforeRestart, guide(R.string.composing_guide_edit))
                assertTrue(nodes().any { inGuide(it) && it.text?.toString() == context.getString(R.string.composing_guide_empty) })

                gesture(guide(R.string.composing_guide_hide))
                assertFalse(preferences.getBoolean("composing_guide_visible", true))
                assertTrue(preferences.getBoolean("composing_guide_enabled", false))
                assertGuideHidden()
                scenario.onActivity { it.restartEditorInput(true) }
                assertGuideHidden()
                gesture(shortcut(R.string.composing_guide_show))
                guide(R.string.composing_guide_edit)
                gesture(key())
                // Candidate mode must retain a way to restore a manually hidden guide.
                gesture(guide(R.string.composing_guide_hide)); assertGuideHidden()
                gesture(shortcut(R.string.composing_guide_show))
                guide(R.string.composing_guide_edit)
                screenshot("restored")
                gesture(awaitNode { it.viewIdResourceName?.endsWith(":id/suggestion_visibility") == true })
                SystemClock.sleep(350)
                gesture(guide(R.string.composing_guide_hide)); assertGuideHidden()
                gesture(shortcut(R.string.composing_guide_show))
                guide(R.string.composing_guide_edit)
                gesture(awaitNode { it.viewIdResourceName?.endsWith(":id/suggestion_visibility") == true })
                pref("composing_guide_enabled", false); assertGuideHidden()
                assertFalse(nodes().any { it.contentDescription?.toString() in listOf(context.getString(R.string.composing_guide_show), context.getString(R.string.composing_guide_hide)) })
                pref("composing_guide_enabled", true)
                pref("keyboard_floating_preference", true)
                scenario.onActivity { it.restartEditorInput(true) }; SystemClock.sleep(1000)
                gesture(key()); scenario.onActivity { assertTrue(it.editText.text.isNotEmpty()) }; assertGuideHidden()
                pref("keyboard_floating_preference", false)
                scenario.onActivity { it.restartEditorInput(true) }; SystemClock.sleep(1000)
                guide(R.string.composing_guide_edit)
                scenario.onActivity {
                    it.editText.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                    it.restartEditorInput(true)
                }
                assertGuideHidden()
            }
            ActivityScenario.launch<ComposingGuideWebViewHostActivity>(Intent(context, ComposingGuideWebViewHostActivity::class.java)).use {
                gesture(awaitNode { it.className?.toString() == "android.widget.EditText" })
                key(); SystemClock.sleep(1000); gesture(key())
                guide(R.string.composing_guide_edit)
                gesture(awaitNode { it.text?.toString()?.equals("Background button", ignoreCase = true) == true })
                awaitNode { it.text?.toString()?.equals("Background tapped", ignoreCase = true) == true }
            }
        } finally {
            if (oldIme.isNotEmpty() && oldIme != "null") shell("ime set $oldIme")
            if (!alreadyEnabled) shell("ime disable $target")
        }
    }
}
