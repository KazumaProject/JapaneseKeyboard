package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingSearchIndex
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real touch/selection/window regression; must run in the isolated floatingqa application. */
@RunWith(AndroidJUnit4::class)
class FloatingCursorDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ui get() = ins.uiAutomation
    private val ctx get() = ins.targetContext
    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(ctx)
    private val density get() = ctx.resources.displayMetrics.density
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command))
        .bufferedReader().use { it.readText().trim() }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            if (node.isVisibleToUser) result.add(node)
            for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
        }
        ui.windows.filter { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD ||
            it.title?.toString() in listOf("Floating cursor", "Floating keyboard") ||
            it.title?.toString()?.startsWith("Split keyboard ") == true || it.isActive || it.isFocused }
            .forEach { it.root?.let(::visit) }
        return result
    }
    private fun await(message: String, predicate: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 15000
        do { if (predicate()) return; SystemClock.sleep(100) } while (SystemClock.uptimeMillis() < until)
        error(message + "; nodes=" + nodes().joinToString { "${it.text}/${it.contentDescription}" })
    }
    private fun node(description: String): AccessibilityNodeInfo {
        var found: AccessibilityNodeInfo? = null
        await("Missing $description") { found = nodes().firstOrNull { it.contentDescription?.toString() == description }; found != null }
        return requireNotNull(found)
    }
    private fun bounds(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)
    private fun tap(description: Int) = gesture(bounds(node(ctx.getString(description))))
    private fun selectButton(text: Int): AccessibilityNodeInfo {
        var found: AccessibilityNodeInfo? = null
        await("Missing selection button") { found = nodes().firstOrNull { it.text?.toString() == ctx.getString(text) }; found != null }
        return requireNotNull(found)
    }
    private fun gesture(rect: Rect, dx: Float = 0f, dy: Float = 0f) {
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            check(ui.injectInputEvent(event, true)); event.recycle()
        }
        val x = rect.exactCenterX(); val y = rect.exactCenterY()
        send(MotionEvent.ACTION_DOWN, x, y); SystemClock.sleep(40)
        if (dx != 0f || dy != 0f) { send(MotionEvent.ACTION_MOVE, x + dx, y + dy); SystemClock.sleep(80) }
        send(MotionEvent.ACTION_UP, x + dx, y + dy); SystemClock.sleep(200)
    }
    private fun selection(activity: FastInputHostActivity): Pair<Int, Int> {
        var value = 0 to 0
        ins.runOnMainSync { value = activity.editText.selectionStart to activity.editText.selectionEnd }
        return value
    }
    private fun kanaKey(id: String, dx: Float = 0f, dy: Float = 0f) {
        var keys = emptyList<AccessibilityNodeInfo>()
        await("Missing $id") { keys = nodes().filter { it.viewIdResourceName == "${ctx.packageName}:id/$id" }; keys.isNotEmpty() }
        var cursor = bounds(node(ctx.getString(R.string.floating_cursor_title)))
        if (keys.all { bounds(it).let { rect -> cursor.contains(rect.centerX(), rect.centerY()) } }) {
            // Floating surfaces may overlap. Move the cursor panel to the opposite corner before typing.
            val key = bounds(keys.first())
            val screen = ctx.resources.displayMetrics
            val x = if (key.centerX() < screen.widthPixels / 2) screen.widthPixels - cursor.width() else 0
            val y = if (key.centerY() < screen.heightPixels / 2) screen.heightPixels - cursor.height() - (48 * density).toInt() else 0
            val grip = Rect(cursor.left, cursor.bottom - (24 * density).toInt(), cursor.right, cursor.bottom)
            gesture(grip, (x - cursor.left).toFloat(), (y - cursor.top).toFloat())
            cursor = bounds(node(ctx.getString(R.string.floating_cursor_title)))
        }
        val key = keys.first { bounds(it).let { rect -> !cursor.contains(rect.centerX(), rect.centerY()) } }
        gesture(bounds(key), dx * density, dy * density)
    }
    private fun capture(name: String) {
        val bitmap = requireNotNull(ui.takeScreenshot())
        val directory = File(ctx.filesDir, "floating-cursor").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun requireIsolatedApp() {
        check(ctx.packageName.startsWith("com.kazumaproject.floatingqa")) { "Use investigation/floating-panel.init.gradle" }
        ui.serviceInfo = ui.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
    }

    @Test fun arrowsTrackpadSelectionPreeditAndGeometry() {
        requireIsolatedApp()
        val args = InstrumentationRegistry.getArguments()
        val surface = args.getString("surface") ?: "normal"
        val landscape = args.getString("rotation") == "landscape"
        val keyboard = args.getString("keyboard") ?: "TENKEY"
        val label = "$surface-$keyboard-${if (landscape) "landscape" else "portrait"}"
        val oldIme = shell("settings get secure default_input_method")
        val oldRotation = shell("settings get system user_rotation")
        val oldAuto = shell("settings get system accelerometer_rotation")
        val target = "${ctx.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val enabled = shell("settings get secure enabled_input_methods").contains(target)
        try {
            shell("input keyevent WAKEUP"); shell("wm dismiss-keyguard")
            shell("settings put system accelerometer_rotation 0")
            shell("settings put system user_rotation ${if (landscape) 1 else 0}")
            prefs.edit().clear().putBoolean("floating_cursor_enabled", true)
                .putString("keyboard_order_preference", "[\"${if (surface == "split") "SPLIT" else keyboard}\"]")
                .putString("split_keyboard_main_type", keyboard).putString("split_keyboard_sub_type", "TENKEY")
                .putBoolean("keyboard_floating_preference", surface == "floating")
                .putBoolean("save_last_used_keyboard", false)
                .putBoolean("live_conversion_preference", args.getString("live") == "true")
                .putBoolean("conversion_bunsetsu_separation_preference", args.getString("segments") == "true")
                .putBoolean("conversion_bunsetsu_cursor_move_preference", args.getString("segmentCursor") == "true")
                .putBoolean("qwerty_english_direct_input_preference", true).commit()
            ActivityScenario.launch<FastInputHostActivity>(Intent(ctx, FastInputHostActivity::class.java)).use { scenario ->
                var activity: FastInputHostActivity? = null
                scenario.onActivity { activity = it }
                fun onEditor(action: (FastInputHostActivity) -> Unit) = ins.runOnMainSync { action(requireNotNull(activity)) }
                try {
                    shell("ime enable $target"); shell("ime set $target")
                    onEditor { it.restartEditorInput(true) }
                    node(ctx.getString(R.string.floating_cursor_title))
                    onEditor {
                        it.editText.setText("abc👨‍👩‍👧‍👦e\u0301def\nsecond line\nthird line")
                        it.restartEditorInput(false)
                    }
                    selectButton(R.string.floating_cursor_selection_off)
                    onEditor { it.editText.setSelection(0) }
                    capture("$label-normal")
                    tap(R.string.floating_cursor_right)
                    await("Arrow did not move") { selection(requireNotNull(activity)) == (1 to 1) }
                    gesture(bounds(selectButton(R.string.floating_cursor_selection_off)))
                    tap(R.string.floating_cursor_right)
                    await("Selection not extended") { selection(requireNotNull(activity)) == (1 to 2) }
                    gesture(bounds(node(ctx.getString(R.string.floating_cursor_trackpad))), 40f * density, 0f)
                    await("Trackpad did not select whole emoji: ${selection(requireNotNull(activity))}") { selection(requireNotNull(activity)) == (1 to 14) }
                    capture("$label-selection")
                    tap(R.string.floating_cursor_left)
                    await("Selection did not shrink over emoji") { selection(requireNotNull(activity)) == (1 to 3) }
                    gesture(bounds(selectButton(R.string.floating_cursor_selection_on)))
                    assertEquals(1 to 3, selection(requireNotNull(activity)))
                    gesture(bounds(selectButton(R.string.floating_cursor_selection_off)))
                    tap(R.string.floating_cursor_left)
                    await("Re-enabled selection did not resume the retained anchor") { selection(requireNotNull(activity)) == (1 to 2) }
                    tap(R.string.floating_cursor_right)
                    await("Re-enabled selection did not extend") { selection(requireNotNull(activity)) == (1 to 3) }
                    gesture(bounds(selectButton(R.string.floating_cursor_selection_on)))
                    onEditor {
                        assertTrue(it.editText.onTextContextMenuItem(android.R.id.copy))
                        val clipboard = it.getSystemService(android.content.ClipboardManager::class.java)
                        assertEquals("bc", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
                    }
                    tap(R.string.floating_cursor_left)
                    await("OFF did not collapse at left edge") { selection(requireNotNull(activity)) == (1 to 1) }
                    gesture(bounds(node(ctx.getString(R.string.floating_cursor_trackpad))), 20f * density, 0f)
                    await("OFF trackpad still selected") { selection(requireNotNull(activity)) == (2 to 2) }
                    gesture(bounds(selectButton(R.string.floating_cursor_selection_off)))
                    tap(R.string.floating_cursor_down)
                    await("Vertical selection failed") { val (a, b) = selection(requireNotNull(activity)); a != b && maxOf(a, b) > 20 }
                    val vertical = selection(requireNotNull(activity))
                    gesture(bounds(selectButton(R.string.floating_cursor_selection_on)))
                    assertEquals(vertical, selection(requireNotNull(activity)))
                    onEditor {
                        val original = it.editText.text.toString()
                        val start = minOf(vertical.first, vertical.second)
                        val end = maxOf(vertical.first, vertical.second)
                        assertTrue(it.editText.onTextContextMenuItem(android.R.id.cut))
                        assertEquals(original.removeRange(start, end), it.editText.text.toString())
                        val clipboard = it.getSystemService(android.content.ClipboardManager::class.java)
                        assertEquals(original.substring(start, end), clipboard.primaryClip?.getItemAt(0)?.text?.toString())
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
                        it.editText.setText(original)
                        it.editText.setSelection(vertical.first, vertical.second)
                    }
                    tap(R.string.floating_cursor_up)
                    await("OFF vertical still selected") { val (a, b) = selection(requireNotNull(activity)); a == b }
                    val beforeMove = bounds(node(ctx.getString(R.string.floating_cursor_title)))
                    val grip = Rect(beforeMove.left, beforeMove.bottom - (24 * density).toInt(), beforeMove.right, beforeMove.bottom)
                    gesture(grip, 24f * density, -24f * density)
                    val moved = bounds(node(ctx.getString(R.string.floating_cursor_title)))
                    assertNotEquals(beforeMove, moved)
                    // The shared edit/check chrome and four resize handles must remain functional.
                    gesture(bounds(nodes().filter { it.contentDescription == ctx.getString(R.string.composing_guide_edit) }
                        .first { bounds(it).let { rect -> moved.contains(rect.centerX(), rect.centerY()) } }))
                    val edited = bounds(node(ctx.getString(R.string.floating_cursor_title)))
                    gesture(Rect(edited.right - (24 * density).toInt(), edited.centerY() - (24 * density).toInt(), edited.right,
                        edited.centerY() + (24 * density).toInt()), 16f * density, 0f)
                    capture("$label-resize")
                    val resized = bounds(node(ctx.getString(R.string.floating_cursor_title)))
                    assertTrue(resized.width() > moved.width())
                    gesture(bounds(nodes().first { it.contentDescription == ctx.getString(R.string.composing_guide_done) &&
                        resized.contains(bounds(it).centerX(), bounds(it).centerY()) }))
                    onEditor { it.restartEditorInput(true) }
                    selectButton(R.string.floating_cursor_selection_off)
                    if (keyboard == "TENKEY") {
                        kanaKey("key_1")
                        val unavailable = selectButton(R.string.floating_cursor_selection_unavailable)
                        assertFalse(unavailable.isEnabled)
                        capture("$label-preedit")
                        var text = ""; onEditor { text = it.editText.text.toString() }
                        tap(R.string.floating_cursor_up)
                        var after = ""; onEditor { after = it.editText.text.toString() }
                        assertEquals(text, after)
                        tap(R.string.floating_cursor_left) // tail-only composition must also disable selection.
                        assertFalse(selectButton(R.string.floating_cursor_selection_unavailable).isEnabled)
                        tap(R.string.floating_cursor_right)
                        assertFalse(selectButton(R.string.floating_cursor_selection_unavailable).isEnabled)
                    }
                    if (keyboard == "TENKEY") {
                        onEditor { it.restartEditorInput(true) }
                        selectButton(R.string.floating_cursor_selection_off)
                        kanaKey("key_11") // わ
                        kanaKey("key_4") // た
                        kanaKey("key_3", -48f) // し
                        kanaKey("key_6") // は
                        kanaKey("key_1") // あ
                        kanaKey("key_3", -48f) // し
                        kanaKey("key_4") // た
                        fun composing(): Boolean {
                            var active = false
                            onEditor { active = android.view.inputmethod.BaseInputConnection.getComposingSpanStart(it.editText.text) >= 0 }
                            return active
                        }
                        fun focusRange(): Pair<Int, Int>? {
                            var range: Pair<Int, Int>? = null
                            onEditor {
                                val text = it.editText.text
                                text.getSpans(0, text.length, android.text.style.BackgroundColorSpan::class.java).lastOrNull()?.let { span ->
                                    range = text.getSpanStart(span) to text.getSpanEnd(span)
                                }
                            }
                            return range
                        }
                        assertTrue("Typing unexpectedly committed preedit", composing())
                        kanaKey("key_space")
                        assertFalse(selectButton(R.string.floating_cursor_selection_unavailable).isEnabled)
                        // Live conversion may already have a single focused segment / a composing tail.
                        // Its navigation must preserve that actual state, rather than require two segments.
                        if (args.getString("live") != "true" && args.getString("segments") == "true" && args.getString("segmentCursor") == "true") {
                            await("Segment conversion did not render") {
                                var count = 0
                                onEditor { count = it.editText.text.getSpans(0, it.editText.length(), android.text.style.UnderlineSpan::class.java).size }
                                count > 1 && focusRange() != null
                            }
                            val first = focusRange()
                            tap(R.string.floating_cursor_right)
                            await("Right did not focus the next segment") { focusRange() != first }
                            tap(R.string.floating_cursor_left)
                            await("Left did not restore the first segment") { focusRange() == first }
                            capture("$label-segments")
                        }
                        for (direction in listOf(R.string.floating_cursor_left, R.string.floating_cursor_right,
                            R.string.floating_cursor_up, R.string.floating_cursor_down)) {
                            tap(direction)
                            assertTrue("Conversion navigation committed text", composing())
                        }
                        gesture(bounds(node(ctx.getString(R.string.floating_cursor_trackpad))), 40f * density, 0f)
                        gesture(bounds(node(ctx.getString(R.string.floating_cursor_trackpad))), 0f, 20f * density)
                        assertTrue("Trackpad navigation committed text", composing())
                        assertFalse(selectButton(R.string.floating_cursor_selection_unavailable).isEnabled)
                    }
                    val savedBounds = bounds(node(ctx.getString(R.string.floating_cursor_title)))
                    prefs.edit().putBoolean("floating_cursor_enabled", false).commit()
                    await("Disabled panel remains") { nodes().none { it.contentDescription == ctx.getString(R.string.floating_cursor_title) } }
                    prefs.edit().putBoolean("floating_cursor_enabled", true).commit()
                    val restored = bounds(node(ctx.getString(R.string.floating_cursor_title)))
                    assertEquals(savedBounds.width(), restored.width())
                    assertEquals(savedBounds.height(), restored.height())
                    assertTrue(kotlin.math.abs(savedBounds.left - restored.left) <= 2)
                    assertTrue(kotlin.math.abs(savedBounds.top - restored.top) <= 2)
                    assertTrue(prefs.contains("floating_cursor_${if (landscape) "landscape" else "portrait"}_width"))
                } finally { onEditor { it.finish() } }
            }
        } finally {
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
            if (!enabled) shell("ime disable $target")
            if (oldRotation != "null") shell("settings put system user_rotation $oldRotation")
            if (oldAuto != "null") shell("settings put system accelerometer_rotation $oldAuto")
        }
    }

    @Test fun settingsBothScreensShareToggleAndResetAndAreSearchable() {
        requireIsolatedApp()
        prefs.edit().putBoolean("floating_cursor_enabled", true).commit()
        for ((name, destination) in listOf("new" to R.id.keyboardDisplayPreferenceFragment, "legacy" to R.id.legacyCommonPreferenceFragment)) {
            prefs.edit().putBoolean("setting_use_new_home_screen_preference", name == "new").commit()
            ActivityScenario.launch<MainActivity>(Intent(ctx, MainActivity::class.java)).use { scenario ->
                await("Settings not ready") { var ready = false; scenario.onActivity { ready = it.isSettingsContentReady }; ready }
                scenario.onActivity {
                    val host = it.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                    host.navController.navigate(destination)
                }
                await("Preferences not ready") {
                    var ready = false
                    scenario.onActivity {
                        val host = it.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                        val fragment = host.childFragmentManager.primaryNavigationFragment as? PreferenceFragmentCompat
                        ready = fragment?.findPreference<androidx.preference.Preference>("floating_cursor_enabled") != null
                    }
                    ready
                }
                scenario.onActivity {
                    val host = it.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                    val fragment = host.childFragmentManager.primaryNavigationFragment as PreferenceFragmentCompat
                    val toggle = fragment.findPreference<androidx.preference.SwitchPreferenceCompat>("floating_cursor_enabled")!!
                    assertEquals(name == "new", toggle.isChecked)
                    assertNotNull(fragment.findPreference<androidx.preference.Preference>("floating_cursor_reset"))
                    fragment.scrollToPreference("floating_cursor_enabled")
                }
                SystemClock.sleep(400); capture("settings-$name")
                gesture(bounds(nodes().first { it.text == ctx.getString(R.string.floating_cursor_title) }))
                assertEquals(name != "new", prefs.getBoolean("floating_cursor_enabled", false))
                prefs.edit().putFloat("floating_cursor_portrait_width", 400f).commit()
                scenario.onActivity {
                    val host = it.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                    (host.childFragmentManager.primaryNavigationFragment as PreferenceFragmentCompat).scrollToPreference("floating_cursor_reset")
                }
                await("Missing reset") { nodes().any { it.text == ctx.getString(R.string.floating_cursor_reset) } }
                gesture(bounds(nodes().first { it.text == ctx.getString(R.string.floating_cursor_reset) }))
                assertFalse(prefs.contains("floating_cursor_portrait_width"))
            }
        }
        assertTrue(SettingSearchIndex.searchable(ctx).any { it.key == "floating_cursor_enabled" })
        assertTrue(SettingSearchIndex.legacySearchable(ctx).any { it.key == "floating_cursor_enabled" })
    }
}
