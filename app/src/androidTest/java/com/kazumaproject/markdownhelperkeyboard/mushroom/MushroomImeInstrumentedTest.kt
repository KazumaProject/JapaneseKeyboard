package com.kazumaproject.markdownhelperkeyboard.mushroom

import android.content.ContextWrapper
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inspector.WindowInspector
import android.view.inputmethod.InputMethodManager
import androidx.test.core.app.ActivityScenario
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
        fail(message)
    }
    private fun service(): IMEService? = WindowInspector.getGlobalWindowViews().asSequence()
        .flatMap { root ->
            val view = root.findViewById<View>(R.id.suggestion_recycler_view) ?: root
            generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
        }.filterIsInstance<IMEService>().firstOrNull()
    private fun click(text: String): Boolean {
        val node = ins.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)
            ?.firstOrNull() ?: return false
        return generateSequence(node) { it.parent }.firstOrNull { it.isClickable }
            ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    @Test fun externalPluginRoundTripCancellationMissingResultAndChangedTarget() {
        // Keep the user's installed keyboard and preferences separate from device tests.
        assumeTrue("Run with an isolated applicationId containing mushroomtest",
            context.packageName.contains("mushroomtest"))
        val targetIme = "${context.packageName}/${IMEService::class.java.name}"
        val oldIme = shell("settings get secure default_input_method").trim()
        try {
            shell("ime enable $targetIme")
            shell("ime set $targetIme")
            val intent = Intent(context, FastInputHostActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ActivityScenario.launch<FastInputHostActivity>(intent).use { scenario ->
                fun editor(text: String, start: Int, end: Int = start) {
                    scenario.onActivity {
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
                    await("Plugin chooser") { click("Mushroom test plugin") }
                    await("Plugin opened") {
                        ins.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Return result")?.isNotEmpty() == true
                    }
                }
                fun textIs(expected: String) = await("Editor should contain $expected") {
                    var matches = false
                    scenario.onActivity { matches = it.editText.text.toString() == expected }
                    var completed = false
                    ins.runOnMainSync {
                        val ime = service()
                        completed = ime != null && IMEService::class.java.getDeclaredField("mushroomTarget")
                            .apply { isAccessible = true }.get(ime) == null
                    }
                    matches && completed && scenario.state == androidx.lifecycle.Lifecycle.State.RESUMED
                }
                editor("abc", 1, 2)
                launch()
                assertTrue(ins.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("Source: b").isNotEmpty())
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
                assertTrue(ins.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("Source: かな").isNotEmpty())
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
                scenario.onActivity { it.editText.setText("changed") }
                assertTrue(click("Return result"))
                textIs("changed")

                editor("secret", 0, 6)
                scenario.onActivity {
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
                scenario.onActivity { it.editText.inputType = android.text.InputType.TYPE_CLASS_TEXT }

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
