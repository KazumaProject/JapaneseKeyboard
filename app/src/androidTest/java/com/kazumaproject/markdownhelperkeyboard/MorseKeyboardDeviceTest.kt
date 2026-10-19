package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.LocaleManager
import android.os.LocaleList
import androidx.test.filters.SdkSuppress
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputMethodManager
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.morse.MorsePreferences
import com.kazumaproject.morse_keyboard.MorsePresets
import com.kazumaproject.morse_keyboard.MorseControl
import com.kazumaproject.morse_keyboard.MorseControlLayout
import com.kazumaproject.morse_keyboard.MorseControlPlacement
import com.kazumaproject.morse_keyboard.MorseMap
import com.kazumaproject.morse_keyboard.MorseEntry
import com.kazumaproject.morse_keyboard.MorseOutput
import com.kazumaproject.morse_keyboard.MorseCommand
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Run with an isolated applicationId containing .morsetest; never modifies the installed IME's data. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 35)
class MorseKeyboardDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val automation get() = ins.uiAutomation
    private val context get() = ins.targetContext
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
        .bufferedReader().use { it.readText() }
    private fun nodes(): List<AccessibilityNodeInfo> = buildList {
        fun visit(node: AccessibilityNodeInfo) {
            add(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        automation.windows.forEach { it.root?.let(::visit) }
    }
    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 20000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(80)
        }
        error("$message: " + nodes().filter { it.isVisibleToUser }.joinToString { "${it.text}:${it.contentDescription}" })
    }
    private fun key(label: String, slot: Int = 0): AccessibilityNodeInfo {
        var result: AccessibilityNodeInfo? = null
        await("Missing $label in slot $slot") {
            result = nodes().filter { it.isVisibleToUser && it.packageName?.toString() == context.packageName &&
                (it.text?.toString() == label || it.contentDescription?.toString() == label) }
                .sortedBy { Rect().also(it::getBoundsInScreen).left }.getOrNull(slot)
            result != null
        }
        return result!!
    }
    private fun tap(node: AccessibilityNodeInfo, holdMs: Long = 25) {
        val bounds = Rect().also(node::getBoundsInScreen)
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { check(automation.injectInputEvent(event, true)) } finally { event.recycle() }
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(holdMs)
        }
        SystemClock.sleep(30)
    }
    private fun holdDotUntilThreeElements() {
        val bounds = Rect().also(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_dot))::getBoundsInScreen)
        val down = SystemClock.uptimeMillis()
        fun send(action: Int) {
            val event = MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,bounds.exactCenterX(),bounds.exactCenterY(),0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { check(automation.injectInputEvent(event,true)) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN)
        try {
            await("held paddle must repeat") { nodes().any { it.contentDescription?.toString()?.startsWith("... →") == true } }
        } finally { send(MotionEvent.ACTION_UP) }
        SystemClock.sleep(260)
    }
    private fun signal(pattern: String, slot: Int = 0) {
        pattern.forEach { tap(key(context.getString(if (it == '.') com.kazumaproject.morse_keyboard.R.string.morse_dot else com.kazumaproject.morse_keyboard.R.string.morse_dash), slot)) }
        tap(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_boundary), slot))
    }
    private fun text(host: ActivityScenario<FastInputHostActivity>): String {
        var text = ""; host.onActivity { text = it.editText.text.toString() }; return text
    }
    private fun reconnectFocusedEditor(host: ActivityScenario<FastInputHostActivity>, padLabel: String) {
        key(padLabel)
        await("editor owns window focus and input connection") {
            var connected = false
            host.onActivity { connected = it.editText.hasWindowFocus() &&
                it.getSystemService(InputMethodManager::class.java).isActive(it.editText) }
            connected
        }
        host.onActivity { it.restartEditorInput(true) }
        ins.waitForIdleSync()
        key(padLabel)
        // The focus callback also restarts input asynchronously. Let that session handover finish
        // before sending a signal, since onStartInput intentionally clears the previous buffer.
        SystemClock.sleep(250)
    }
    private fun composing(host: ActivityScenario<FastInputHostActivity>): Boolean {
        var composing = false
        host.onActivity { composing = BaseInputConnection.getComposingSpanStart(it.editText.text) >= 0 }
        return composing
    }
    private fun screenshot(name: String) {
        ins.waitForIdleSync()
        SystemClock.sleep(180)
        val folder = File(context.getExternalFilesDir(null), "morse-shots").apply { mkdirs() }
        val bitmap = automation.takeScreenshot()
        File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun overlap(slot: Int) {
        val dot = Rect().also(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_dot), slot)::getBoundsInScreen)
        val dash = Rect().also(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_dash), slot)::getBoundsInScreen)
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, index: Int, ids: IntArray, bounds: List<Rect>) {
            val properties = Array(ids.size) { i -> MotionEvent.PointerProperties().apply { id = ids[i]; toolType = MotionEvent.TOOL_TYPE_FINGER } }
            val coordinates = Array(ids.size) { i -> MotionEvent.PointerCoords().apply {
                x = bounds[i].exactCenterX(); y = bounds[i].exactCenterY(); pressure = 1f; size = 1f
            } }
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                ids.size, properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { check(automation.injectInputEvent(event, true)) } finally { event.recycle() }
            SystemClock.sleep(30)
        }
        send(MotionEvent.ACTION_DOWN, 0, intArrayOf(3), listOf(dot))
        send(MotionEvent.ACTION_POINTER_DOWN, 1, intArrayOf(3, 19), listOf(dot, dash))
        send(MotionEvent.ACTION_POINTER_UP, 0, intArrayOf(3, 19), listOf(dot, dash))
        send(MotionEvent.ACTION_UP, 0, intArrayOf(19), listOf(dash))
        tap(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_boundary), slot))
    }

    private fun chooseGuide(label: String, pattern: String) {
        val description = context.getString(com.kazumaproject.morse_keyboard.R.string.morse_guide_choose,
            label, pattern.map { if (it == '.') "・" else "－" }.joinToString(" "))
        repeat(12) {
            val entry = nodes().firstOrNull { it.isVisibleToUser && it.contentDescription?.toString() == description }
            if (entry != null) { assertTrue(entry.performAction(AccessibilityNodeInfo.ACTION_CLICK)); return }
            nodes().firstOrNull { it.isVisibleToUser && it.className?.toString() == "android.widget.ScrollView" }
                ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            SystemClock.sleep(100)
        }
        screenshot("guide-missing-entry")
        error("Missing guide entry $description: " + nodes().filter { it.isVisibleToUser }.joinToString { "${it.text}:${it.contentDescription}" })
    }

    @Test fun beginnerGuideIsOptionalAndFollowsWabunRomajiEnglishAndCustomMaps() {
        org.junit.Assume.assumeTrue(context.packageName.contains(".morsetest"))
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        val target = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val oldIme = shell("settings get secure default_input_method").trim()
        val oldHardware = shell("settings get secure show_ime_with_hard_keyboard").trim()
        val enabled = shell("ime list -s").lineSequence().any { it.trim() == target }
        val localeManager = context.getSystemService(LocaleManager::class.java)
        val oldLocales = localeManager.applicationLocales
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val store = MorsePreferences(context)
        val customText = "ありがとうございます".repeat(8) + "😊"
        val custom = MorseMap("guide-device", "ガイド用", listOf(MorseEntry(".", MorseOutput.LiteralText(customText)),
            MorseEntry("-", MorseOutput.Command(MorseCommand.DELETE))))
        fun hasText(value: String) = nodes().any { it.isVisibleToUser && it.text?.toString() == value }
        try {
            localeManager.applicationLocales = LocaleList.forLanguageTags("ja")
            shell("settings put secure show_ime_with_hard_keyboard 1")
            shell("ime enable $target"); shell("ime set $target")
            store.saveControlLayout(MorseControlLayout()); store.saveReverse(false); store.saveMap(custom)
            prefs.edit().putString("keyboard_order_preference", "[\"MORSE\"]")
                .putString("morse_style", "TAP").putBoolean("morse_auto_boundary", false)
                .putBoolean("morse_sound", false).putBoolean("morse_beginner_guide", false)
                .putBoolean("save_last_used_keyboard", false).putBoolean("live_conversion_preference", false)
                .putBoolean("keyboard_floating_preference", false).putString("morse_selected_map", MorsePresets.WABUN).commit()
            ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { host ->
                reconnectFocusedEditor(host, context.getString(com.kazumaproject.morse_keyboard.R.string.morse_dot))
                assertFalse(hasText(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_guide_pick)))
                prefs.edit().putBoolean("morse_beginner_guide", true).commit()
                reconnectFocusedEditor(host, context.getString(com.kazumaproject.morse_keyboard.R.string.morse_dot))
                await("beginner list shown") { hasText(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_guide_pick)) }
                chooseGuide("る", "-.--.")
                screenshot("guide-wabun")
                tap(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_dash)))
                await("guide advances after actual dash") { nodes().any { it.isVisibleToUser && it.text?.toString()?.startsWith("1 / 5") == true } }
                assertEquals(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_guide_next),
                    key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_dot)).stateDescription?.toString())
                signal(".--.")
                await("guide input is real composing input") { text(host) == "る" && composing(host) }
                assertTrue(hasText(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_guide_completed, 5)))
                screenshot("guide-completed")
                var candidate: AccessibilityNodeInfo? = null
                await("guide leaves conversion candidates accessible") {
                    val guideBottom = Rect().also(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_guide_completed, 5))::getBoundsInScreen).bottom
                    candidate = nodes().firstOrNull { it.isVisibleToUser && it.text?.toString()?.trim() == "る" &&
                        Rect().also(it::getBoundsInScreen).top > guideBottom }
                    candidate != null
                }
                tap(candidate!!)
                await("candidate can be selected while guide is open") { text(host) == "る" && !composing(host) }
                val beforeDemo = text(host)
                key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_guide_replay)).performAction(AccessibilityNodeInfo.ACTION_CLICK)
                SystemClock.sleep(4200)
                assertEquals("guide replay never enters text", beforeDemo, text(host))
                tap(key(MorsePreferences(context).allMaps().single { it.id == MorsePresets.ENGLISH }.name))
                chooseGuide("z", "--..")
                signal("--..")
                await("English guide follows its map") { text(host).endsWith("z") }
                screenshot("guide-english")
                tap(key(MorsePreferences(context).allMaps().single { it.id == MorsePresets.ROMAJI }.name))
                await("romaji guide map is shown") { hasText(MorsePreferences(context).allMaps().single { it.id == MorsePresets.ROMAJI }.name) }
                chooseGuide("k", "-.-"); signal("-.-")
                chooseGuide("a", ".-"); signal(".-")
                await("romaji guide uses Japanese composing converter") { text(host).endsWith("か") && composing(host) }
                tap(key(custom.name))
                chooseGuide(customText, ".")
                screenshot("guide-custom-text")
                tap(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_dash)))
                await("mismatched guide signal") { nodes().any { it.isVisibleToUser && it.text?.toString()?.contains("順序が違います") == true } }
                val beforeReset = text(host)
                key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_guide_reset)).performAction(AccessibilityNodeInfo.ACTION_CLICK)
                assertEquals(beforeReset, text(host))
                signal("."); await("custom literal in guide") { text(host).endsWith("😊") }
                chooseGuide(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_delete), "-")
                screenshot("guide-custom-command")
                signal("-"); await("custom command acts only after actual input") { !text(host).endsWith("😊") }
                prefs.edit().putBoolean("morse_beginner_guide", false).commit()
                reconnectFocusedEditor(host, context.getString(com.kazumaproject.morse_keyboard.R.string.morse_dot))
                assertFalse(nodes().any { it.isVisibleToUser && it.contentDescription?.toString() == context.getString(com.kazumaproject.morse_keyboard.R.string.morse_guide_replay) })
            }
            prefs.edit().putBoolean("morse_beginner_guide", true).putBoolean("keyboard_floating_preference", true)
                .putString("morse_selected_map", MorsePresets.WABUN).commit()
            ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { host ->
                reconnectFocusedEditor(host, context.getString(com.kazumaproject.morse_keyboard.R.string.morse_dot))
                chooseGuide("る", "-.--.")
                assertEquals("one guide per active keyboard", 1, nodes().count { it.isVisibleToUser &&
                    it.contentDescription?.toString() == context.getString(com.kazumaproject.morse_keyboard.R.string.morse_guide_replay) })
                signal("-.--.")
                await("guide with floating keyboard") { text(host) == "る" }
                key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_guide_list)).performAction(AccessibilityNodeInfo.ACTION_CLICK)
                await("guide list collapses") { nodes().none { it.isVisibleToUser &&
                    it.contentDescription?.toString()?.startsWith("るのガイド") == true } }
                screenshot("guide-floating")
                key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_guide_list)).performAction(AccessibilityNodeInfo.ACTION_CLICK)
                chooseGuide("る", "-.--.")
                host.onActivity { it.restartEditorInput(true) }
                SystemClock.sleep(4200)
                assertEquals("restarted editor receives no demo input", "", text(host))
                prefs.edit().putString("morse_style", "STRAIGHT").commit()
                host.onActivity { it.restartEditorInput(true) }
                reconnectFocusedEditor(host, context.getString(com.kazumaproject.morse_keyboard.R.string.morse_straight))
                chooseGuide("へ", ".")
                tap(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_straight)), 25)
                tap(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_boundary)))
                await("straight key guide") { text(host) == "へ" }
            }
            prefs.edit().putString("keyboard_order_preference", "[\"SPLIT\"]")
                .putBoolean("keyboard_floating_preference", false).putString("morse_style", "TAP")
                .putString("split_keyboard_main_type", "MORSE").putString("split_keyboard_sub_type", "MORSE")
                .putFloat("split_keyboard_main_portrait_width", 180f).putFloat("split_keyboard_sub_portrait_width", 180f)
                .putFloat("split_keyboard_main_portrait_height", 280f).putFloat("split_keyboard_sub_portrait_height", 280f).commit()
            ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { host ->
                reconnectFocusedEditor(host, context.getString(com.kazumaproject.morse_keyboard.R.string.morse_dot))
                for (slot in listOf(0, 1)) {
                    tap(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_dot), slot))
                    tap(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_delete), slot))
                    chooseGuide("へ", ".")
                    signal(".", slot)
                    await("split slot $slot accepts guided input") { text(host).endsWith("へ") }
                    assertEquals(1, nodes().count { it.isVisibleToUser && it.contentDescription?.toString() ==
                        context.getString(com.kazumaproject.morse_keyboard.R.string.morse_guide_replay) })
                }
                screenshot("guide-split")
                shell("input keyevent KEYCODE_BACK")
                await("hiding the keyboard also hides the guide") { nodes().none { it.isVisibleToUser &&
                    it.contentDescription?.toString() == context.getString(com.kazumaproject.morse_keyboard.R.string.morse_guide_replay) } }
            }
        } finally {
            prefs.edit().putBoolean("morse_beginner_guide", false).putBoolean("keyboard_floating_preference", false).commit()
            store.deleteMap(custom.id)
            localeManager.applicationLocales = oldLocales
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
            if (!enabled) shell("ime disable $target")
            if (oldHardware == "null") shell("settings delete secure show_ime_with_hard_keyboard")
            else shell("settings put secure show_ime_with_hard_keyboard $oldHardware")
        }
    }

    @Test fun composingConversionAndPointerIdentityAcrossNormalFloatingAndBothSplitSlots() {
        org.junit.Assume.assumeTrue("Use an isolated .morsetest applicationId", context.packageName.contains(".morsetest"))
        val target = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val oldIme = shell("settings get secure default_input_method").trim()
        val oldHardware = shell("settings get secure show_ime_with_hard_keyboard").trim()
        val enabled = shell("ime list -s").lineSequence().any { it.trim() == target }
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val localeManager = context.getSystemService(LocaleManager::class.java)
        val oldLocales = localeManager.applicationLocales
        val oldNightMode = shell("cmd uimode night").substringAfter("Night mode: ").trim()
        try {
            MorsePreferences(context).saveControlLayout(MorseControlLayout())
            MorsePreferences(context).saveReverse(false)
            localeManager.applicationLocales = LocaleList.forLanguageTags("ja")
            shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard")
            shell("settings put secure show_ime_with_hard_keyboard 1")
            shell("ime enable $target"); shell("ime set $target")
            for (presentation in listOf("normal", "floating", "split")) {
                prefs.edit().putString("keyboard_order_preference", if (presentation == "split") "[\"SPLIT\"]" else "[\"MORSE\"]")
                    .putString("morse_style", "TAP").putBoolean("morse_auto_boundary", false)
                    .putBoolean("morse_sound", false).putBoolean("morse_vibration", false)
                    .putString("morse_selected_map", MorsePresets.WABUN)
                    .putBoolean("keyboard_floating_preference", presentation == "floating")
                    .putBoolean("save_last_used_keyboard", false).putBoolean("live_conversion_preference", false)
                    .putString("split_keyboard_main_type", "MORSE").putString("split_keyboard_sub_type", "MORSE")
                    .putFloat("split_keyboard_main_portrait_width", 180f).putFloat("split_keyboard_sub_portrait_width", 180f)
                    .putFloat("split_keyboard_main_portrait_height", 280f).putFloat("split_keyboard_sub_portrait_height", 280f).commit()
                ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { host ->
                    key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_dot))
                    for (slot in if (presentation == "split") listOf(0, 1) else listOf(0)) {
                        host.onActivity { it.restartEditorInput(true) }
                        key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_dot), slot)
                        overlap(slot)
                        await("$presentation slot $slot must compose い") { text(host) == "い" && composing(host) }
                        tap(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_delete), slot))
                        await("delete reading") { text(host).isEmpty() }
                        signal(".-..", slot); await("か") { text(host) == "か" }
                        signal("..", slot); await("が") { text(host) == "が" }
                        signal("-...", slot); signal("..--.", slot); await("がぱ") { text(host) == "がぱ" }
                        signal(".--", slot)
                        tap(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_small), slot))
                        await("small kana") { text(host) == "がぱゃ" && composing(host) }
                    }
                    // Invalid signals change Enter into Clear without committing the Japanese reading.
                    val beforeClear = text(host)
                    repeat(8) { tap(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_dot))) }
                    key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_clear_signal))
                    if (presentation == "normal") screenshot("invalid-signal-clear")
                    tap(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_clear_signal)))
                    assertEquals(beforeClear, text(host))
                    assertTrue(composing(host))
                    key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_enter))
                    screenshot("$presentation-wabun")
                    if (presentation == "normal") {
                        host.onActivity { it.restartEditorInput(true) }
                        signal("-.-."); signal("-.."); signal(".-.-.")
                        await("にほん") { text(host) == "にほん" && composing(host) }
                        await("Japanese reading candidate") { nodes().any { it.isVisibleToUser && it.text?.toString()?.trim() == "日本" } }
                        tap(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_convert)))
                        await("Japanese conversion candidate") { nodes().any { it.isVisibleToUser && it.text?.toString()?.trim() == "日本" } }
                        screenshot("normal-conversion")
                        tap(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_enter)))
                        await("commit converted Japanese") { text(host).contains("日本") && !composing(host) }
                        val english = MorsePreferences(context).allMaps().single { it.id == MorsePresets.ENGLISH }.name
                        tap(key(english))
                        signal(".-"); await("English literal") { text(host).endsWith("a") && !composing(host) }
                    }
                }
            }
            prefs.edit().putString("keyboard_order_preference", "[\"MORSE\"]")
                .putBoolean("keyboard_floating_preference", false).putString("morse_selected_map", MorsePresets.WABUN)
                .putString("morse_style", "TAP").putBoolean("morse_auto_boundary", false).commit()
            for (placement in listOf(MorseControlPlacement.LEFT, MorseControlPlacement.RIGHT, MorseControlPlacement.BOTH_SIDES)) {
                MorsePreferences(context).saveControlLayout(MorseControlLayout(placement = placement))
                ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { host ->
                    reconnectFocusedEditor(host, context.getString(com.kazumaproject.morse_keyboard.R.string.morse_dot))
                    signal(".")
                    await("$placement editor is connected") { text(host) == "へ" && composing(host) }
                    tap(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_delete)))
                    await("clear connection probe") { text(host).isEmpty() }
                    val dot = Rect().also(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_dot))::getBoundsInScreen)
                    val dash = Rect().also(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_dash))::getBoundsInScreen)
                    val divide = Rect().also(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_boundary))::getBoundsInScreen)
                    val keyboard = Rect().also(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_keyboard))::getBoundsInScreen)
                    if (placement != MorseControlPlacement.RIGHT) assertTrue(divide.right <= dot.left)
                    if (placement != MorseControlPlacement.LEFT) assertTrue(keyboard.left >= dash.right)
                    signal(".-..")
                    await("$placement accepts input") { text(host) == "か" && composing(host) }
                    screenshot("controls-${placement.name.lowercase()}")
                    // The same repeat deletion path handles the signal buffer and composing reading.
                    repeat(3) { tap(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_dot))) }
                    tap(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_delete)), 1000)
                    await("held deletion clears signals and reading") { text(host).isEmpty() }
                    signal(".-")
                    await("deletion stopped after release") { text(host) == "い" }
                    SystemClock.sleep(200)
                    assertEquals("い", text(host))
                }
            }
            MorsePreferences(context).saveControlLayout(MorseControlLayout(listOf(
                listOf(MorseControl.SPACE, MorseControl.DELETE, MorseControl.ENTER),
                listOf(MorseControl.BOUNDARY, MorseControl.CONVERT, MorseControl.KEYBOARD)), abovePads = true))
            MorsePreferences(context).saveReverse(true)
            prefs.edit().putString("keyboard_order_preference", "[\"MORSE\"]")
                .putBoolean("keyboard_floating_preference", false).putString("morse_selected_map", MorsePresets.WABUN)
                .putString("morse_style", "PADDLE_A").putInt("morse_wpm", 5)
                .putBoolean("morse_auto_boundary", false).putBoolean("morse_sound", true).commit()
            ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { host ->
                reconnectFocusedEditor(host, context.getString(com.kazumaproject.morse_keyboard.R.string.morse_dot))
                val dot = Rect().also(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_dot))::getBoundsInScreen)
                val dash = Rect().also(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_dash))::getBoundsInScreen)
                val divide = Rect().also(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_boundary))::getBoundsInScreen)
                assertTrue("Reversed paddle order is restored", dash.left < dot.left)
                assertTrue("Controls are above paddles", divide.bottom <= dot.top)
                tap(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_dot)))
                tap(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_boundary)))
                await("new editor receives paddle input") { text(host) == "へ" }
                tap(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_delete)))
                await("clear warm-up reading") { text(host).isEmpty() }
                holdDotUntilThreeElements()
                tap(key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_boundary)))
                await("held paddle produces three dots") { text(host) == "ら" && composing(host) }
                screenshot("custom-layout-paddle")
                shell("cmd uimode night yes")
                SystemClock.sleep(500)
                key(context.getString(com.kazumaproject.morse_keyboard.R.string.morse_dot))
                screenshot("dark-paddle")
                if(oldNightMode in setOf("yes", "no", "auto", "custom")) shell("cmd uimode night $oldNightMode")
            }
            await("tone synthesis stops after the input view hides") {
                Thread.getAllStackTraces().keys.none { it.isAlive && it.name == "MorsePCM" }
            }
            MorsePreferences(context).saveControlLayout(MorseControlLayout())
            MorsePreferences(context).saveReverse(false)
            prefs.edit().putString("keyboard_order_preference", "[\"MORSE\"]")
                .putBoolean("keyboard_floating_preference", false).putString("morse_selected_map", MorsePresets.WABUN)
                .putString("morse_style", "STRAIGHT").putBoolean("morse_auto_boundary", true)
                .putInt("morse_boundary_ms", 250).commit()
            ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { host ->
                val label = context.getString(com.kazumaproject.morse_keyboard.R.string.morse_straight)
                reconnectFocusedEditor(host, label)
                tap(key(label), 25); await("straight short press") { text(host) == "へ" }
                tap(key(label), 300); await("straight long press") { text(host) == "へむ" }
                tap(key(label), 25)
                host.onActivity { it.restartEditorInput(true) }
                SystemClock.sleep(800)
                assertEquals("A cancelled automatic boundary must not write into the restarted editor", "", text(host))
                host.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                await("landscape layout") { context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
                key(label); screenshot("landscape-straight")
                shell("cmd uimode night yes")
                SystemClock.sleep(500)
                key(label); screenshot("dark-straight")
            }
        } finally {
            MorsePreferences(context).saveControlLayout(MorseControlLayout())
            MorsePreferences(context).saveReverse(false)
            localeManager.applicationLocales = oldLocales
            if(oldNightMode in setOf("yes", "no", "auto", "custom")) shell("cmd uimode night $oldNightMode")
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
            if (!enabled) shell("ime disable $target")
            if (oldHardware == "null") shell("settings delete secure show_ime_with_hard_keyboard")
            else shell("settings put secure show_ime_with_hard_keyboard $oldHardware")
        }
    }
}
