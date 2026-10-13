package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.BaseInputConnection
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.copyWithKeys
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.AppModule
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real composing editor and software key taps. Requires investigation/custom-toggle.init.gradle. */
@RunWith(AndroidJUnit4::class)
class CustomDirectInputCompositionDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val automation get() = ins.uiAutomation
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            result.add(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        automation.windows.forEach { it.root?.let(::visit) }
        return result
    }
    private fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            nodes().firstOrNull { it.isVisibleToUser && predicate(it) }?.let { return it }
            SystemClock.sleep(100)
        }
        error("Node missing: " + nodes().joinToString { "${it.viewIdResourceName}:${it.text}:${it.contentDescription}" })
    }
    private fun tap(node: AccessibilityNodeInfo) {
        val rect = Rect().also(node::getBoundsInScreen)
        val start = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, rect.exactCenterX(), rect.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            check(automation.injectInputEvent(event, true)); event.recycle()
            SystemClock.sleep(35)
        }
        SystemClock.sleep(120)
    }
    private fun key(label: String) = awaitNode {
        it.packageName?.toString() == ins.targetContext.packageName && it.isClickable &&
            (it.text ?: it.contentDescription)?.toString()?.lineSequence()?.firstOrNull()?.trim()?.equals(label, true) == true
    }
    private fun text(host: ActivityScenario<FastInputHostActivity>): String {
        var text = ""; host.onActivity { text = it.editText.text.toString() }; return text
    }
    @Test
    fun firstDirectInputCommitsOrReplacesComposingTextOnBothSurfaces() = runBlocking {
        val context = ins.targetContext
        check(context.packageName.startsWith("com.kazumaproject.customtoggletest")) {
            "Use an isolated customtoggletest application ID"
        }
        val target = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val previousIme = shell("settings get secure default_input_method").trim()
        val wasEnabled = shell("ime list -s").lineSequence().any { it.trim() == target }
        val db = AppModule.providesLearnDatabase(context)
        val repository = KeyboardRepository(db.keyboardLayoutDao())
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val template = KeyboardDefaultLayouts.createQwertyTemplateLayout().copy(isRomaji = false, isDirectMode = false)
        val layout = template.copyWithKeys(template.keys.map {
            if (it.label.equals("q", true)) it.copy(label = "MODE", action = KeyAction.SwitchDirectMode, drawableResId = null, icon = null)
            else it
        })
        repository.saveLayout(layout, "Direct composition device test", repository.getLayoutsNotFlow().firstOrNull()?.layoutId)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        try {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            shell("ime enable $target")
            shell("ime set $target")
            for (floating in listOf(false, true)) for (replace in listOf(false, true)) {
                check(prefs.edit()
                    .putString("keyboard_order_preference", "[\"CUSTOM\"]")
                    .putBoolean("save_last_used_keyboard", false)
                    .putBoolean("keyboard_floating_preference", floating)
                    .putBoolean("remember_custom_keyboard_input_mode_preference", false)
                    .putBoolean("live_conversion_preference", false)
                    .putBoolean(AppPreference.CUSTOM_DIRECT_INPUT_REPLACE_COMPOSING_KEY, !replace)
                    .commit())
                ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { host ->
                    key("a")
                    ins.waitForIdleSync()
                    SystemClock.sleep(400)
                    tap(key("a"))
                    assertEquals("a", text(host))
                    host.onActivity { assertTrue(BaseInputConnection.getComposingSpanStart(it.editText.text) >= 0) }
                    // Exercise the live preference listener while composition is active.
                    check(prefs.edit().putBoolean(AppPreference.CUSTOM_DIRECT_INPUT_REPLACE_COMPOSING_KEY, replace).commit())
                    ins.waitForIdleSync()
                    SystemClock.sleep(200)
                    assertEquals("a", text(host))
                    tap(key("MODE"))
                    assertEquals("a", text(host))
                    host.onActivity {
                        assertEquals("replace=$replace floating=$floating", replace,
                            BaseInputConnection.getComposingSpanStart(it.editText.text) >= 0)
                    }
                    tap(key("b"))
                    assertEquals("replace=$replace floating=$floating", if (replace) "b" else "ab", text(host))
                    host.onActivity { assertEquals(-1, BaseInputConnection.getComposingSpanStart(it.editText.text)) }
                }
            }
        } finally {
            if (previousIme.isNotEmpty() && previousIme != "null") shell("ime set $previousIme")
            if (!wasEnabled) shell("ime disable $target")
            db.close()
        }
    }
}
