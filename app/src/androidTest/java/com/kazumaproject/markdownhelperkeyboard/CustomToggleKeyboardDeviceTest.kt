package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.text.style.BackgroundColorSpan
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.BaseInputConnection
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.custom_keyboard.data.KeyTextInputBehavior
import com.kazumaproject.custom_keyboard.data.copyWithKeys
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.AppModule
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.KeyboardEditorViewModel
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uses the real IME and editor in an isolated application, restoring the selected IME afterward.
 * Run :app:connectedLiteStandardDebugAndroidTest with -I investigation/custom-toggle.init.gradle
 * and -Pandroid.testInstrumentationRunnerArguments.class=com.kazumaproject.markdownhelperkeyboard.CustomToggleKeyboardDeviceTest.
 */
@RunWith(AndroidJUnit4::class)
class CustomToggleKeyboardDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val automation get() = ins.uiAutomation
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            result.add(node)
            for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
        }
        automation.windows.forEach { it.root?.let(::visit) }
        return result
    }
    private fun key(label: String): Rect {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            nodes().firstOrNull { it.packageName?.toString() == ins.targetContext.packageName &&
                it.isVisibleToUser && it.isClickable &&
                it.text?.toString()?.lineSequence()?.firstOrNull()?.trim() == label
            }?.let { return Rect().also(it::getBoundsInScreen) }
            SystemClock.sleep(100)
        }
        error("Missing key $label: " + nodes().joinToString { "${it.text}/${it.contentDescription}" })
    }
    private fun awaitStableKeyboard() {
        var previous: Rect? = null
        var unchanged = 0
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            val bounds = key("あ")
            unchanged = if (bounds == previous) unchanged + 1 else 0
            if (unchanged >= 3) return
            previous = bounds
            SystemClock.sleep(100)
        }
        error("Custom keyboard geometry did not settle")
    }
    private fun tap(rect: Rect) {
        val start = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action,
                rect.exactCenterX(), rect.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            check(automation.injectInputEvent(event, true))
            event.recycle()
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(25)
        }
        SystemClock.sleep(80)
    }
    private fun cell(column: Int, row: Int): Rect {
        val a = key("あ")
        val ka = key("か")
        val ta = key("た")
        return Rect(a).apply {
            offset((column - 1) * (ka.centerX() - a.centerX()), row * (ta.centerY() - a.centerY()))
        }
    }
    private fun flickUp(rect: Rect) {
        val start = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action,
                rect.exactCenterX(), rect.exactCenterY() - if (action == MotionEvent.ACTION_DOWN) 0f else rect.height().toFloat(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            check(automation.injectInputEvent(event, true))
            event.recycle()
            SystemClock.sleep(30)
        }
        SystemClock.sleep(80)
    }
    private fun text(scenario: ActivityScenario<FastInputHostActivity>): String {
        var result = ""
        scenario.onActivity { result = it.editText.text.toString() }
        return result
    }
    private fun color(scenario: ActivityScenario<FastInputHostActivity>): Int? {
        var result: Int? = null
        scenario.onActivity {
            val text = it.editText.text
            result = text.getSpans(0, text.length, BackgroundColorSpan::class.java).lastOrNull()?.backgroundColor
        }
        return result
    }

    @Test fun actualIme_customToggleAndOrdinaryOutputAcrossModesAndSurfaces() = runBlocking {
        val context = ins.targetContext
        check(context.packageName.startsWith("com.kazumaproject.customtoggletest")) {
            "Use an isolated customtoggletest application ID"
        }
        val target = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val oldIme = shell("settings get secure default_input_method").trim()
        val enabled = shell("ime list -s").lineSequence().any { it.trim() == target }
        val db = AppModule.providesLearnDatabase(context)
        val repository = KeyboardRepository(db.keyboardLayoutDao())
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        val editor = KeyboardEditorViewModel(repository)
        editor.applyTemplate(KeyboardDefaultLayouts.createToggleKanaTemplateLayout())
        val template = editor.uiState.value.layout
        // One ordinary kana key proves that global toggle mode cannot change normal output.
        val layout = template.copyWithKeys(template.keys.map {
            if (it.label == "か") it.copy(textInputBehavior = KeyTextInputBehavior.NORMAL) else it
        })
        var id: Long? = repository.getLayoutsNotFlow().firstOrNull()?.layoutId
        try {
            shell("ime enable $target")
            shell("ime set $target")
            for (direct in listOf(false, true)) {
                id = repository.saveLayout(layout.copy(isDirectMode = direct), "Toggle device test", id)
                for (floating in listOf(false, true)) for (flickOnly in listOf(false, true)) {
                    check(prefs.edit()
                        .putString("keyboard_order_preference", "[\"CUSTOM\"]")
                        .putBoolean("save_last_used_keyboard", false)
                        .putBoolean("keyboard_floating_preference", floating)
                        .putBoolean("flick_input_only_preference", flickOnly)
                        .putInt("time_same_pronounce_typing_preference", 600)
                        .putBoolean("live_conversion_preference", false)
                        .putBoolean("theme_custom_input_color_enable", true)
                        .putInt("theme_custom_pre_edit_bg_color", 0x44112233)
                        .commit())
                    ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { scenario ->
                        awaitStableKeyboard()
                        val a = key("あ")
                        val ka = key("か")
                        tap(a)
                        assertEquals("direct=$direct floating=$floating flickOnly=$flickOnly", "あ", text(scenario))
                        val before = color(scenario)
                        if (!direct) assertEquals(0x44112233, before)
                        tap(a)
                        assertEquals("い", text(scenario))
                        SystemClock.sleep(700)
                        if (!direct) {
                            assertNotNull(color(scenario))
                            assertNotEquals(before, color(scenario))
                        }
                        tap(a)
                        assertEquals("いあ", text(scenario))
                        tap(ka); tap(ka)
                        assertEquals("いあかか", text(scenario))
                        if (!direct) {
                            tap(cell(1, 3))
                            assertEquals("いあかが", text(scenario))
                            tap(key("あ")); tap(cell(1, 3))
                            assertEquals("いあかがぁ", text(scenario))
                            flickUp(key("あ"))
                            assertEquals("いあかがぁう", text(scenario))
                            tap(key("あ"))
                            assertEquals("いあかがぁうあ", text(scenario))
                            tap(cell(4, 0))
                            assertEquals("いあかがぁう", text(scenario))
                            SystemClock.sleep(700)
                            tap(cell(4, 2))
                            tap(cell(4, 3))
                            val committed = text(scenario)
                            assertTrue(committed.isNotEmpty())
                            scenario.onActivity {
                                assertEquals(-1, BaseInputConnection.getComposingSpanStart(it.editText.text))
                            }
                            SystemClock.sleep(700)
                            assertEquals(committed, text(scenario))
                        }
                    }
                }
            }
        } finally {
            if (oldIme.isNotEmpty() && oldIme != "null") shell("ime set $oldIme")
            if (!enabled) shell("ime disable $target")
            db.close()
        }
    }
}
