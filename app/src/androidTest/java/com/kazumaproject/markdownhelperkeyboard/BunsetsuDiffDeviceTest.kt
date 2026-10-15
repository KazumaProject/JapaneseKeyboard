package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Bitmap
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
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real on-screen ROMAJI input, real editor geometry and IME window touch dispatch. */
@RunWith(AndroidJUnit4::class)
class BunsetsuDiffDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ui get() = ins.uiAutomation
    private val context get() = ins.targetContext
    private lateinit var host: ActivityScenario<FastInputHostActivity>
    private val bubbleId get() = "${context.packageName}:id/bunsetsu_diff_candidate"
    private val enabledKey = "bunsetsu_diff_floating_enabled"
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command))
        .bufferedReader().use { it.readText().trim() }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            if (!node.isVisibleToUser) return
            result.add(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        ui.windows.mapNotNull { it.root }.forEach(::visit)
        return result
    }
    private fun bubbles() = nodes().filter { it.viewIdResourceName == bubbleId }
    private fun bounds(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)
    private fun text(): String { var text = ""; host.onActivity { text = it.editText.text.toString() }; return text }
    private fun composing(): Boolean { var value = false; host.onActivity { value = BaseInputConnection.getComposingSpanStart(it.editText.text) >= 0 }; return value }
    private fun await(message: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 30_000
        do { if (condition()) return; SystemClock.sleep(100) } while (SystemClock.uptimeMillis() < end)
        capture("failure")
        fail("$message; text=[${text()}]; nodes=" + nodes().joinToString { "${it.viewIdResourceName}:${it.text}" })
    }
    private fun tap(rect: Rect) {
        val now = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            val event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, rect.exactCenterX(), rect.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(ui.injectInputEvent(event, true)); event.recycle()
            SystemClock.sleep(40)
        }
        SystemClock.sleep(100)
    }
    private fun key(name: String) {
        val id = "${context.packageName}:id/$name"
        await("Missing key $name") { nodes().any { it.viewIdResourceName == id } }
        tap(bounds(nodes().first { it.viewIdResourceName == id }))
    }
    private fun type(romaji: String) = romaji.forEach { key("key_$it") }
    private fun choices(node: AccessibilityNodeInfo) = node.text.toString().split("\n")
    private fun capture(name: String) {
        val directory = File(context.cacheDir, "bunsetsu-diff-qa").apply { mkdirs() }
        ui.takeScreenshot()?.let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
        File(directory, "$name.txt").writeText(nodes().joinToString("\n") { "${it.viewIdResourceName} ${it.text} ${bounds(it)}" })
    }

    @Test fun rawInputTapAndKeyboardCyclePreserveOtherSegmentsAndFixedPlacement() = withEditor(live = false) {
        type("ashitahatoukyouniikimasu")
        val raw = "あしたはとうきょうにいきます"
        await("Raw composition did not arrive") { text() == raw && composing() }
        await("No aligned difference bubbles") { bubbles().any { choices(it).size > 1 && raw.contains(choices(it)[0]) } }
        assertTrue("Expected simultaneous alternatives for multiple segments", bubbles().size >= 2)
        val bubble = bubbles().first { choices(it).size > 1 && raw.contains(choices(it)[0]) }
        val options = choices(bubble)
        val label = bubble.text.toString()
        val position = bounds(bubble)
        val expected = raw.replaceFirst(options[0], options[1])
        capture("raw-preview")
        tap(position)
        await("Tap changed a different range") { text() == expected && composing() }
        await("Bubble changed order or disappeared") { bubbles().any { it.text.toString() == label } }
        assertEquals(position, bounds(bubbles().first { it.text.toString() == label }))
        SystemClock.sleep(1500)
        assertEquals(expected, text())
        assertEquals(label, bubbles().first { it.text.toString() == label }.text.toString())
        capture("selected")
        val next = options[2 % options.size]
        key("key_space")
        await("Existing conversion key did not use the fixed alternatives") { text() == raw.replaceFirst(options[0], next) }
        assertTrue(composing())
        val beforeOtherSegment = text()
        val other = bubbles().first { it.text.toString() != label }
        val otherOptions = choices(other)
        tap(bounds(other))
        await("Switching another bubble lost the first segment choice") {
            text() == beforeOtherSegment.replaceFirst(otherOptions[0], otherOptions[1])
        }
        assertTrue(composing())
        capture("multiple-selected")
        val beforeDisable = text()
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(enabledKey, false).commit()
        await("Disabled bubbles remain visible") { bubbles().isEmpty() }
        assertEquals(beforeDisable, text())
        key("key_return")
        await("Conversion was not committed") { !composing() }
        assertEquals(beforeDisable, text())
    }

    @Test fun liveConversionTapKeepsCompositionAndEditorSwitchDropsBubbles() = withEditor(live = true) {
        type("toukyou")
        await("No live conversion alternatives") { bubbles().isNotEmpty() && text() != "とうきょう" }
        val bubble = bubbles().first()
        val options = choices(bubble)
        val before = text()
        assertTrue(options.size > 1)
        val current = options.indexOfFirst { before.contains(it) }
        assertTrue(current >= 0)
        tap(bounds(bubble))
        await("Live conversion tap did not cycle") { text() == before.replaceFirst(options[current], options[(current + 1) % options.size]) }
        assertTrue(composing())
        host.onActivity { it.editText.setText(""); it.restartEditorInput(false) }
        await("Previous editor bubbles survived restart") { bubbles().isEmpty() }
    }

    @Test fun anEditorWithoutGeometryShowsNoFallbackBubble() = withEditor(live = false, denyGeometry = true) {
        type("hashi")
        await("Raw text did not arrive") { text() == "はし" && composing() }
        SystemClock.sleep(1800)
        assertTrue(bubbles().isEmpty())
        assertEquals("はし", text())
    }

    @Test fun aCompositionAfterCommittedTextUsesTheCorrectAbsoluteCharacterPositions() = withEditor(live = false) {
        val prefix = "確定済み🙂："
        host.onActivity { it.editText.setText(prefix); it.restartEditorInput(false) }
        SystemClock.sleep(500)
        type("hashi")
        await("Composition after prefix did not arrive") { text() == prefix + "はし" && composing() }
        await("No bubble for the nonzero composing range") { bubbles().isNotEmpty() }
        val bubble = bubbles().first()
        val options = choices(bubble)
        tap(bounds(bubble))
        await("Tap modified committed text or used the wrong offset") { text() == prefix + options[1] && composing() }
        key("key_return")
        await("Commit after prefix did not finish") { !composing() }
        assertEquals(prefix + options[1], text())
    }

    private fun withEditor(live: Boolean, denyGeometry: Boolean = false, action: () -> Unit) {
        check(context.packageName.startsWith("com.kazumaproject.floatingqa")) { "Use investigation/floating-panel.init.gradle" }
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val previous = preferences.all.toMap()
        val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val oldIme = shell("settings get secure default_input_method")
        val wasEnabled = shell("settings get secure enabled_input_methods").split(':').any { it.substringBefore(';') == ime }
        val hardware = shell("settings get secure show_ime_with_hard_keyboard")
        ui.serviceInfo = ui.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        try {
            shell("input keyevent WAKEUP"); shell("wm dismiss-keyguard")
            shell("settings put secure show_ime_with_hard_keyboard 1")
            preferences.edit().clear()
                .putString("keyboard_order_preference", "[\"ROMAJI\"]")
                .putBoolean("save_last_used_keyboard", false)
                .putBoolean(enabledKey, true)
                .putBoolean("live_conversion_preference", live)
                .putBoolean("conversion_bunsetsu_separation_preference", false)
                .putBoolean("conversion_bunsetsu_cursor_move_preference", false)
                .putBoolean("learn_dictionary_preference", false)
                .putBoolean("clipboard_preview_enable_preference", false)
                .putBoolean("candidate_tab_visibility_preference", false)
                .putBoolean("enable_ai_conversion_zenz_preference", false)
                .putInt("n_best_preference", 4).commit()
            shell("ime enable $ime"); shell("ime set $ime")
            host = ActivityScenario.launch(Intent(context, FastInputHostActivity::class.java).putExtra("deny_cursor_updates", denyGeometry))
            host.onActivity { it.restartEditorInput(true) }
            await("ROMAJI keyboard unavailable") { nodes().any { it.viewIdResourceName == "${context.packageName}:id/key_a" } }
            SystemClock.sleep(1500)
            action()
        } finally {
            if (::host.isInitialized) host.close()
            if (oldIme != "null") shell("ime set $oldIme")
            if (!wasEnabled && oldIme != ime) shell("ime disable $ime")
            if (hardware == "null") shell("settings delete secure show_ime_with_hard_keyboard")
            else shell("settings put secure show_ime_with_hard_keyboard $hardware")
            preferences.edit().clear().apply {
                previous.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> putBoolean(key, value)
                        is String -> putString(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                        is Set<*> -> @Suppress("UNCHECKED_CAST") putStringSet(key, value as Set<String>)
                    }
                }
            }.commit()
        }
    }
}
