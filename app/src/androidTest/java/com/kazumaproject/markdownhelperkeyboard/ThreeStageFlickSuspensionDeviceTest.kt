package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.AppModule
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit opt-in is required because this exercises the real IME on an isolated emulator. */
@RunWith(AndroidJUnit4::class)
class ThreeStageFlickSuspensionDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    private fun key(label: String, floating: Boolean): AccessibilityNodeInfo {
        fun collect(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = listOf(node) +
            (0 until node.childCount).flatMap { node.getChild(it)?.let(::collect).orEmpty() }
        val surfaceId = if (floating) "custom_layout_floating" else "custom_layout_default"
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            val nodes = automation.windows.flatMap { it.root?.let(::collect).orEmpty() }
            nodes.filter { it.isVisibleToUser && it.viewIdResourceName?.endsWith("/$surfaceId") == true }
                .flatMap(::collect).firstOrNull {
                    it.isVisibleToUser && it.text?.toString()?.lineSequence()?.firstOrNull()?.trim() == label
                }?.let { return it }
            SystemClock.sleep(100)
        }
        error("Missing key $label on $surfaceId")
    }

    private fun tap(node: AccessibilityNodeInfo, hold: Boolean = false) {
        val bounds = Rect().also(node::getBoundsInScreen)
        val start = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action,
                bounds.exactCenterX(), bounds.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { check(automation.injectInputEvent(event, true)) } finally { event.recycle() }
            SystemClock.sleep(if (hold && action == MotionEvent.ACTION_DOWN) 600 else 50)
        }
    }

    private fun awaitText(scenario: ActivityScenario<FastInputHostActivity>, expected: String) {
        val deadline = SystemClock.uptimeMillis() + 5000
        var text = ""
        do {
            scenario.onActivity { text = it.editText.text.toString() }
            if (text == expected) return
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        assertEquals(expected, text)
    }

    @Test fun threeStageStaysSuspendedAndTwoStepWorksOnDockedAndFloatingIme() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("suspensionIsolatedEmulator") == "true" &&
            shell("getprop ro.kernel.qemu").trim() == "1")
        val context = instrumentation.targetContext
        val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val oldIme = shell("settings get secure default_input_method").trim()
        val wasEnabled = shell("ime list -s").lineSequence().any { it.trim() == ime }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val originalPrefs = prefs.all.toMap()
        val db = AppModule.providesLearnDatabase(context)
        val repository = KeyboardRepository(db.keyboardLayoutDao())
        var createdId: Long? = null
        try {
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            }
            val blockedTypes = listOf(KeyType.HIERARCHICAL_FLICK)
            val blocked = blockedTypes.mapIndexed { index, type ->
                KeyData("PausedKey$index", 0, index, true, action = KeyAction.Text("blocked"),
                    keyType = type, keyId = "paused-key$index")
            }
            val ordinary = KeyData("WorkingKey", 0, 1, false, action = KeyAction.Text("あ"), keyId = "working-key")
            val twoStep = KeyData("TwoStepKey", 0, 2, true, keyType = KeyType.TWO_STEP_FLICK, keyId = "two-step")
            val sticky = KeyData("StickyKey", 0, 3, true, keyType = KeyType.STICKY_TWO_STEP_FLICK, keyId = "sticky")
            val twoStepMaps = mapOf(
                "two-step" to mapOf(TfbiFlickDirection.TAP to mapOf(TfbiFlickDirection.TAP to "い")),
                "sticky" to mapOf(TfbiFlickDirection.TAP to mapOf(TfbiFlickDirection.TAP to "う"))
            )
            val definition = """{"normal":{"TAP":{"type":"input","output":"preserved"}}}"""
            createdId = repository.saveLayout(KeyboardLayout(blocked + ordinary + twoStep + sticky, emptyMap(), 4, 1,
                twoStepFlickKeyMaps = twoStepMaps,
                preservedFlickDefinitions = mapOf("paused-key0" to definition)), "Suspension device verification", null)
            val saved = repository.getFullLayout(createdId!!).first()
            val stableId = repository.getLayoutsNotFlow().single { it.layoutId == createdId }.stableId
            shell("ime enable $ime"); shell("ime set $ime")
            for (floating in listOf(false, true)) {
                check(prefs.edit().putString("keyboard_order_preference", "[\"CUSTOM\"]")
                    .putBoolean("save_last_used_keyboard", false)
                    .putBoolean("remember_last_custom_keyboard_preference", true)
                    .putString("last_used_custom_keyboard_stable_id", stableId)
                    .putBoolean("keyboard_floating_preference", floating)
                    .putBoolean("live_conversion_preference", false).commit())
                ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { scenario ->
                    scenario.onActivity { it.restartEditorInput(clearText = true) }
                    blockedTypes.indices.forEach { index ->
                        val stopped = key("PausedKey$index", floating)
                        assertFalse(stopped.isEnabled)
                        tap(stopped); tap(stopped); tap(stopped, hold = true)
                        awaitText(scenario, "")
                    }
                    tap(key("WorkingKey", floating))
                    awaitText(scenario, "あ")
                    tap(key("TwoStepKey", floating))
                    awaitText(scenario, "あい")
                    tap(key("StickyKey", floating))
                    awaitText(scenario, "あいう")
                }
                assertEquals(saved.preservedFlickDefinitions, repository.getFullLayout(createdId!!).first().preservedFlickDefinitions)
                check(prefs.edit().putString("keyboard_order_preference", "[\"SUMIRE\"]")
                    .putString("sumire_keyboard_style_preference", "third-flick")
                    .putString("sumire_input_method_preference", "toggle").commit())
                ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { scenario ->
                    scenario.onActivity { it.restartEditorInput(clearText = true) }
                    tap(key("あ", floating))
                    awaitText(scenario, "あ")
                }
                assertEquals("third-flick", prefs.getString("sumire_keyboard_style_preference", null))
                check(prefs.edit().putString("sumire_keyboard_style_preference", "second-flick").commit())
                ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { scenario ->
                    scenario.onActivity { it.restartEditorInput(clearText = true) }
                    tap(key("あ", floating))
                    awaitText(scenario, "あ")
                }
            }
        } finally {
            createdId?.let { repository.deleteLayoutConfirmed(it) }
            prefs.edit().clear().apply {
                for ((key, value) in originalPrefs) when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }.commit()
            if (oldIme.isNotEmpty() && oldIme != "null") shell("ime set $oldIme")
            if (!wasEnabled) shell("ime disable $ime")
            db.close()
        }
    }
}
