package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt in with -e candidate_height_device_test true on a disposable emulator. */
@RunWith(AndroidJUnit4::class)
class CandidateHeightStabilizationDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private val context get() = instrumentation.targetContext

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)
    ).bufferedReader().use { it.readText().trim() }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        error(message)
    }

    private fun find(node: AccessibilityNodeInfo?, id: String): Rect? {
        node ?: return null
        if (node.viewIdResourceName?.endsWith(":id/$id") == true) {
            return Rect().also(node::getBoundsInScreen).takeUnless { it.isEmpty }
        }
        for (index in 0 until node.childCount) {
            find(node.getChild(index), id)?.let { return it }
        }
        return null
    }

    private fun key(id: String): Rect? = automation.windows
        .filter { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        .firstNotNullOfOrNull { find(it.root, id) }

    private fun tap(x: Int, y: Int) {
        val downTime = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x.toFloat(), y.toFloat(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(automation.injectInputEvent(event, true))
            event.recycle()
        }
        SystemClock.sleep(250)
    }

    private fun tapKey(id: String) {
        var rect: Rect? = null
        await("Missing IME key $id") { key(id).also { rect = it } != null }
        tap(rect!!.centerX(), rect!!.centerY())
    }

    private fun editorBounds(host: CandidateHeightTestHostActivity): Rect {
        val rect = Rect()
        instrumentation.runOnMainSync {
            val position = IntArray(2)
            host.editor.getLocationOnScreen(position)
            rect.set(position[0], position[1], position[0] + host.editor.width, position[1] + host.editor.height)
        }
        return rect
    }

    private fun text(host: CandidateHeightTestHostActivity): String {
        var result = ""
        instrumentation.runOnMainSync { result = host.editor.text.toString() }
        return result
    }

    private data class Case(
        val columns: Int,
        val activeHeight: Int,
        val emptyHeight: Int = 60,
        val tab: Boolean = false,
        val toolbarHeight: Int = 0,
        val integrated: Boolean = false,
        val stable: Boolean = true,
        val skin: String = "default",
        val width: Int = 100
    )

    @Test
    fun actualImeKeepsEditorBoundsStableAndTransparentSpacePassesThrough() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("candidate_height_device_test") == "true")
        AppPreference.init(context)
        val backup = AppPreference.exportAllToJson()
        val oldIme = shell("settings get secure default_input_method")
        val target = ComponentName(context, IMEService::class.java).flattenToShortString()
        val originallyEnabled = shell("ime list -s").lineSequence().any { it.trim() == target }
        val rotation = InstrumentationRegistry.getArguments().getString("candidate_height_rotation") ?: "0"
        require(rotation == "0" || rotation == "1")
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val info = automation.serviceInfo
        val oldFlags = info.flags
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = info
        var host: CandidateHeightTestHostActivity? = null
        try {
            await("IME is not registered after installation") {
                shell("ime list -a -s").lineSequence().any { it.trim() == target }
            }
            shell("ime enable $target")
            shell("ime set $target")
            val cases = listOf(
                Case(1, 60, tab = true, toolbarHeight = 72),
                Case(2, 80),
                Case(3, 100, tab = true),
                Case(1, 60, emptyHeight = 120, toolbarHeight = 36),
                Case(2, 80, toolbarHeight = 36, integrated = true),
                Case(3, 100, tab = true, skin = "cupertino_classic"),
                Case(3, 100, width = 70),
                Case(3, 100, stable = false)
            )
            for ((index, case) in cases.withIndex()) {
                check(prefs.edit()
                    .putString("keyboard_order_preference", "[\"TENKEY\"]")
                    .putBoolean("save_last_used_keyboard", false)
                    .putBoolean("keyboard_floating_preference", false)
                    .putBoolean("allow_fullscreen_mode_preference", false)
                    .putBoolean("landscape_force_qwerty_preference", false)
                    .putInt("keyboard_height_landscape_preference", 180)
                    .putInt("keyboard_width_preference", case.width)
                    .putInt("keyboard_width_landscape_preference", case.width)
                    .putString("candidate_column_preference", case.columns.toString())
                    .putString("candidate_column_landscape_preference", case.columns.toString())
                    .putInt("candidate_view_height_dp_preference", case.activeHeight)
                    .putInt("candidate_view_height_dp_landscape_preference", case.activeHeight)
                    .putInt("candidate_view_height_landscape_column_${case.columns}_dp_preference", case.activeHeight)
                    .putInt("candidate_view_height_portrait_column_${case.columns}_dp_preference", case.activeHeight)
                    .putInt("candidate_view_empty_height_dp_preference", case.emptyHeight)
                    .putInt("candidate_view_empty_height_dp_landscape_preference", case.emptyHeight)
                    .putBoolean("candidate_height_per_column_migrated_preference", true)
                    .putInt("candidate_height_defaults_migration_version_preference", 1)
                    .putBoolean("candidate_tab_visibility_preference", case.tab)
                    .putBoolean("shortcut_toolbar_visibility_preference", case.toolbarHeight > 0)
                    .putInt("shortcut_toolbar_height_dp_preference", case.toolbarHeight.takeIf { it > 0 } ?: 36)
                    .putBoolean("shortcut_toolbar_integrated_in_suggestion_preference", case.integrated)
                    .putBoolean(AppPreference.STABILIZE_CANDIDATE_STRIP_HEIGHT_KEY, case.stable)
                    .putBoolean("live_conversion_preference", false)
                    .putBoolean("inline_suggestion_enabled_preference", false)
                    .putString("keyboard_skin_preference", case.skin)
                    .commit())
                if (host == null) {
                    context.startActivity(Intent(context, CandidateHeightTestHostActivity::class.java)
                        .putExtra("landscape", rotation == "1")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    await("Editor activity did not start") {
                        CandidateHeightTestHostActivity.current.get()?.also { host = it }?.hasWindowFocus() == true
                    }
                } else {
                    instrumentation.runOnMainSync { host!!.restartEditor() }
                }
                val activity = host!!
                await("Editor orientation did not change") {
                    activity.resources.configuration.orientation == if (rotation == "1") {
                        Configuration.ORIENTATION_LANDSCAPE
                    } else {
                        Configuration.ORIENTATION_PORTRAIT
                    }
                }
                await("IME did not start for case $case") { key("key_2") != null && text(activity).isEmpty() }
                SystemClock.sleep(500)
                val baseline = editorBounds(activity)
                assertTrue("Editor should consume IME insets", baseline.bottom < activity.resources.displayMetrics.heightPixels)
                assertTrue("Editor should have space above the IME", baseline.height() > 0)
                val states = mutableListOf(baseline)
                tapKey("key_2")
                await("Kana was not inserted: $case") { text(activity).contains("か") }
                states += editorBounds(activity)
                tapKey("key_space")
                states += editorBounds(activity)
                tapKey("key_enter")
                SystemClock.sleep(300)
                states += editorBounds(activity)
                tapKey("key_delete")
                await("Committed text was not deleted: $case") { text(activity).isEmpty() }
                states += editorBounds(activity)
                if (case.stable) {
                    assertTrue("Editor moved for $case: $states", states.all { it == baseline })
                } else {
                    assertTrue("OFF should retain variable height: $states", states.any { it != baseline })
                }
                if (case.stable && case.columns == 3 && case.skin == "default") {
                    var before = 0
                    instrumentation.runOnMainSync { before = activity.receivedTouches }
                    tap(baseline.centerX(), baseline.bottom + (20 * context.resources.displayMetrics.density).toInt())
                    var after = 0
                    instrumentation.runOnMainSync { after = activity.receivedTouches }
                    assertEquals("Transparent space should reach the editor window", before + 1, after)
                }
                println("CandidateHeight rotation=$rotation case=$index config=$case editorBounds=$states")
            }
        } finally {
            instrumentation.runOnMainSync { host?.finish() }
            AppPreference.importAllFromJson(backup)
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
            if (!originallyEnabled) shell("ime disable $target")
            info.flags = oldFlags
            automation.serviceInfo = info
        }
    }
}
