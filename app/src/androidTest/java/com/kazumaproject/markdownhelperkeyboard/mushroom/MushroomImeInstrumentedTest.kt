package com.kazumaproject.markdownhelperkeyboard.mushroom

import android.content.ContextWrapper
import android.os.ParcelFileDescriptor
import android.os.Bundle
import android.os.Build
import android.os.ResultReceiver
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent
import android.view.inspector.WindowInspector
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.preference.PreferenceManager
import com.kazumaproject.markdownhelperkeyboard.FastInputHostActivity
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

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
        fail("$message; defaultIme=${shell("settings get secure default_input_method").trim()}; $focus; visible=${visibleText()}; ime=${imeState()}")
    }
    private fun imeState(): String {
        var state = "not found"
        ins.runOnMainSync {
            service()?.let { ime ->
                val target = IMEService::class.java.getDeclaredField("mushroomTarget")
                    .apply { isAccessible = true }.get(ime)
                val originalEditor = IMEService::class.java.getDeclaredField("mushroomEditor")
                    .apply { isAccessible = true }.get(ime)
                val currentEditor = IMEService::class.java.getDeclaredMethod("currentMushroomEditor")
                    .apply { isAccessible = true }.invoke(ime)
                val text = ime.currentInputConnection?.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
                state = "pending=${target != null}, original=$originalEditor, current=$currentEditor, " +
                    "text=${text?.text}, selection=${text?.selectionStart}:${text?.selectionEnd}"
            }
        }
        return state
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
    private fun visibleText(): String {
        fun collect(node: AccessibilityNodeInfo): List<String> =
            (if (node.isVisibleToUser) listOfNotNull(node.text?.toString()) else emptyList()) +
                (0 until node.childCount).flatMap { node.getChild(it)?.let(::collect).orEmpty() }
        return roots().flatMap(::collect).joinToString(" | ")
    }
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
            // Dismiss an external plugin even when an assertion fails, before the next test starts.
            shell("input keyevent KEYCODE_BACK")
            SystemClock.sleep(300)
            shell("input keyevent KEYCODE_BACK")
            SystemClock.sleep(300)
            onHost(activity) { it.finish() }
            shell("am broadcast -a ${MushroomPluginActivity.ACTION_CLOSE} -p ${ins.context.packageName}")
        }
    }

    private fun bridgeReceiver(): ResultReceiver? {
        var receiver: ResultReceiver? = null
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
        return receiver
    }

    private fun chosenPlugin(): String? {
        var chosen: String? = null
        ins.runOnMainSync {
            chosen = service()?.let { IMEService::class.java.getDeclaredField("mushroomPluginPackage")
                .apply { isAccessible = true }.get(it) as String? }
        }
        return chosen
    }

    @Test(timeout = 180_000) fun resultAfterOriginalEditorReconnectsIsAppliedOnce() {
        assumeTrue("Use an isolated applicationId containing mushroomtest", context.packageName.contains("mushroomtest"))
        val targetIme = "${context.packageName}/${IMEService::class.java.name}"
        val oldIme = shell("settings get secure default_input_method").trim()
        try {
            prepareIme(targetIme)
            withHost { host ->
                onHost(host) {
                    it.editText.setText("abc")
                    it.editText.setSelection(1, 2)
                    it.editText.requestFocus()
                    it.getSystemService(InputMethodManager::class.java).restartInput(it.editText)
                    it.getSystemService(InputMethodManager::class.java).showSoftInput(it.editText, 0)
                }
                await("Original editor connected") {
                    var ready = false
                    ins.runOnMainSync {
                        val ime = service()
                        val snapshot = ime?.currentInputConnection?.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
                        ready = ime?.currentInputStarted == true && ime.currentInputEditorInfo?.packageName == context.packageName &&
                            snapshot?.text?.toString() == "abc" && snapshot.selectionStart == 1 && snapshot.selectionEnd == 2
                    }
                    ready
                }
                ins.runOnMainSync {
                    IMEService::class.java.getDeclaredMethod("launchMushroom").apply { isAccessible = true }.invoke(service()!!)
                }
                await("Plugin chooser or direct launch") { hasText("Return result") || click("Mushroom test plugin") }
                await("Plugin received selected text") { hasText("Source: b") }
                var receiver: ResultReceiver? = null
                await("Bridge is available behind the plugin") { bridgeReceiver().also { receiver = it } != null }
                await("Chooser reports the selected plugin") { chosenPlugin() == ins.context.packageName }
                shell("am start -n ${context.packageName}/${FastInputHostActivity::class.java.name} --activity-reorder-to-front")
                await("Original editor reconnects before the result") {
                    var ready = false
                    ins.runOnMainSync {
                        val ime = service()
                        val snapshot = ime?.currentInputConnection?.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
                        ready = ime?.currentInputStarted == true && ime.currentInputEditorInfo?.packageName == context.packageName &&
                            snapshot?.text?.toString() == "abc" && snapshot.selectionStart == 1 && snapshot.selectionEnd == 2
                    }
                    ready
                }
                requireNotNull(receiver).send(android.app.Activity.RESULT_OK, Bundle().apply {
                    putString(MushroomActivity.REPLACE_KEY, "[b]")
                })
                await("Result is applied without another tap") {
                    var actual = ""
                    onHost(host) { actual = it.editText.text.toString() }
                    actual == "a[b]c"
                }
                // A duplicate callback for the same request must not insert twice.
                requireNotNull(receiver).send(android.app.Activity.RESULT_OK, Bundle().apply {
                    putString(MushroomActivity.REPLACE_KEY, "[b]")
                })
                SystemClock.sleep(500)
                onHost(host) { assertEquals("a[b]c", it.editText.text.toString()) }
            }
        } finally {
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
            shell("ime disable $targetIme")
        }
    }

    @Test(timeout = 180_000) fun resultWhilePluginEditorIsConnectedWaitsForOriginalEditor() =
        verifyPluginEditorResult(missingChooserCallback = false)

    @Test(timeout = 180_000) fun resultWithoutChooserCallbackWaitsForOriginalEditor() =
        verifyPluginEditorResult(missingChooserCallback = true)

    private fun verifyPluginEditorResult(missingChooserCallback: Boolean) {
        assumeTrue("Use an isolated applicationId containing mushroomtest", context.packageName.contains("mushroomtest"))
        val targetIme = "${context.packageName}/${IMEService::class.java.name}"
        val oldIme = shell("settings get secure default_input_method").trim()
        try {
            prepareIme(targetIme)
            withHost { host ->
                onHost(host) {
                    it.editText.setText("abc")
                    it.editText.setSelection(1, 2)
                    it.editText.requestFocus()
                    it.getSystemService(InputMethodManager::class.java).restartInput(it.editText)
                    it.getSystemService(InputMethodManager::class.java).showSoftInput(it.editText, 0)
                }
                await("Original editor connected") {
                    var ready = false
                    ins.runOnMainSync {
                        val ime = service()
                        val snapshot = ime?.currentInputConnection?.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
                        ready = ime?.currentInputStarted == true && ime.currentInputEditorInfo?.packageName == context.packageName &&
                            snapshot?.text?.toString() == "abc" && snapshot.selectionStart == 1 && snapshot.selectionEnd == 2
                    }
                    ready
                }
                ins.runOnMainSync {
                    IMEService::class.java.getDeclaredMethod("launchMushroom").apply { isAccessible = true }.invoke(service()!!)
                }
                await("Plugin chooser or direct launch") { hasText("Return result") || click("Mushroom test plugin") }
                await("Plugin received selected text") { hasText("Source: b") }
                var receiver: ResultReceiver? = null
                await("Bridge is available behind the plugin") { bridgeReceiver().also { receiver = it } != null }
                await("Chooser reports the selected plugin") { chosenPlugin() == ins.context.packageName }
                // Simulate the selected plugin's editor still being connected when its result arrives.
                shell("am start -n ${ins.context.packageName}/${MushroomPluginActivity::class.java.name} --ez editor true")
                await("Different editor connected before the result") {
                    var ready = false
                    ins.runOnMainSync {
                        val ime = service()
                        val snapshot = ime?.currentInputConnection?.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
                        ready = ime?.currentInputStarted == true && ime.currentInputEditorInfo?.packageName == ins.context.packageName &&
                            snapshot?.text?.toString() == "abc"
                    }
                    ready
                }
                if (missingChooserCallback) {
                    // Android allows the chooser callback to be absent; exercise that fallback explicitly.
                    ins.runOnMainSync {
                        IMEService::class.java.getDeclaredField("mushroomPluginPackage").apply { isAccessible = true }
                            .set(service(), null)
                    }
                }
                requireNotNull(receiver).send(android.app.Activity.RESULT_OK, Bundle().apply {
                    putString(MushroomActivity.REPLACE_KEY, "[b]")
                })
                await("Result is pending while the different editor remains connected") {
                    var pending = false
                    ins.runOnMainSync {
                        val ime = service()
                        pending = ime != null && IMEService::class.java.getDeclaredField("mushroomResult")
                            .apply { isAccessible = true }.get(ime) == "[b]" &&
                            ime.currentInputEditorInfo?.packageName == ins.context.packageName
                    }
                    pending
                }
                var text: String? = null
                ins.runOnMainSync { text = service()?.currentInputConnection?.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)?.text?.toString() }
                assertEquals("abc", text)
                // Bring back the original host without sending a second plugin result.
                shell("am start -n ${context.packageName}/${FastInputHostActivity::class.java.name} --activity-reorder-to-front")
                await("Original editor reconnects") {
                    var reconnected = false
                    ins.runOnMainSync { reconnected = service()?.currentInputEditorInfo?.packageName == context.packageName }
                    reconnected
                }
                await("Pending result is applied when the original editor reconnects") {
                    var actual = ""
                    onHost(host) { actual = it.editText.text.toString() }
                    actual == "a[b]c"
                }
            }
        } finally {
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
            shell("ime disable $targetIme")
        }
    }

    @Test(timeout = 180_000) fun resultAfterDifferentHostEditorConnectsIsRejected() {
        assumeTrue("Use an isolated applicationId containing mushroomtest", context.packageName.contains("mushroomtest"))
        val targetIme = "${context.packageName}/${IMEService::class.java.name}"
        val oldIme = shell("settings get secure default_input_method").trim()
        try {
            prepareIme(targetIme)
            withHost { host ->
                onHost(host) {
                    it.editText.setText("abc")
                    it.editText.setSelection(1, 2)
                    it.editText.requestFocus()
                    it.getSystemService(InputMethodManager::class.java).restartInput(it.editText)
                    it.getSystemService(InputMethodManager::class.java).showSoftInput(it.editText, 0)
                }
                await("Original editor connected") {
                    var ready = false
                    ins.runOnMainSync {
                        val ime = service()
                        val snapshot = ime?.currentInputConnection?.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
                        ready = ime?.currentInputStarted == true && ime.currentInputEditorInfo?.packageName == context.packageName &&
                            snapshot?.text?.toString() == "abc" && snapshot.selectionStart == 1 && snapshot.selectionEnd == 2
                    }
                    ready
                }
                ins.runOnMainSync {
                    IMEService::class.java.getDeclaredMethod("launchMushroom").apply { isAccessible = true }.invoke(service()!!)
                }
                await("Plugin chooser or direct launch") { hasText("Return result") || click("Mushroom test plugin") }
                await("Plugin received selected text") { hasText("Source: b") }
                var receiver: ResultReceiver? = null
                await("Bridge is available behind the plugin") { bridgeReceiver().also { receiver = it } != null }
                await("Chooser reports the selected plugin") { chosenPlugin() == ins.context.packageName }
                lateinit var otherEditor: EditText
                onHost(host) {
                    otherEditor = EditText(it).apply {
                        id = View.generateViewId()
                        setText("other")
                        setSelection(5)
                    }
                    val content = it.findViewById<ViewGroup>(android.R.id.content)
                    val layout = content.getChildAt(0) as LinearLayout
                    layout.addView(otherEditor, 1, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }
                shell("am start -n ${context.packageName}/${FastInputHostActivity::class.java.name} --activity-reorder-to-front")
                await("Host returned to the foreground") {
                    var focused = false
                    onHost(host) { focused = it.hasWindowFocus() }
                    focused
                }
                onHost(host) {
                    otherEditor.requestFocus()
                    it.getSystemService(InputMethodManager::class.java).restartInput(otherEditor)
                    it.getSystemService(InputMethodManager::class.java).showSoftInput(otherEditor, 0)
                }
                await("Different host editor connects before the result") {
                    var ready = false
                    ins.runOnMainSync {
                        val ime = service()
                        val snapshot = ime?.currentInputConnection?.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
                        ready = ime?.currentInputStarted == true && ime.currentInputEditorInfo?.packageName == context.packageName &&
                            ime.currentInputEditorInfo?.fieldId == otherEditor.id && snapshot?.text?.toString() == "other"
                    }
                    ready
                }
                val noticeSeen = AtomicBoolean(false)
                val expectedNotice = context.getString(R.string.mushroom_target_changed)
                ins.uiAutomation.setOnAccessibilityEventListener { event ->
                    if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
                        event.text.any { it.toString() == expectedNotice }) noticeSeen.set(true)
                }
                try {
                    requireNotNull(receiver).send(android.app.Activity.RESULT_OK, Bundle().apply {
                        putString(MushroomActivity.REPLACE_KEY, "[b]")
                    })
                    await("Result is discarded and the user is notified") {
                        var discarded = false
                        ins.runOnMainSync {
                            val ime = service()
                            discarded = ime != null && IMEService::class.java.getDeclaredField("mushroomTarget")
                                .apply { isAccessible = true }.get(ime) == null
                        }
                        discarded && noticeSeen.get()
                    }
                } finally {
                    ins.uiAutomation.setOnAccessibilityEventListener(null)
                }
                onHost(host) {
                    assertEquals("abc", it.editText.text.toString())
                    assertEquals("other", otherEditor.text.toString())
                    it.editText.requestFocus()
                    it.getSystemService(InputMethodManager::class.java).restartInput(it.editText)
                }
                await("Original editor reconnects without a delayed insertion") {
                    var ready = false
                    ins.runOnMainSync {
                        val ime = service()
                        val snapshot = ime?.currentInputConnection?.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
                        ready = ime?.currentInputEditorInfo?.fieldId == host.editText.id && snapshot?.text?.toString() == "abc"
                    }
                    ready
                }
            }
        } finally {
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
            shell("ime disable $targetIme")
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
            }
        } finally {
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
            shell("ime disable $targetIme")
        }
    }

    @Test(timeout = 180_000) fun returnsToPlainExternalEditorWithoutAnotherTap() {
        assumeTrue("Use an isolated applicationId containing mushroomtest", context.packageName.contains("mushroomtest"))
        val targetIme = "${context.packageName}/${IMEService::class.java.name}"
        val oldIme = shell("settings get secure default_input_method").trim()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putBoolean("shortcut_toolbar_visibility_preference", true)
            .putBoolean("shortcut_toolbar_integrated_in_suggestion_preference", false).commit()
        try {
            prepareIme(targetIme)
            withHost {
                val ime = requireNotNull(service())
                runBlocking { ime.shortCurRepository.updateShortcuts(listOf(ShortcutType.MUSHROOM, ShortcutType.CLIP_BOARD)) }
                shell("am start -n ${ins.context.packageName}/${MushroomPluginActivity::class.java.name} --ez editor true --ez plain_editor true")
                var editor: AccessibilityNodeInfo? = null
                await("Plain external editor visible") {
                    editor = roots().flatMap { it.findAccessibilityNodeInfosByViewId("android:id/input") }
                        .firstOrNull { it.isVisibleToUser && it.packageName?.toString() == ins.context.packageName }
                    editor != null
                }
                assertTrue(requireNotNull(editor).performAction(AccessibilityNodeInfo.ACTION_CLICK))
                await("Select b in the external editor") {
                    var selectionMatches = false
                    ins.runOnMainSync {
                        val text = service()?.currentInputConnection?.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
                        selectionMatches = text?.text?.toString() == "abc" && text.selectionStart == 1 && text.selectionEnd == 2
                    }
                    val current = roots().flatMap { it.findAccessibilityNodeInfosByViewId("android:id/input") }
                        .firstOrNull { it.isVisibleToUser && it.packageName?.toString() == ins.context.packageName }
                    if (!selectionMatches) {
                        current?.performAction(
                            AccessibilityNodeInfo.ACTION_SET_SELECTION,
                            Bundle().apply {
                                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 1)
                                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, 2)
                            },
                        )
                    }
                    selectionMatches
                }
                await("Original editor and toolbar connected") {
                    var ready = false
                    ins.runOnMainSync {
                        val active = service()
                        val text = active?.currentInputConnection?.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
                        ready = active?.isInputViewShown == true && active.currentInputEditorInfo?.packageName == ins.context.packageName &&
                            text?.text?.toString() == "abc" && text.selectionStart == 1 && text.selectionEnd == 2
                    }
                    ready && roots().any { it.findAccessibilityNodeInfosByText(ShortcutType.MUSHROOM.description).isNotEmpty() }
                }
                assertTrue("Tap the actual toolbar shortcut", click(ShortcutType.MUSHROOM.description))
                await("Plugin chooser or direct launch") { hasText("Return result") || click("Mushroom test plugin") }
                await("Plugin received selected text") { hasText("Source: b") }
                assertTrue(click("Return result"))
                await("Editor and keyboard return without tapping the editor") {
                    val editorText = roots().any { root ->
                        root.packageName?.toString() == ins.context.packageName &&
                            root.findAccessibilityNodeInfosByViewId("android:id/input")
                                .any { it.isVisibleToUser && it.text?.toString() == "a[b]c" }
                    }
                    var keyboardShown = false
                    ins.runOnMainSync { keyboardShown = service()?.isInputViewShown == true }
                    editorText && keyboardShown
                }
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
                await("Plugin received selected text b") { hasText("Source: b") }
                assertTrue(click("Return result"))
                textIs("a[b]c")

                editor("recreate", 0, 8)
                launch()
                await("Bridge stopped behind the plugin") {
                    var stopped = false
                    ins.runOnMainSync {
                        stopped = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.STOPPED)
                            .filterIsInstance<MushroomActivity>().size == 1
                    }
                    stopped
                }
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
                await("Plugin received preedit かな") { hasText("Source: かな") }
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

                editor("abc", 1, 2)
                launch()
                // Reconnect as a different editor type with the same text and selection.
                onHost(host) {
                    it.editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    it.editText.setSelection(1, 2)
                }
                assertTrue(click("Return result"))
                textIs("abc")
                onHost(host) { it.editText.inputType = android.text.InputType.TYPE_CLASS_TEXT }

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
                val disabled = shell("run-as ${ins.context.packageName} pm disable $plugin")
                assertTrue("Could not disable the test plugin: $disabled", disabled.contains("new state: disabled"))
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
                    shell("run-as ${ins.context.packageName} pm enable $plugin")
                }
            }
        } finally {
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
            shell("ime disable $targetIme")
        }
    }
}
