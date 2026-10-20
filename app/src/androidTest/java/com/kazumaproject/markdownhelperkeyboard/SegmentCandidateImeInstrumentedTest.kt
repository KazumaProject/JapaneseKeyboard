package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.view.accessibility.AccessibilityNodeInfo
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.BeforeClass
import org.junit.AfterClass
import org.junit.runner.RunWith

/** Exercises the shipped IME, its composing spans, native touch routing and candidate window. */
@RunWith(AndroidJUnit4::class)
class SegmentCandidateImeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private lateinit var host: EditorHost
    private var keyboardId = 0

    @Test fun treeSelectionOnlyReplacesTargetAndConfirmCommitsWholeSentence() = withKeyboard {
        startConversion()
        clickDescription(instrumentation.targetContext.getString(R.string.segment_select_description, "東京に", "とうきょうに"))
        await { findDescription(instrumentation.targetContext.getString(R.string.segment_range_description, 4, 10)) != null }
        screenshot("segments.png")
        clickDescription(instrumentation.targetContext.getString(R.string.segment_tree_open))
        await { allNodes().any { it.text?.toString() == instrumentation.targetContext.getString(R.string.segment_tree_title) } }
        screenshot("tree.png")
        val alternative = allNodes().firstOrNull { node ->
            val label = node.text?.toString().orEmpty()
            label.endsWith("\nとうきょうに") && !label.startsWith("東京に\n") && node.isClickable
        } ?: error("No alternative for the focused reading in the tree")
        val replacement = alternative.text.toString().substringBefore('\n')
        assertTrue(alternative.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        awaitText("明日は" + replacement + "行きます")
        assertComposing()
        clickDescription(instrumentation.targetContext.getString(R.string.segment_tree_close))
        key(KeyEvent.KEYCODE_ENTER)
        awaitText("明日は" + replacement + "行きます")
        host.onActivity { assertEquals(-1, BaseInputConnection.getComposingSpanStart(it.editText.text)) }
    }

    @Test fun draggingBoundaryRequeriesRangeWithoutLosingReadingOrCommitting() = withKeyboard {
        startConversion()
        clickDescription(instrumentation.targetContext.getString(R.string.segment_select_description, "東京に", "とうきょうに"))
        val description = instrumentation.targetContext.getString(R.string.segment_range_description, 4, 10)
        await { findDescription(description) != null }
        val bounds = android.graphics.Rect().also(findDescription(description)!!::getBoundsInScreen)
        val density = instrumentation.targetContext.resources.displayMetrics.density
        val x = bounds.left + 276 * density
        val y = bounds.centerY().toFloat()
        drag(x, y, x - 26 * density, y)
        await { findDescription(instrumentation.targetContext.getString(R.string.segment_range_description, 4, 9)) != null }
        assertComposing()
        screenshot("boundary.png")
        key(KeyEvent.KEYCODE_ESCAPE)
        awaitText("あしたはとうきょうにいきます")
    }

    @Test fun candidateTapBeforeConversionDoesNotCommitAndLegacySettingsStayDisabled() = withKeyboard {
        type("ashitahatoukyouniikimasu")
        awaitText("あしたはとうきょうにいきます")
        showSoftwareKeyboard()
        fun isWholeCandidate(node: AccessibilityNodeInfo): Boolean {
            val label = node.text?.toString().orEmpty()
            return "明日" in label && "東京" in label && "行き" in label
        }
        await { allNodes().any(::isWholeCandidate) }
        var candidate: AccessibilityNodeInfo? = allNodes().first(::isWholeCandidate)
        while (candidate != null && !candidate.isClickable) candidate = candidate.parent
        assertTrue(candidate?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        awaitText("明日は東京に行きます")
        assertComposing()
        val prefs = PreferenceManager.getDefaultSharedPreferences(instrumentation.targetContext)
        assertFalse(prefs.getBoolean("conversion_bunsetsu_separation_preference", true))
        assertFalse(prefs.getBoolean("conversion_bunsetsu_cursor_move_preference", true))
    }

    @Test fun floatingKeyboardAnchorsTreeAboveItsOwnCandidateBar() = withKeyboard(floating = true) {
        startConversion()
        clickDescription(instrumentation.targetContext.getString(R.string.segment_tree_open))
        await { allNodes().any { it.text?.toString() == instrumentation.targetContext.getString(R.string.segment_tree_title) } }
        assertComposing()
        screenshot("floating.png")
        clickDescription(instrumentation.targetContext.getString(R.string.segment_tree_close))
        assertComposing()
    }

    @Test fun switchingToLegacyAndRestartingEditorDismissTreeWithoutStaleResults() = withKeyboard {
        startConversion()
        clickDescription(instrumentation.targetContext.getString(R.string.segment_tree_open))
        val prefs = PreferenceManager.getDefaultSharedPreferences(instrumentation.targetContext)
        prefs.edit().putString("candidate_display_method", "legacy").commit()
        awaitText("あしたはとうきょうにいきます")
        await { findDescription(instrumentation.targetContext.getString(R.string.segment_tree_open)) == null }
        host.onActivity { it.restartEditorInput(true) }
        SystemClock.sleep(500)
        key(KeyEvent.KEYCODE_KANA)
        type("a")
        awaitText("あ")
        SystemClock.sleep(500)
        assertEquals("あ", text())
        assertFalse(prefs.getBoolean("conversion_bunsetsu_separation_preference", true))
        assertFalse(prefs.getBoolean("conversion_bunsetsu_cursor_move_preference", true))
    }

    @Test fun landscapeBarAndTreeRemainEditable() = withKeyboard {
        host.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        await { instrumentation.targetContext.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        prepareJapaneseEditor()
        startConversion()
        screenshot("landscape-before.png")
        clickDescription(instrumentation.targetContext.getString(R.string.segment_tree_open))
        screenshot("landscape-open.png")
        await { allNodes().any { it.text?.toString() == instrumentation.targetContext.getString(R.string.segment_tree_title) } }
        screenshot("landscape.png")
        assertComposing()
    }

    private fun prepareJapaneseEditor() {
        val deadline = SystemClock.uptimeMillis() + 30000
        var observed = ""
        while (SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(500)
            key(KeyEvent.KEYCODE_KANA)
            key(KeyEvent.KEYCODE_A)
            observed = text()
            host.onActivity { it.restartEditorInput(true) }
            if (observed == "あ") { SystemClock.sleep(500); return }
        }
        fail("Japanese IME input did not initialize: [$observed]")
    }

    private fun startConversion() {
        type("ashitahatoukyouniikimasu")
        awaitText("あしたはとうきょうにいきます")
        key(KeyEvent.KEYCODE_SPACE)
        awaitText("明日は東京に行きます")
        showSoftwareKeyboard()
        assertComposing()
    }

    private fun showSoftwareKeyboard() {
        clickDescription(instrumentation.targetContext.getString(R.string.physical_toolbar_keyboard_title))
        await { findDescription(instrumentation.targetContext.getString(R.string.segment_tree_open)) != null }
    }

    private fun assertComposing() {
        host.onActivity { assertEquals(0, BaseInputConnection.getComposingSpanStart(it.editText.text)) }
    }

    private fun allNodes(): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            nodes.add(node)
            for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
        }
        automation.windows.mapNotNull { it.root }.forEach(::visit)
        return nodes
    }

    private fun findDescription(value: String) = allNodes().firstOrNull { it.contentDescription?.toString() == value }
    private fun clickDescription(value: String) {
        await { findDescription(value) != null }
        assertTrue("Could not click $value", findDescription(value)!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun drag(x: Float, y: Float, endX: Float, endY: Float) {
        val down = SystemClock.uptimeMillis()
        fun event(action: Int, atX: Float, atY: Float) {
            val motion = android.view.MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, atX, atY, 0)
            motion.source = InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(automation.injectInputEvent(motion, true))
            motion.recycle()
        }
        event(android.view.MotionEvent.ACTION_DOWN, x, y)
        repeat(8) { step ->
            SystemClock.sleep(25)
            event(android.view.MotionEvent.ACTION_MOVE, x + (endX - x) * (step + 1) / 8, y + (endY - y) * (step + 1) / 8)
        }
        event(android.view.MotionEvent.ACTION_UP, endX, endY)
    }

    private fun screenshot(name: String) {
        val file = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "segment-candidates-$name")
        val bitmap = automation.takeScreenshot() ?: error("No screenshot")
        java.io.FileOutputStream(file).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    // IME windows can animate continuously. Wait for activity lifecycle instead of global idle.
    private inner class EditorHost : AutoCloseable {
        private val application = instrumentation.targetContext.applicationContext as android.app.Application
        private val current = java.util.concurrent.atomic.AtomicReference<FastInputHostActivity>()
        private val callbacks = object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: android.app.Activity) {
                if (activity is FastInputHostActivity) current.set(activity)
            }
            override fun onActivityCreated(activity: android.app.Activity, state: android.os.Bundle?) = Unit
            override fun onActivityStarted(activity: android.app.Activity) = Unit
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivityStopped(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, state: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        }
        fun launch() {
            instrumentation.runOnMainSync { application.registerActivityLifecycleCallbacks(callbacks) }
            instrumentation.targetContext.startActivity(android.content.Intent(instrumentation.targetContext, FastInputHostActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
            val deadline = SystemClock.uptimeMillis() + 30000
            while (current.get() == null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
            check(current.get() != null) { "Editor activity did not resume" }
        }
        fun onActivity(action: (FastInputHostActivity) -> Unit) {
            instrumentation.runOnMainSync { action(checkNotNull(current.get())) }
        }
        override fun close() {
            instrumentation.runOnMainSync {
                application.unregisterActivityLifecycleCallbacks(callbacks)
                current.get()?.finish()
            }
        }
    }

    private fun type(romaji: String) = romaji.forEach { key(KeyEvent.KEYCODE_A + (it - 'a'), settleMillis = 250) }

    private fun key(code: Int, meta: Int = 0, settleMillis: Long = 160) {
        val time = SystemClock.uptimeMillis()
        fun inject(keyCode: Int, action: Int, metaState: Int) {
            assertTrue(automation.injectInputEvent(KeyEvent(
                time, SystemClock.uptimeMillis(), action, keyCode, 0, metaState, keyboardId,
                0, 0, InputDevice.SOURCE_KEYBOARD
            ), true))
        }
        val shifted = meta and KeyEvent.META_SHIFT_ON != 0
        if (shifted) inject(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.ACTION_DOWN, meta)
        inject(code, KeyEvent.ACTION_DOWN, meta)
        inject(code, KeyEvent.ACTION_UP, meta)
        if (shifted) inject(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.ACTION_UP, 0)
        SystemClock.sleep(settleMillis)
    }

    private fun text(): String {
        var value = ""
        host.onActivity { value = it.editText.text.toString() }
        return value
    }

    private fun awaitText(expected: String) = await { text() == expected }

    private fun await(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        screenshot("failure-${SystemClock.uptimeMillis()}.png")
        val labels = allNodes().mapNotNull { it.text?.toString() ?: it.contentDescription?.toString() }.distinct()
        fail("Timed out; editor=[${text()}]; visible labels=$labels")
    }

    companion object {
        private lateinit var originalIme: String
        private lateinit var targetIme: String
        private lateinit var originalShowWithHardware: String
        private var wasEnabled = false

        private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ).bufferedReader().use { it.readText().trim() }

        private fun sameIme(first: String, second: String): Boolean =
            ComponentName.unflattenFromString(first) == ComponentName.unflattenFromString(second)

        @JvmStatic @BeforeClass fun selectIme() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val component = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
            targetIme = shell("ime list -a -s").lines().first { sameIme(it, component) }
            originalIme = shell("settings get secure default_input_method")
            originalShowWithHardware = shell("settings get secure show_ime_with_hard_keyboard")
            wasEnabled = shell("ime list -s").lines().any { sameIme(it, targetIme) }
            shell("settings put secure show_ime_with_hard_keyboard 1")
            shell("ime enable $targetIme")
            shell("ime set $targetIme")
            assertTrue("Test IME was not selected", sameIme(shell("settings get secure default_input_method"), targetIme))
        }

        @JvmStatic @AfterClass fun restoreIme() {
            if (originalIme.isNotEmpty() && originalIme != "null") shell("ime set $originalIme")
            if (!wasEnabled && !sameIme(originalIme, targetIme)) shell("ime disable $targetIme")
            if (originalShowWithHardware == "null") {
                shell("settings delete secure show_ime_with_hard_keyboard")
            } else {
                shell("settings put secure show_ime_with_hard_keyboard $originalShowWithHardware")
            }
        }
    }

    private fun withKeyboard(floating: Boolean = false, block: () -> Unit) {
        keyboardId = InputDevice.getDeviceIds().toList().mapNotNull(InputDevice::getDevice)
            .firstOrNull { !it.isVirtual && it.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC }
            ?.id ?: android.view.KeyCharacterMap.VIRTUAL_KEYBOARD
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val context = instrumentation.targetContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val values = mapOf<String, Any>(
            "conversion_bunsetsu_separation_preference" to false,
            "conversion_bunsetsu_cursor_move_preference" to false,
            "candidate_order_override_enable_preference" to false,
            "candidate_display_method" to "segments",
            "physical_toolbar_style" to "floating",
            "keyboard_floating_preference" to floating,
            "allow_fullscreen_mode_preference" to false,
            "keyboard_skin_id" to "default",
            "composing_guide_enabled" to false,
            "composing_guide_text_enabled" to false,
            "learn_dictionary_preference" to false,
            "user_template_preference" to true,
            "ng_word_enable_preference" to true,
            "physical_keyboard_input_mode_preference" to "romaji",
            "live_conversion_preference" to false,
            "sumire_keymap_guide_japanese" to false,
            "sumire_keymap_guide_modes_migrated" to true,
        )
        val previous = values.keys.associateWith { prefs.all[it] }
        try {
            prefs.edit().apply {
                values.forEach { (key, value) ->
                    when (value) { is Boolean -> putBoolean(key, value); is String -> putString(key, value) }
                }
            }.commit()
            host = EditorHost()
            host.launch()
            host.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            host.onActivity { it.restartEditorInput(true) }
            prepareJapaneseEditor()
            block()
        } finally {
            if (::host.isInitialized) host.close()
            prefs.edit().apply {
                previous.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> putBoolean(key, value)
                        is String -> putString(key, value)
                        null -> remove(key)
                    }
                }
            }.commit()
        }
    }
}
