package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.BaseInputConnection
import android.widget.PopupWindow
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
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
    // Ask the window for a matching node directly. Walking every child in both split
    // keyboard windows for each key press can spend seconds waiting for unrelated replies.
    private fun findNode(query: (AccessibilityNodeInfo) -> List<AccessibilityNodeInfo>,
                         predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        var found: AccessibilityNodeInfo? = null
        await("Missing cursor control") {
            if (android.os.Build.VERSION.SDK_INT >= 33) ui.clearCache()
            found = ui.windows.asSequence().mapNotNull { it.root }
                .flatMap { query(it).asSequence() }.firstOrNull { it.isVisibleToUser && predicate(it) }
            found != null
        }
        return requireNotNull(found)
    }
    private fun bounds(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)
    private fun byDescription(value: String, predicate: (AccessibilityNodeInfo) -> Boolean = { true }) =
        findNode({ it.findAccessibilityNodeInfosByText(value) }) { it.contentDescription == value && predicate(it) }
    private fun description(resource: Int) = byDescription(context.getString(resource)) {
        resource != R.string.floating_cursor_title || it.className == "android.widget.FrameLayout"
    }
    private fun key(id: String): AccessibilityNodeInfo {
        val resource = context.resources.getIdentifier(id, "id", context.packageName)
        check(resource != 0) { "Missing key resource: $id" }
        val name = context.resources.getResourceName(resource)
        return findNode({ it.findAccessibilityNodeInfosByViewId(name) }) { it.viewIdResourceName == name }
    }
    private fun send(down: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        check(ui.injectInputEvent(event, false)); event.recycle()
    }
    private fun sendPair(down: Long, action: Int, first: Pair<Float, Float>, second: Pair<Float, Float>) {
        val properties = Array(2) { index -> MotionEvent.PointerProperties().apply {
            id = index; toolType = MotionEvent.TOOL_TYPE_FINGER
        } }
        val coordinates = arrayOf(first, second).map { point -> MotionEvent.PointerCoords().apply {
            x = point.first; y = point.second; pressure = 1f; size = 1f
        } }.toTypedArray()
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 2, properties,
            coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        check(ui.injectInputEvent(event, false)); event.recycle()
    }
    private fun touch(rect: Rect, hold: Long = 40, dx: Float = 0f, dy: Float = 0f, settle: Long = 150,
                      duringHold: (() -> Unit)? = null) {
        val down = SystemClock.uptimeMillis(); val x = rect.exactCenterX(); val y = rect.exactCenterY()
        send(down, MotionEvent.ACTION_DOWN, x, y); SystemClock.sleep(hold)
        duringHold?.invoke()
        if (dx != 0f || dy != 0f) { send(down, MotionEvent.ACTION_MOVE, x + dx, y + dy); SystemClock.sleep(40) }
        send(down, MotionEvent.ACTION_UP, x + dx, y + dy); SystemClock.sleep(settle)
    }

    @Test @SdkSuppress(minSdkVersion = 24) fun selectionTrackpadGeometryAndFollowingInput() {
        org.junit.Assume.assumeTrue("Requires an isolated QA application ID", context.packageName.startsWith("com.kazumaproject.floatingqa"))
        val args = InstrumentationRegistry.getArguments()
        val surface = args.getString("surface") ?: "normal"
        val keyboard = args.getString("keyboard") ?: "ROMAJI"
        val plainEditor = args.getString("editor") in listOf("plain", "plain-filter")
        val expandingFilter = args.getString("editor") == "plain-filter"
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
            .putBoolean("qwerty_show_popup_window_preference", args.getString("preview") != "off")
            .putBoolean("shortcut_toolbar_visibility_preference", true)
            .putBoolean("conversion_key_swipe_cursor_move_preference", true)
            .putBoolean("independent_multi_touch_preference", args.getString("multitouch") == "independent")
            .putString("keyboard_skin_preference", args.getString("skin") ?: "default")
            .putBoolean("shortcut_toolbar_integrated_in_suggestion_preference", surface != "normal").commit()
        val settings = FloatingCursorSettings(prefs)
        if (surface == "split") {
            // Explicit side-by-side placements leave the upper cursor panel clear of
            // the keys. Default wide split panes may intentionally stack vertically.
            prefs.edit().apply {
                listOf("portrait", "landscape").forEach { orientation ->
                    listOf("main", "sub").forEach { slot ->
                        val prefix = "split_keyboard_${slot}_${orientation}_"
                        putFloat(prefix + "width", 180f).putFloat(prefix + "height", 240f)
                        putFloat(prefix + "x", if (slot == "main") 0f else 1f).putFloat(prefix + "y", 1f)
                    }
                    putFloat("floating_cursor_${orientation}_width", 300f)
                    putFloat("floating_cursor_${orientation}_height", 284f)
                    putFloat("floating_cursor_${orientation}_y", 0f)
                }
            }.commit()
        }
        assertFalse(settings.enabled)
        val db = AppModule.providesLearnDatabase(context)
        runBlocking { ShortcutRepository(db.shortcutDao()).updateShortcuts(listOf(ShortcutType.FLOATING_CURSOR) + if (surface == "floating") listOf(ShortcutType.KEYBOARD_FLOATING_TOGGLE) else emptyList()) }
        ui.serviceInfo = ui.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        try {
            shell("input keyevent WAKEUP"); shell("wm dismiss-keyguard")
            ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)
                .putExtra("plain_editor_snapshots", plainEditor)).use { scenario ->
                // Rotation can recreate the host. Always use the current native editor.
                fun editor(action: (FastInputHostActivity) -> Unit) { scenario.onActivity { action(it) } }
                fun selection() : Pair<Int, Int> { var value = -1 to -1; editor { value = minOf(it.editText.selectionStart, it.editText.selectionEnd) to maxOf(it.editText.selectionStart, it.editText.selectionEnd) }; return value }
                fun value(): String { var result = ""; editor { result = it.editText.text.toString() }; return result }
                fun setText(text: String, cursor: Int) {
                    editor { it.editText.setText(text); it.restartEditorInput(false); it.editText.setSelection(cursor) }
                    SystemClock.sleep(500)
                }
                fun icon() = byDescription(ShortcutType.FLOATING_CURSOR.description) { it.className == "android.widget.LinearLayout" }
                fun arrow(resource: Int) = touch(bounds(description(resource)))
                try {
                    // Leaving a NOSENSOR activity can restore the platform's saved rotation
                    // lock. Apply ours after the host has resumed and verify the actual display.
                    check(ui.setRotation(if (landscape) android.app.UiAutomation.ROTATION_FREEZE_90
                        else android.app.UiAutomation.ROTATION_FREEZE_0))
                    await("Requested screen orientation was not applied to the native activity") {
                        var actual = Configuration.ORIENTATION_UNDEFINED
                        editor { actual = it.resources.configuration.orientation }
                        actual == if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                    }
                    requireNotNull(ui.takeScreenshot()).let { bitmap ->
                        try {
                            assertEquals("Actual display orientation disagrees with the test argument",
                                landscape, bitmap.width > bitmap.height)
                        } finally { bitmap.recycle() }
                    }
                    shell("ime enable $target"); shell("ime set $target")
                    editor { it.restartEditorInput(true) }
                    SystemClock.sleep(600)
                    if (surface == "floating") {
                        check(byDescription(ShortcutType.KEYBOARD_FLOATING_TOGGLE.description).performAction(AccessibilityNodeInfo.ACTION_CLICK))
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
                        // The QWERTY space gesture dispatches distinct Up/Down callbacks to
                        // the IME. Both must retain the floating panel's selection mode.
                        listOf(12 to -40f, 2 to 40f).forEach { (caret, distance) ->
                            val original = "abcd\nefgh\nijkl"
                            setText(original, caret)
                            var expectedSelection = -1 to -1
                            editor { host ->
                                val layout = host.editText.layout
                                val line = layout.getLineForOffset(caret) + if (distance < 0) -1 else 1
                                val destination = layout.getOffsetForHorizontal(line, layout.getPrimaryHorizontal(caret))
                                expectedSelection = minOf(caret, destination) to maxOf(caret, destination)
                            }
                            touch(bounds(description(R.string.floating_cursor_selection_off)))
                            touch(bounds(key("key_space")), hold = 650, dy = distance * density)
                            await("QWERTY vertical gesture did not reach the adjacent line: expected $expectedSelection, actual ${selection()}") {
                                selection() == expectedSelection
                            }
                            description(R.string.floating_cursor_selection_on)
                            val selected = selection()
                            touch(bounds(key("key_a")))
                            val character = if (keyboard == "ROMAJI") "あ" else "a"
                            await("Input did not replace the vertical selection") {
                                value() == original.substring(0, selected.first) + character + original.substring(selected.second)
                            }
                            description(R.string.floating_cursor_selection_unavailable)
                            touch(bounds(key("key_return")))
                        }
                        editor { it.expandSingleLatinA = expandingFilter }
                        val expected = if (keyboard == "ROMAJI") "あ" else if (expandingFilter) "AA" else "a"
                        repeat(20) {
                            setText("", 0)
                            touch(bounds(description(R.string.floating_cursor_selection_off)))
                            touch(bounds(description(R.string.floating_cursor_trackpad)), dx = -30 * density, settle = 0)
                            assertFalse("Cursor panel covers the character key", Rect.intersects(
                                bounds(description(R.string.floating_cursor_title)), bounds(key("key_a"))))
                            touch(bounds(key("key_a")), settle = 0, duringHold =
                                if (it == 0 && args.getString("verify_preview") == "software" && android.os.Build.VERSION.SDK_INT >= 29)
                                    ({ ins.runOnMainSync { PreviewRendererProbe.assertSoftware() } }) else null)
                            await("Unexpected input after trackpad trial $it: expected=$expected actual=${value()}") { value() == expected }
                            if (expandingFilter && keyboard == "QWERTY") editor { host ->
                                assertEquals("Filter dropped native composing spans", 0,
                                    BaseInputConnection.getComposingSpanStart(host.editText.text))
                                assertEquals("Filter did not expand the native composing range", 2,
                                    BaseInputConnection.getComposingSpanEnd(host.editText.text))
                            }
                            description(R.string.floating_cursor_selection_unavailable)
                            touch(bounds(key("key_return")))
                            await("Commit lost after trackpad trial $it") { value() == expected }
                        }
                        if (expandingFilter && keyboard == "QWERTY") {
                            setText("", 0)
                            touch(bounds(key("key_a")))
                            await("Initial filtered preedit") { value() == "AA" }
                            touch(bounds(key("key_b")))
                            await("Plain length-changing editor lost continuing preedit") { value() == "AAb" }
                            editor { host ->
                                assertEquals(0, BaseInputConnection.getComposingSpanStart(host.editText.text))
                                assertEquals(3, BaseInputConnection.getComposingSpanEnd(host.editText.text))
                            }
                            touch(bounds(key("key_return")))
                            await("Filtered preedit was lost on commit") { value() == "AAb" }
                        }
                        setText("", 0)
                        touch(bounds(key("key_a")))
                        await("Initial preedit") { value() == expected }
                        editor { BaseInputConnection.removeComposingSpans(it.editText.text); it.editText.setSelection(0) }
                        touch(bounds(key("key_a")), settle = 0)
                        await("External caret then fresh input was lost") { value() == expected + expected }
                        touch(bounds(key("key_return")))
                        setText("", 0)
                        touch(bounds(key("key_a")))
                        await("Preedit before native completion") { value() == expected }
                        editor { BaseInputConnection.removeComposingSpans(it.editText.text) }
                        touch(bounds(key("key_a")), settle = 0)
                        await("Native completion at the same caret duplicated local preedit") { value() == expected + expected }
                        touch(bounds(key("key_return")))
                        editor { it.expandSingleLatinA = false }
                        if (args.getString("verify_preview") == "software" && android.os.Build.VERSION.SDK_INT >= 29) {
                            setText("", 0)
                            var variation: Char? = null
                            // Capture a deliberate long press separately from the tap trials.
                            // Screenshots can exceed the key's long-press deadline on emulators.
                            touch(bounds(key("key_a")), hold = 650, duringHold = {
                                ins.runOnMainSync { variation = PreviewRendererProbe.assertVariationSoftware() }
                                ui.takeScreenshot()?.let { bitmap ->
                                    val directory = File(context.filesDir, "floating-cursor-results").apply { mkdirs() }
                                    File(directory, "key-variation-${args.getString("skin") ?: "default"}-$surface-$keyboard.png")
                                        .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                    bitmap.recycle()
                                }
                            })
                            await("Long-press variation did not reach the editor: expected=$variation actual=${value()}") {
                                value() == variation.toString()
                            }
                            touch(bounds(key("key_return")))
                        }
                    }
                    if (args.getString("pointer_probe") == "on") {
                        // The first finger is already suppressed by default-mode rollover.
                        // Cursor movement and release must still follow the second, SPACE finger.
                        setText("abcd\nefgh\nijkl", 12)
                        val first = bounds(key("key_shift")).let { it.exactCenterX() to it.exactCenterY() }
                        val space = bounds(key("key_space")).let { it.exactCenterX() to it.exactCenterY() }
                        val down = SystemClock.uptimeMillis()
                        send(down, MotionEvent.ACTION_DOWN, first.first, first.second)
                        sendPair(down, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), first, space)
                        SystemClock.sleep(650)
                        check(description(R.string.floating_cursor_selection_off).performAction(AccessibilityNodeInfo.ACTION_CLICK))
                        var expectedSelection = -1 to -1
                        editor { host ->
                            val caret = host.editText.selectionEnd
                            val layout = host.editText.layout
                            val destination = layout.getOffsetForHorizontal(layout.getLineForOffset(caret) - 1,
                                layout.getPrimaryHorizontal(caret))
                            expectedSelection = minOf(caret, destination) to maxOf(caret, destination)
                        }
                        val moved = space.first to space.second - 40f * density
                        sendPair(down, MotionEvent.ACTION_MOVE, first, moved)
                        await("Second SPACE pointer did not select the previous line") { selection() == expectedSelection }
                        sendPair(down, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), first, moved)
                        await("SPACE pointer release did not restore character labels") { !key("key_a").text.isNullOrEmpty() }
                        send(down, MotionEvent.ACTION_MOVE, first.first + 50f * density, first.second)
                        send(down, MotionEvent.ACTION_UP, first.first + 50f * density, first.second)
                        SystemClock.sleep(200)
                        assertEquals("Suppressed finger moved the selection after SPACE release", expectedSelection, selection())
                        assertEquals("Suppressed finger entered text after SPACE release", "abcd\nefgh\nijkl", value())
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
                    if (plainEditor) editor {
                        assertTrue("Plain InputConnection was not queried", it.plainSnapshotReads > 0)
                        assertTrue("Native cursor composition was not requested", it.cursorAnchorRequests > 0)
                    }

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

    /** Isolated so API24's test discovery does not resolve the API29 inspector. */
    private object PreviewRendererProbe {
        private fun keyboards(): List<com.kazumaproject.qwerty_keyboard.ui.QWERTYKeyboardView> {
            fun descendants(view: View): List<View> = buildList {
                add(view)
                if (view is ViewGroup) for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
            }
            return android.view.inspector.WindowInspector.getGlobalWindowViews().flatMap(::descendants)
                .filterIsInstance<com.kazumaproject.qwerty_keyboard.ui.QWERTYKeyboardView>()
        }

        fun assertVariationSoftware(): Char {
            val (keyboard, popup) = keyboards().mapNotNull { keyboard ->
                val popup = keyboard.javaClass.getDeclaredField("variationPopup").apply { isAccessible = true }
                    .get(keyboard) as? PopupWindow
                popup?.takeIf { it.isShowing }?.let { keyboard to it }
            }.single()
            assertTrue("Keyboard hardware rendering was disabled", keyboard.isHardwareAccelerated)
            assertTrue("Variation popup was not attached", popup.contentView.isAttachedToWindow)
            assertFalse("Variation popup still owns a hardware renderer", popup.contentView.isHardwareAccelerated)
            return requireNotNull((popup.contentView as com.kazumaproject.qwerty_keyboard.ui.VariationsPopupView).getSelectedChar())
        }

        fun assertSoftware() {
            val previews = keyboards().flatMap { keyboard ->
                @Suppress("UNCHECKED_CAST")
                val popups = keyboard.javaClass.getDeclaredField("keyPreviewPopups").apply { isAccessible = true }
                    .get(keyboard) as Map<Int, PopupWindow>
                popups.values.filter { it.isShowing }.map { keyboard to it }
            }
            assertTrue("No visible key preview was inspected", previews.isNotEmpty())
            previews.forEach { (keyboard, popup) ->
                assertTrue("Keyboard hardware rendering was disabled", keyboard.isHardwareAccelerated)
                assertTrue("Key preview was not attached", popup.contentView.isAttachedToWindow)
                assertFalse("Key preview still owns a hardware renderer", popup.contentView.isHardwareAccelerated)
                assertTrue("Preview context changed the shared application flags",
                    keyboard.context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_HARDWARE_ACCELERATED != 0)
            }
        }
    }
}
