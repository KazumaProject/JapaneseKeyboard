package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.ime_service.floating_clipboard.FloatingClipboardView
import android.view.accessibility.AccessibilityNodeInfo
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.filters.SdkSuppress
import com.kazumaproject.core.data.clipboard.ClipboardItem
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ClipboardFileStore
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.AppModule
import com.kazumaproject.markdownhelperkeyboard.repository.ClipboardHistoryRepository
import com.kazumaproject.markdownhelperkeyboard.repository.ShortcutRepository
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Actual IME/window/touch test. Refuses to modify the user's installed keyboard data. */
@RunWith(AndroidJUnit4::class)
class FloatingClipboardDeviceTest {
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
    private fun await(message: String, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30000
        while (!predicate()) { check(SystemClock.uptimeMillis() < deadline) { message }; SystemClock.sleep(100) }
    }
    private fun node(check: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        var found: AccessibilityNodeInfo? = null
        await("Missing clipboard control") {
            found = nodes().firstOrNull(check); found != null
        }
        return requireNotNull(found)
    }
    private fun rect(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)
    private fun touch(r: Rect, hold: Long = 50, dx: Float = 0f, dy: Float = 0f) {
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            check(ui.injectInputEvent(event, false)); event.recycle()
        }
        val x = r.exactCenterX(); val y = r.exactCenterY()
        send(MotionEvent.ACTION_DOWN, x, y)
        SystemClock.sleep(hold)
        if (dx != 0f || dy != 0f) { send(MotionEvent.ACTION_MOVE, x + dx, y + dy); SystemClock.sleep(100) }
        send(MotionEvent.ACTION_UP, x + dx, y + dy)
        SystemClock.sleep(300)
    }

    @Test @SdkSuppress(minSdkVersion = 31) fun pastePinDeleteMoveResizeAndRestore() {
        org.junit.Assume.assumeTrue("Requires an isolated QA application ID", context.packageName.startsWith("com.kazumaproject.floatingqa"))
        val args = InstrumentationRegistry.getArguments()
        val surface = args.getString("surface") ?: "normal"
        val landscape = args.getString("rotation") == "landscape"
        val keyboard = args.getString("keyboard") ?: "TENKEY"
        val oldIme = shell("settings get secure default_input_method")
        val oldRotation = shell("settings get system user_rotation")
        val oldAutoRotate = shell("settings get system accelerometer_rotation")
        val target = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val wasEnabled = shell("settings get secure enabled_input_methods").split(':').any { it.substringBefore(';') == target }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear().putBoolean("save_last_used_keyboard", false)
            .putBoolean("learn_dictionary_preference", false)
            .putString("keyboard_order_preference", "[\"${if (surface == "split") "SPLIT" else keyboard}\"]")
            .putString("split_keyboard_main_type", keyboard).putString("split_keyboard_sub_type", "TENKEY")
            .putBoolean("keyboard_floating_preference", false)
            .putBoolean("landscape_force_qwerty_preference", false)
            .putBoolean("clipboard_history_preference", false)
            .putBoolean("shortcut_toolbar_visibility_preference", true)
            .putBoolean("shortcut_toolbar_integrated_in_suggestion_preference", surface != "normal")
            .putBoolean("allow_fullscreen_mode_preference", false).commit()
        val db = AppModule.providesLearnDatabase(context)
        val repository = ClipboardHistoryRepository(db.clipboardHistoryDao(), ClipboardFileStore(context))
        val fullText = "clipboard全文\n" + "長い本文😀".repeat(60)
        val deleteText = "delete-from-floating-panel"
        runBlocking {
            repository.deleteAll()
            repository.insertFromClipboard(ClipboardItem.Text(0, fullText), true)
            repository.insertFromClipboard(ClipboardItem.Text(0, deleteText))
            repository.insertFromClipboard(ClipboardItem.Image(0, Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)))
            ShortcutRepository(db.shortcutDao()).updateShortcuts(listOf(ShortcutType.FLOATING_CLIPBOARD, ShortcutType.CLIP_BOARD) + if (surface == "floating") listOf(ShortcutType.KEYBOARD_FLOATING_TOGGLE) else emptyList())
        }
        val seeded = runBlocking { repository.getHistorySnapshot() }
        val longItem = seeded.first { it.isPinned }
        val deleteItem = seeded.first { it.preview == deleteText }
        ui.serviceInfo = ui.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        try {
            shell("input keyevent WAKEUP"); shell("wm dismiss-keyguard")
            ui.setRotation(if (landscape) android.app.UiAutomation.ROTATION_FREEZE_90 else android.app.UiAutomation.ROTATION_FREEZE_0)
            ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { scenario ->
                var host: FastInputHostActivity? = null
                ins.runOnMainSync { host = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<FastInputHostActivity>().single() }
                fun onEditor(action: (FastInputHostActivity) -> Unit) = ins.runOnMainSync { action(requireNotNull(host)) }
                var receivedImage = false
                onEditor { activity ->
                    activity.editText.setOnReceiveContentListener(arrayOf("image/png")) { _, content ->
                        val uri = content.clip.getItemAt(0).uri
                        activity.contentResolver.openInputStream(uri)?.use { stream ->
                            val bitmap = BitmapFactory.decodeStream(stream)
                            receivedImage = bitmap?.width == 24 && bitmap.height == 24
                        }
                        null
                    }
                }
                try {
                shell("ime enable $target"); shell("ime set $target")
                onEditor { it.restartEditorInput(true) }
                fun icon() = node { it.contentDescription == ShortcutType.FLOATING_CLIPBOARD.description && it.className == "android.widget.LinearLayout" }
                fun description(resource: Int) = node {
                    it.contentDescription == context.getString(resource) &&
                        (resource != R.string.floating_clipboard_title || it.className == "android.widget.FrameLayout")
                }
                fun text(resource: Int) = node { it.text?.toString() == context.getString(resource) }
                fun preview(value: String): AccessibilityNodeInfo {
                    var found: AccessibilityNodeInfo? = null
                    // RecyclerView can retain an offset past a row moved by pinning.
                    ins.runOnMainSync {
                        fun reset(view: android.view.View, inPanel: Boolean = false) {
                            val panel = inPanel || view is FloatingClipboardView
                            if (panel && view is RecyclerView) view.scrollToPosition(0)
                            if (view is ViewGroup) for (i in 0 until view.childCount) reset(view.getChildAt(i), panel)
                        }
                        WindowInspector.getGlobalWindowViews().forEach { reset(it) }
                    }
                    SystemClock.sleep(150)
                    await("Missing clipboard item: $value") {
                        val currentNodes = nodes()
                        // A partially clipped image row can extend below the panel. Its label
                        // gives a visible touch target rather than the full recycled row bounds.
                        found = currentNodes.firstOrNull { it.text?.toString() == value }
                            ?: currentNodes.firstOrNull { it.contentDescription?.toString() == value }
                        if (found == null) nodes().firstOrNull { it.isScrollable && it.className.toString().contains("RecyclerView") &&
                            rect(it).top < rect(description(R.string.floating_clipboard_title)).bottom }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                        found != null
                    }
                    return requireNotNull(found)
                }
                SystemClock.sleep(600)
                if (surface == "floating") {
                    check(node { it.contentDescription == ShortcutType.KEYBOARD_FLOATING_TOGGLE.description }.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    SystemClock.sleep(800)
                }
                touch(rect(icon()))
                preview(longItem.preview)
                assertFalse(nodes().any { it.text?.toString() == context.getString(R.string.floating_clipboard_title) })
                val historySwitch = text(R.string.floating_clipboard_history)
                assertFalse(historySwitch.isChecked)
                touch(rect(historySwitch))
                await("Floating history switch did not enable recording") {
                    prefs.getBoolean("clipboard_history_preference", false)
                }
                touch(rect(preview(longItem.preview)))
                await("The full stored text was not pasted") {
                    var value = ""; onEditor { value = it.editText.text.toString() }; value == fullText
                }
                preview(deleteText) // Pasting keeps the panel open.
                touch(rect(preview(context.getString(R.string.floating_clipboard_image))))
                await("PNG content did not reach the editor") {
                    var received = false; onEditor { received = receivedImage }; received
                }
                touch(rect(preview(deleteText)), hold = 800)
                touch(rect(text(com.kazumaproject.symbol_keyboard.R.string.symbol_clipboard_action_pin)))
                await("Pin did not reach shared storage") { runBlocking { repository.getHistorySnapshot().first { it.id == deleteItem.id }.isPinned } }
                touch(rect(preview(deleteText)), hold = 800)
                touch(rect(text(com.kazumaproject.symbol_keyboard.R.string.symbol_clipboard_action_delete)))
                await("Delete did not reach shared storage") { runBlocking { repository.getHistorySnapshot().none { it.id == deleteItem.id } } }
                assertFalse(File(deleteItem.contentPath).exists())
                await("Deleted row remained visible") { nodes().none { it.text?.toString() == deleteText } }
                val beforeMove = rect(description(R.string.floating_clipboard_title))
                val grip = description(R.string.composing_guide_move)
                touch(rect(grip), dx = -45f, dy = 35f)
                val moved = rect(description(R.string.floating_clipboard_title))
                assertNotEquals(beforeMove, moved)
                touch(rect(description(R.string.composing_guide_edit)))
                touch(rect(description(R.string.composing_guide_resize_right)), dx = 50f)
                touch(rect(description(R.string.composing_guide_done)))
                val saved = rect(description(R.string.floating_clipboard_title))
                val prefix = "floating_clipboard_${if (landscape) "landscape" else "portrait"}_"
                assertTrue(prefs.contains(prefix + "width"))
                touch(rect(description(R.string.floating_dictionary_hide)))
                await("Close left the panel visible") { nodes().none { it.contentDescription == context.getString(R.string.floating_clipboard_title) && it.className == "android.widget.FrameLayout" } }
                touch(rect(icon()))
                val restored = rect(description(R.string.floating_clipboard_title))
                assertTrue("Width was not restored: $saved / $restored", kotlin.math.abs(saved.width() - restored.width()) <= 2)
                assertTrue(kotlin.math.abs(saved.left - restored.left) <= 2)
                assertTrue(kotlin.math.abs(saved.top - restored.top) <= 2)
                val screenshot = requireNotNull(ui.takeScreenshot())
                val directory = File(context.filesDir, "floating-clipboard-results").apply { mkdirs() }
                File(directory, "$surface-$keyboard-${if (landscape) "landscape" else "portrait"}.png").outputStream().use {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                screenshot.recycle()
                touch(rect(icon()))
                touch(rect(node { it.contentDescription == ShortcutType.CLIP_BOARD.description }))
                await("Deleted row remained in the original clipboard") { nodes().none { it.text?.toString() == deleteText } }
                val originalHistorySwitch = node { it.viewIdResourceName?.endsWith(":id/clipboard_tab_switch") == true }
                assertTrue("Original history switch did not receive the floating switch state", originalHistorySwitch.isChecked)
                touch(rect(originalHistorySwitch))
                await("Original history switch did not disable recording") {
                    !prefs.getBoolean("clipboard_history_preference", true)
                }
                touch(rect(node { it.viewIdResourceName?.endsWith(":id/return_jp_keyboard_button") == true }))
                touch(rect(icon()))
                assertFalse("Floating history switch did not receive the original switch state", text(R.string.floating_clipboard_history).isChecked)
                } catch (failure: Throwable) {
                    ui.takeScreenshot()?.let { bitmap ->
                        File(context.filesDir, "clipboard-failure.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        bitmap.recycle()
                    }
                    android.util.Log.e("FloatingClipboardTest", "Clipboard UI test failed", failure)
                    throw failure
                } finally {
                    if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
                    onEditor { it.finish() }
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
