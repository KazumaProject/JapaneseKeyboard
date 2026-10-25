package com.kazumaproject.markdownhelperkeyboard.mushroom

import android.content.ContextWrapper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inspector.WindowInspector
import android.view.inputmethod.InputMethodManager
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.FastInputHostActivity
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MushroomImeInstrumentedTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context get() = ins.targetContext
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        ins.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun await(message: String, condition: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 60000
        while (SystemClock.uptimeMillis() < until) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        val focus = shell("dumpsys window").lineSequence()
            .filter { "mCurrentFocus" in it || "mFocusedApp" in it }.take(2).joinToString()
        fail("$message; defaultIme=${shell("settings get secure default_input_method").trim()}; $focus")
    }
    private fun prepareIme(target: String) {
        ins.uiAutomation.serviceInfo = ins.uiAutomation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        await("IME registered") { shell("ime list -a -s").lineSequence().any { it.trim() == target } }
        shell("ime enable $target")
        shell("ime set $target")
        await("IME selected") { shell("settings get secure default_input_method").trim() == target }
    }
    private fun roots(): List<AccessibilityNodeInfo> = ins.uiAutomation.windows.mapNotNull { it.root }
        .ifEmpty { listOfNotNull(ins.uiAutomation.rootInActiveWindow) }
    private fun hasText(text: String): Boolean = roots().any { root ->
        root.findAccessibilityNodeInfosByText(text).any { it.isVisibleToUser }
    }
    private fun service(): IMEService? = WindowInspector.getGlobalWindowViews().asSequence()
        .flatMap { root ->
            val view = root.findViewById<View>(R.id.suggestion_recycler_view) ?: root
            generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
        }.filterIsInstance<IMEService>().firstOrNull()
    private fun click(text: String): Boolean {
        val node = roots().flatMap { it.findAccessibilityNodeInfosByText(text) }
            .firstOrNull { it.isVisibleToUser } ?: return false
        return generateSequence(node) { it.parent }.firstOrNull { it.isClickable }
            ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun onHost(host: FastInputHostActivity, action: (FastInputHostActivity) -> Unit) {
        ins.runOnMainSync { action(host) }
    }
    private fun withHost(action: (FastInputHostActivity) -> Unit) {
        shell("am start -n ${context.packageName}/${FastInputHostActivity::class.java.name}")
        var host: FastInputHostActivity? = null
        await("Host window focused") {
            ins.runOnMainSync {
                host = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<FastInputHostActivity>()
                    .firstOrNull { it.hasWindowFocus() && !it.isFinishing }
            }
            host != null
        }
        val activity = requireNotNull(host)
        try {
            action(activity)
        } finally {
            onHost(activity) { it.finish() }
        }
    }

    @Test(timeout = 180_000) fun returnsToExternalEditorWhenKeyboardAppAlreadyHasATask() {
        assumeTrue("Use an isolated applicationId containing mushroomtest", context.packageName.contains("mushroomtest"))
        val targetIme = "${context.packageName}/${IMEService::class.java.name}"
        val oldIme = shell("settings get secure default_input_method").trim()
        try {
            prepareIme(targetIme)
            withHost {
                shell("am start -n ${ins.context.packageName}/${MushroomPluginActivity::class.java.name} --ez editor true")
                await("External editor connected") {
                    var ready = false
                    ins.runOnMainSync {
                        val ime = service()
                        val text = ime?.currentInputConnection?.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
                        ready = ime != null && text != null && ime.currentInputEditorInfo?.packageName == ins.context.packageName &&
                            ime.isInputViewShown && text.text?.toString() == "abc" && text.selectionStart == 1 && text.selectionEnd == 2
                    }
                    ready
                }
                ins.runOnMainSync {
                    IMEService::class.java.getDeclaredMethod("launchMushroom").apply { isAccessible = true }.invoke(service()!!)
                }
                await("Plugin chooser or direct launch") { hasText("Return result") || click("Mushroom test plugin") }
                await("Plugin opened") { hasText("Return result") }
                assertTrue(click("Return result"))
                await("Returned to the external editor with the replacement") {
                    roots().any { root ->
                        root.packageName?.toString() == ins.context.packageName &&
                            root.findAccessibilityNodeInfosByViewId("android:id/input")
                                .any { it.isVisibleToUser && it.text?.toString() == "a[b]c" }
                    }
                }
                shell("input keyevent KEYCODE_BACK")
                shell("input keyevent KEYCODE_BACK")
            }
        } finally {
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
            shell("ime disable $targetIme")
        }
    }

    @Test(timeout = 180_000) fun externalPluginRoundTripCancellationMissingResultAndChangedTarget() {
        // Keep the user's installed keyboard and preferences separate from device tests.
        assumeTrue("Run with an isolated applicationId containing mushroomtest",
            context.packageName.contains("mushroomtest"))
        val targetIme = "${context.packageName}/${IMEService::class.java.name}"
        val oldIme = shell("settings get secure default_input_method").trim()
        try {
            prepareIme(targetIme)
            withHost { host ->
                fun editor(text: String, start: Int, end: Int = start) {
                    onHost(host) {
                        it.editText.setText(text)
                        it.editText.setSelection(start, end)
                        it.getSystemService(InputMethodManager::class.java).restartInput(it.editText)
                        it.editText.requestFocus()
                        it.getSystemService(InputMethodManager::class.java).showSoftInput(it.editText, 0)
                    }
                    await("Editor connection matches the test input") {
                        var ready = false
                        ins.runOnMainSync {
                            val ime = service()
                            val snapshot = ime?.currentInputConnection?.getExtractedText(
                                android.view.inputmethod.ExtractedTextRequest(), 0)
                            ready = ime?.isInputViewShown == true && snapshot != null && snapshot.text?.toString() == text &&
                                snapshot.selectionStart == start && snapshot.selectionEnd == end
                        }
                        ready
                    }
                }
                fun launch(preedit: String? = null) {
                    await("IME is visible") {
                        var ready = false
                        ins.runOnMainSync { ready = service()?.isInputViewShown == true }
                        ready
                    }
                    if (preedit != null) {
                        ins.runOnMainSync {
                            val ime = service()!!
                            @Suppress("UNCHECKED_CAST")
                            val input = IMEService::class.java.getDeclaredField("_inputString")
                                .apply { isAccessible = true }.get(ime) as kotlinx.coroutines.flow.MutableStateFlow<String>
                            input.value = preedit
                            ime.setComposingText(preedit, 1)
                        }
                        SystemClock.sleep(300)
                    }
                    ins.runOnMainSync {
                        IMEService::class.java.getDeclaredMethod("launchMushroom").apply { isAccessible = true }
                            .invoke(service()!!)
                    }
                    await("Plugin chooser or direct launch") { hasText("Return result") || click("Mushroom test plugin") }
                    await("Plugin opened") {
                        hasText("Return result")
                    }
                }
                fun textIs(expected: String) = await("Editor should contain $expected") {
                    var matches = false
                    onHost(host) { matches = it.editText.text.toString() == expected }
                    var completed = false
                    ins.runOnMainSync {
                        val ime = service()
                        completed = ime != null && IMEService::class.java.getDeclaredField("mushroomTarget")
                            .apply { isAccessible = true }.get(ime) == null
                    }
                    var focused = false
                    onHost(host) { focused = it.hasWindowFocus() }
                    matches && completed && focused
                }
                editor("abc", 1, 2)
                launch()
                assertTrue(hasText("Source: b"))
                assertTrue(click("Return result"))
                textIs("a[b]c")

                editor("recreate", 0, 8)
                launch()
                ins.runOnMainSync {
                    val bridges = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.STOPPED)
                        .filterIsInstance<MushroomActivity>()
                    assertEquals(1, bridges.size)
                    bridges.single().recreate()
                }
                SystemClock.sleep(500)
                assertTrue(click("Return result"))
                textIs("[recreate]")

                editor("前後", 1)
                launch("かな")
                assertTrue(hasText("Source: かな"))
                assertTrue(click("Return result"))
                textIs("前[かな]後")

                editor("", 0)
                launch()
                assertTrue(click("Return result"))
                textIs("[]")

                for (action in listOf("Cancel", "Missing result")) {
                    editor("keep", 1, 3)
                    launch()
                    assertTrue(click(action))
                    textIs("keep")
                }

                editor("abc", 1, 2)
                launch()
                // Change the original editor while the plugin is active.
                onHost(host) { it.editText.setText("changed") }
                assertTrue(click("Return result"))
                textIs("changed")

                editor("secret", 0, 6)
                onHost(host) {
                    it.editText.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                    it.getSystemService(InputMethodManager::class.java).restartInput(it.editText)
                }
                SystemClock.sleep(800)
                ins.runOnMainSync {
                    IMEService::class.java.getDeclaredMethod("launchMushroom").apply { isAccessible = true }
                        .invoke(service()!!)
                }
                textIs("secret")
                onHost(host) { it.editText.inputType = android.text.InputType.TYPE_CLASS_TEXT }

                // A missing plugin must return control without touching the original text.
                val plugin = "${ins.context.packageName}/${MushroomPluginActivity::class.java.name}"
                shell("pm disable $plugin")
                try {
                    editor("keep", 4)
                    ins.runOnMainSync {
                        IMEService::class.java.getDeclaredMethod("launchMushroom").apply { isAccessible = true }
                            .invoke(service()!!)
                    }
                    await("Unavailable request completed") {
                        var completed = false
                        ins.runOnMainSync {
                            val ime = service()
                            completed = ime != null && IMEService::class.java.getDeclaredField("mushroomTarget")
                                .apply { isAccessible = true }.get(ime) == null
                        }
                        completed
                    }
                    textIs("keep")
                } finally {
                    shell("pm enable $plugin")
                }
            }
        } finally {
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
            shell("ime disable $targetIme")
        }
    }
}
