package com.kazumaproject.markdownhelperkeyboard.mushroom

import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ResultReceiver
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kazumaproject.markdownhelperkeyboard.FastInputHostActivity
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.AppModule
import com.kazumaproject.markdownhelperkeyboard.repository.ShortcutRepository
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

/** Uses accessibility rather than WindowInspector, which is unavailable before API 29. */
@RunWith(AndroidJUnit4::class)
class MushroomCompatibilityInstrumentedTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context get() = ins.targetContext
    private val ui get() = ins.uiAutomation

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        ui.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }

    private fun roots(): List<AccessibilityNodeInfo> = ui.windows.mapNotNull { it.root }
        .ifEmpty { listOfNotNull(ui.rootInActiveWindow) }

    private fun await(message: String, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 60_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return
            SystemClock.sleep(100)
        }
        fail("$message; SDK=${android.os.Build.VERSION.SDK_INT}; windows=${ui.windows.map { it.type to it.root?.packageName }}")
    }

    private fun editor(): AccessibilityNodeInfo? = roots()
        .flatMap { it.findAccessibilityNodeInfosByViewId("android:id/input") }
        .firstOrNull { it.isVisibleToUser && it.packageName?.toString() == ins.context.packageName }

    private fun click(text: String): Boolean {
        val node = roots().flatMap { it.findAccessibilityNodeInfosByText(text) }
            .firstOrNull { it.isVisibleToUser } ?: return false
        return generateSequence(node) { it.parent }.firstOrNull { it.isClickable }
            ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun keyboardVisible(): Boolean = ui.windows.any {
        it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD && it.root != null
    }

    @Test(timeout = 180_000) fun mushroomReturnsToExternalEditorWithoutAnotherTap() {
        assumeTrue("Use an isolated applicationId containing mushroomtest", context.packageName.contains("mushroomtest"))
        val targetIme = "${context.packageName}/${IMEService::class.java.name}"
        val oldIme = shell("settings get secure default_input_method")
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putBoolean("shortcut_toolbar_visibility_preference", true)
            .putBoolean("shortcut_toolbar_integrated_in_suggestion_preference", false).commit()
        val db = AppModule.providesLearnDatabase(context)
        try {
            runBlocking { ShortcutRepository(db.shortcutDao()).updateShortcuts(listOf(ShortcutType.MUSHROOM, ShortcutType.CLIP_BOARD)) }
        } finally {
            db.close()
        }
        ui.serviceInfo = ui.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        try {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            await("IME registered") { shell("ime list -a -s").lineSequence().any { it.trim() == targetIme } }
            shell("ime enable $targetIme")
            shell("ime set $targetIme")
            await("IME selected") { shell("settings get secure default_input_method") == targetIme }
            shell("am start -n ${ins.context.packageName}/${MushroomPluginActivity::class.java.name} --ez editor true --ez plain_editor true")
            await("External editor visible") { editor() != null }
            assertTrue(requireNotNull(editor()).performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await("Select b and show the shortcut toolbar") {
                val node = editor()
                if (node?.textSelectionStart != 1 || node.textSelectionEnd != 2) {
                    node?.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 1)
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, 2)
                    })
                }
                editor()?.let { it.text?.toString() == "abc" && it.textSelectionStart == 1 && it.textSelectionEnd == 2 } == true &&
                    keyboardVisible() && roots().any { it.findAccessibilityNodeInfosByText(ShortcutType.MUSHROOM.description).isNotEmpty() }
            }
            assertTrue("Tap the actual toolbar shortcut", click(ShortcutType.MUSHROOM.description))
            await("Plugin chooser or direct launch") { click("Mushroom test plugin") || roots().any { it.findAccessibilityNodeInfosByText("Return result").isNotEmpty() } }
            await("Plugin received selected text") { roots().any { it.findAccessibilityNodeInfosByText("Source: b").isNotEmpty() } }
            await("Return button is clickable") { click("Return result") }
            await("Result and keyboard return without another editor tap") {
                editor()?.text?.toString() == "a[b]c" && keyboardVisible()
            }
        } finally {
            shell("input keyevent KEYCODE_BACK")
            shell("input keyevent KEYCODE_BACK")
            shell("am broadcast -a ${MushroomPluginActivity.ACTION_CLOSE} -p ${ins.context.packageName}")
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
            shell("ime disable $targetIme")
        }
    }

    @Test(timeout = 180_000) fun resultAfterChangingEditorIsDiscardedAndNotified() {
        assumeTrue("Use an isolated applicationId containing mushroomtest", context.packageName.contains("mushroomtest"))
        val targetIme = "${context.packageName}/${IMEService::class.java.name}"
        val oldIme = shell("settings get secure default_input_method")
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean("shortcut_toolbar_visibility_preference", true)
            .putBoolean("shortcut_toolbar_integrated_in_suggestion_preference", false).commit()
        val db = AppModule.providesLearnDatabase(context)
        try {
            runBlocking { ShortcutRepository(db.shortcutDao()).updateShortcuts(listOf(ShortcutType.MUSHROOM)) }
        } finally {
            db.close()
        }
        ui.serviceInfo = ui.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        try {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            shell("ime enable $targetIme")
            shell("ime set $targetIme")
            await("IME selected") { shell("settings get secure default_input_method") == targetIme }
            shell("am start -n ${context.packageName}/${FastInputHostActivity::class.java.name}")
            var host: FastInputHostActivity? = null
            await("Host window focused") {
                ins.runOnMainSync {
                    host = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<FastInputHostActivity>().firstOrNull { it.hasWindowFocus() }
                }
                host != null
            }
            val activity = requireNotNull(host)
            ins.runOnMainSync {
                activity.editText.setText("abc")
                activity.editText.setSelection(1, 2)
                activity.editText.requestFocus()
                activity.getSystemService(InputMethodManager::class.java).restartInput(activity.editText)
                activity.getSystemService(InputMethodManager::class.java).showSoftInput(activity.editText, 0)
            }
            await("Toolbar visible") {
                keyboardVisible() && roots().any { it.findAccessibilityNodeInfosByText(ShortcutType.MUSHROOM.description).isNotEmpty() }
            }
            await("Tap Mushroom") { click(ShortcutType.MUSHROOM.description) }
            await("Plugin chooser or direct launch") { click("Mushroom test plugin") || roots().any { it.findAccessibilityNodeInfosByText("Return result").isNotEmpty() } }
            await("Plugin received selected text") { roots().any { it.findAccessibilityNodeInfosByText("Source: b").isNotEmpty() } }
            var receiver: ResultReceiver? = null
            await("Bridge available") {
                ins.runOnMainSync {
                    val bridge = listOf(Stage.STOPPED, Stage.PAUSED, Stage.STARTED, Stage.RESUMED)
                        .flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }
                        .filterIsInstance<MushroomActivity>().firstOrNull()
                    receiver = if (Build.VERSION.SDK_INT >= 33) {
                        bridge?.intent?.getParcelableExtra(MushroomActivity.EXTRA_RECEIVER, ResultReceiver::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        bridge?.intent?.getParcelableExtra(MushroomActivity.EXTRA_RECEIVER)
                    }
                }
                receiver != null
            }
            lateinit var other: EditText
            ins.runOnMainSync {
                other = EditText(activity).apply { id = View.generateViewId(); setText("other"); setSelection(5) }
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                (content.getChildAt(0) as LinearLayout).addView(other, 1,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            shell("am start -n ${context.packageName}/${FastInputHostActivity::class.java.name} --activity-reorder-to-front")
            await("Host returned") {
                var focused = false
                ins.runOnMainSync { focused = activity.hasWindowFocus() }
                focused
            }
            ins.runOnMainSync {
                other.requestFocus()
                activity.getSystemService(InputMethodManager::class.java).restartInput(other)
                activity.getSystemService(InputMethodManager::class.java).showSoftInput(other, 0)
            }
            await("Different editor connected") {
                var active = false
                ins.runOnMainSync { active = activity.getSystemService(InputMethodManager::class.java).isActive(other) }
                active && shell("dumpsys input_method").contains("fieldId=${other.id}")
            }
            val noticeSeen = AtomicBoolean(false)
            val expectedNotice = context.getString(R.string.mushroom_target_changed)
            ui.setOnAccessibilityEventListener { event ->
                if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
                    event.text.any { it.toString() == expectedNotice }) noticeSeen.set(true)
            }
            try {
                requireNotNull(receiver).send(android.app.Activity.RESULT_OK, Bundle().apply {
                    putString(MushroomActivity.REPLACE_KEY, "[b]")
                })
                await("Result was rejected and the notice appeared") { noticeSeen.get() }
            } finally {
                ui.setOnAccessibilityEventListener(null)
            }
            ins.runOnMainSync {
                assertTrue(activity.editText.text.toString() == "abc")
                assertTrue(other.text.toString() == "other")
                activity.editText.requestFocus()
                activity.getSystemService(InputMethodManager::class.java).restartInput(activity.editText)
            }
            await("Original editor reconnected") {
                var active = false
                ins.runOnMainSync { active = activity.getSystemService(InputMethodManager::class.java).isActive(activity.editText) }
                active
            }
            ins.runOnMainSync { assertTrue(activity.editText.text.toString() == "abc") }
            ins.runOnMainSync { activity.finish() }
        } finally {
            shell("input keyevent KEYCODE_BACK")
            shell("input keyevent KEYCODE_BACK")
            shell("am broadcast -a ${MushroomPluginActivity.ACTION_CLOSE} -p ${ins.context.packageName}")
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
            shell("ime disable $targetIme")
        }
    }
}
