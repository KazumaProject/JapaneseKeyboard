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
        fail("$message; text=[${text()}]; nodes=" + nodes().joinToString { "${it.viewIdResourceName}:${it.text}:clickable=${it.isClickable}:description=${it.contentDescription}" })
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
    private fun options(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> =
        (0 until node.childCount).mapNotNull(node::getChild).filter {
            it.viewIdResourceName == "${context.packageName}:id/bunsetsu_diff_option"
        }
    private fun choices(node: AccessibilityNodeInfo) = options(node).map { it.text.toString() }
    private fun label(node: AccessibilityNodeInfo) = choices(node).joinToString("\n")
    private fun tapOption(node: AccessibilityNodeInfo, text: String) = tap(bounds(options(node).single { it.text.toString() == text }))
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
        val bubble = bubbles().first { choices(it).size >= 3 && raw.contains(choices(it)[0]) }
        val options = choices(bubble)
        val label = label(bubble)
        val position = bounds(bubble)
        val expected = raw.replaceFirst(options[0], options[1])
        capture("raw-preview")
        tapOption(bubble, options.last())
        await("Tapping a nonadjacent option did not select that exact alternative") {
            text() == raw.replaceFirst(options[0], options.last()) && composing()
        }
        val selectedBubble = bubbles().first { label(it) == label }
        tapOption(selectedBubble, options.last())
        SystemClock.sleep(300)
        assertEquals(raw.replaceFirst(options[0], options.last()), text())
        assertTrue(options(bubbles().first { label(it) == label }).last().isSelected)
        tapOption(bubbles().first { label(it) == label }, options[0])
        await("Tapping the original option did not restore only that segment") { text() == raw && composing() }
        tapOption(bubbles().first { label(it) == label }, options[1])
        await("Tap changed a different range") { text() == expected && composing() }
        await("Bubble changed order or disappeared") { bubbles().any { label(it) == label } }
        assertEquals(position, bounds(bubbles().first { label(it) == label }))
        SystemClock.sleep(1500)
        assertEquals(expected, text())
        assertEquals(label, label(bubbles().first { label(it) == label }))
        capture("selected")
        val next = options[2 % options.size]
        key("key_space")
        await("Existing conversion key did not use the fixed alternatives") { text() == raw.replaceFirst(options[0], next) }
        assertTrue(composing())
        val beforeOtherSegment = text()
        val other = bubbles().first { label(it) != label }
        val otherOptions = choices(other)
        tapOption(other, otherOptions[1])
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
        var readyBubble: AccessibilityNodeInfo? = null
        await("No alternatives for the completed live conversion") {
            val candidate = bubbles().firstOrNull { choices(it).firstOrNull() == "東京" }
            if (text() == "東京" && candidate != null) {
                readyBubble = candidate
                true
            } else false
        }
        val bubble = checkNotNull(readyBubble)
        val options = choices(bubble)
        val before = text()
        assertTrue(options.size > 1)
        val current = options.indexOfFirst { before.contains(it) }
        assertTrue(current >= 0)
        val selected = options[(current + options.size - 1) % options.size]
        tapOption(bubble, selected)
        await("Live conversion tap did not select the tapped alternative") { text() == before.replaceFirst(options[current], selected) }
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
        tapOption(bubble, options[1])
        await("Tap modified committed text or used the wrong offset") { text() == prefix + options[1] && composing() }
        key("key_return")
        await("Commit after prefix did not finish") { !composing() }
        assertEquals(prefix + options[1], text())
    }

    @Test fun candidateStripModeWorksWithoutEditorGeometryAndKeepsItsSentenceAnchor() =
        withEditor(live = false, denyGeometry = true, placement = "candidates") {
            type("ashitahatoukyouniikimasu")
            val raw = "あしたはとうきょうにいきます"
            await("Raw composition did not arrive") { text() == raw && composing() }
            await("No candidate-strip alternatives without editor geometry") { bubbles().size >= 2 }
            val candidateId = "${context.packageName}:id/suggestion_item_text_view"
            val candidates = nodes().filter { it.viewIdResourceName == candidateId }.map { it.text.toString() }
            val bubble = bubbles().first { choices(it).size >= 3 }
            val options = choices(bubble)
            val label = label(bubble)
            val position = bounds(bubble)
            assertTrue(raw.contains(options[0]))
            assertTrue(nodes().filter { it.viewIdResourceName == candidateId }.any {
                val rect = bounds(it)
                position.bottom < rect.top && position.centerX() in rect.left..rect.right
            })
            capture("candidate-strip-preview")
            tapOption(bubble, options.last())
            await("Candidate-strip tap did not directly select a nonadjacent alternative") {
                text() == raw.replaceFirst(options[0], options.last()) && composing()
            }
            tapOption(bubbles().first { label(it) == label }, options.last())
            SystemClock.sleep(300)
            assertEquals(raw.replaceFirst(options[0], options.last()), text())
            tapOption(bubbles().first { label(it) == label }, options[1])
            await("Candidate-strip tap changed an unrelated range") { text() == raw.replaceFirst(options[0], options[1]) && composing() }
            await("Anchored sentence was replaced by segment candidates") {
                nodes().filter { it.viewIdResourceName == candidateId }.map { it.text.toString() } == candidates
            }
            assertEquals(position, bounds(bubbles().first { label(it) == label }))
            SystemClock.sleep(1500)
            assertEquals(position, bounds(bubbles().first { label(it) == label }))
            capture("candidate-strip-selected")
            val beforeOther = text()
            val other = bubbles().first { label(it) != label }
            val otherOptions = choices(other)
            tapOption(other, otherOptions.last())
            await("Selecting a different segment lost the first segment's direct selection") {
                text() == beforeOther.replaceFirst(otherOptions[0], otherOptions.last()) && composing()
            }
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            preferences.edit().putString("bunsetsu_diff_floating_placement", "editor").commit()
            await("Editor mode must hide when its text geometry is unavailable") { bubbles().isEmpty() }
            preferences.edit().putString("bunsetsu_diff_floating_placement", "candidates").commit()
            await("Switching back lost the frozen candidates") { bubbles().any { label(it) == label } }
            val beforeCommit = candidates.first().trim()
            val target = nodes().first { it.viewIdResourceName == candidateId && it.text.toString() == candidates.first() }
            tap(bounds(target))
            await("Normal sentence candidate tap no longer commits the sentence") { text() == beforeCommit && !composing() }
            await("Committed candidate left stale bubbles") { bubbles().isEmpty() }
        }

    @Test fun savedCycleModeAndRuntimeTapBehaviorSwitchKeepSegmentsIndependent() =
        withEditor(live = false, tapBehavior = "cycle") {
            type("ashitahatoukyouniikimasu")
            val raw = "あしたはとうきょうにいきます"
            await("Cycle-mode floats did not appear") { text() == raw && bubbles().size >= 2 }
            val cycleBubble = bubbles().first { it.contentDescription?.contains("いきます") == true }
            val position = bounds(cycleBubble)
            tap(position)
            await("Saved cycle setting did not preserve next-candidate behavior") {
                text() == raw.replaceFirst("いきます", "行きます") && composing()
            }
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            preferences.edit().putString("bunsetsu_diff_floating_tap_behavior", "direct").commit()
            await("Direct selection did not become available after changing the setting") {
                bubbles().any { choices(it).size >= 3 }
            }
            val directBubble = bubbles().first { choices(it).size >= 3 }
            val options = choices(directBubble)
            val label = label(directBubble)
            val other = bubbles().first { label(it) != label }
            val otherOptions = choices(other)
            val otherPosition = bounds(other)
            tapOption(directBubble, options.last())
            await("Direct mode still cycled instead of choosing the tapped alternative") {
                text() == raw.replaceFirst(options[0], options.last()) && composing()
            }
            preferences.edit().putString("bunsetsu_diff_floating_tap_behavior", "cycle").commit()
            await("Cycle mode did not restore the whole-float tap action") {
                bubbles().all { it.isClickable && options(it).none { option -> option.isClickable } }
            }
            tap(position)
            await("Cycle mode did not advance from the last alternative back to the first") { text() == raw && composing() }
            tap(position)
            await("Cycle mode did not advance to the next alternative") { text() == raw.replaceFirst(options[0], options[1]) }
            val beforeOther = text()
            tap(otherPosition)
            await("Cycling a different segment lost the earlier segment choice") {
                text() == beforeOther.replaceFirst(otherOptions[0], otherOptions[1]) && composing()
            }
            preferences.edit().putString("bunsetsu_diff_floating_tap_behavior", "direct").commit()
            await("Switching back to direct mode lost the fixed candidate list") { bubbles().any { label(it) == label } }
            val beforeDirect = text()
            tapOption(bubbles().first { label(it) == label }, options[0])
            await("Direct selection after switching modes changed another segment") {
                text() == beforeDirect.replaceFirst(options[1], options[0]) && composing()
            }
        }

    private fun withEditor(live: Boolean, denyGeometry: Boolean = false, placement: String = "editor", tapBehavior: String? = null, action: () -> Unit) {
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
                .putString("bunsetsu_diff_floating_placement", placement)
                .putBoolean("live_conversion_preference", live)
                .putBoolean("conversion_bunsetsu_separation_preference", false)
                .putBoolean("conversion_bunsetsu_cursor_move_preference", false)
                .putBoolean("learn_dictionary_preference", false)
                .putBoolean("clipboard_preview_enable_preference", false)
                .putBoolean("candidate_tab_visibility_preference", false)
                .putBoolean("enable_ai_conversion_zenz_preference", false)
                .putInt("n_best_preference", 4).commit()
            if (tapBehavior != null) preferences.edit().putString("bunsetsu_diff_floating_tap_behavior", tapBehavior).commit()
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
