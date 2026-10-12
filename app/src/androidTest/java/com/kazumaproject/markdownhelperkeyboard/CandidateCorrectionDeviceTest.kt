package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Rect
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inspector.WindowInspector
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.core.data.floating_candidate.CandidateItem
import com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt in on a disposable emulator with -e candidate_correction_device_test true.
 * Run once with its hardware keyboard disabled for the touch modes, and once with it
 * enabled for physical input. -e candidate_correction_modes accepts a comma-separated subset.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class CandidateCorrectionDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context = ins.targetContext
    private val ui get() = ins.uiAutomation
    private var floatingKeyboard = false

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command))
        .bufferedReader().use { it.readText().trim() }

    private fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var result: T? = null
        var failure: Throwable? = null
        ins.runOnMainSync { try { result = block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST") return result as T
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30000
        while (SystemClock.uptimeMillis() < deadline) {
            if (onMain(condition)) return
            SystemClock.sleep(100)
        }
        error("$message; candidates=" + onMain { suggestions().take(8).map { it.string to it.yomi } } +
            "; floating=" + onMain { floatingSuggestions().take(8).map { it.word } })
    }

    private fun find(node: AccessibilityNodeInfo?, id: String): Rect? {
        node ?: return null
        if (node.isVisibleToUser && node.viewIdResourceName?.endsWith(":id/$id") == true) {
            return Rect().also(node::getBoundsInScreen).takeUnless { it.isEmpty }
        }
        for (index in 0 until node.childCount) find(node.getChild(index), id)?.let { return it }
        return null
    }

    private fun key(id: String): Rect? = onMain {
        // Floating keyboards move to another window. Accessibility can briefly retain the
        // previous docked coordinates, so obtain current screen coordinates from its views.
        val resourceId = context.resources.getIdentifier(id, "id", context.packageName)
        WindowInspector.getGlobalWindowViews().asReversed().firstNotNullOfOrNull { root ->
            val keyboard = root.findViewById<View>(if (floatingKeyboard) R.id.keyboard_view_floating else R.id.keyboard_view)
                ?: return@firstNotNullOfOrNull null
            val view = keyboard.findViewById<View>(resourceId) ?: return@firstNotNullOfOrNull null
            if (!view.isShown || !view.getGlobalVisibleRect(Rect())) return@firstNotNullOfOrNull null
            val position = IntArray(2)
            view.getLocationOnScreen(position)
            Rect(position[0], position[1], position[0] + view.width, position[1] + view.height)
        } ?: if (floatingKeyboard) null else ui.windows.firstNotNullOfOrNull { find(it.root, id) }
    }

    private fun flick(id: String, dx: Int = 0, dy: Int = 0) {
        var bounds: Rect? = null
        await("Missing $id") { key(id).also { bounds = it } != null }
        val time = SystemClock.uptimeMillis()
        val distance = 45 * context.resources.displayMetrics.density
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action,
                bounds!!.centerX() + if (action == MotionEvent.ACTION_DOWN) 0f else dx * distance,
                bounds!!.centerY() + if (action == MotionEvent.ACTION_DOWN) 0f else dy * distance, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(ui.injectInputEvent(event, true))
            event.recycle()
            SystemClock.sleep(30)
        }
        SystemClock.sleep(200)
    }

    private fun physical(code: Int) {
        val keyboardId = InputDevice.getDeviceIds().toList().mapNotNull(InputDevice::getDevice)
            .firstOrNull { !it.isVirtual && it.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC }
            ?.id ?: error("An emulator hardware keyboard is required")
        val time = SystemClock.uptimeMillis()
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            assertTrue(ui.injectInputEvent(KeyEvent(time, SystemClock.uptimeMillis(), action,
                code, 0, 0, keyboardId, 0, 0, InputDevice.SOURCE_KEYBOARD), true))
        }
        SystemClock.sleep(200)
    }

    private fun service(): IMEService? = WindowInspector.getGlobalWindowViews().asSequence()
        .flatMap { root ->
            val view = root.findViewById<View>(R.id.suggestion_recycler_view) ?: root
            generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
        }.filterIsInstance<IMEService>().firstOrNull()

    private fun field(ime: IMEService, name: String): Any? = IMEService::class.java.getDeclaredField(name)
        .also { it.isAccessible = true }.get(ime)

    @Suppress("UNCHECKED_CAST")
    private fun suggestions(): List<Candidate> = service()?.let {
        field(it, "currentCandidateStripCandidates") as List<Candidate>
    }.orEmpty()

    @Suppress("UNCHECKED_CAST")
    private fun floatingSuggestions(): List<CandidateItem> = service()?.let {
        field(it, "fullSuggestionsList") as List<CandidateItem>
    }.orEmpty()

    private fun converting(): Boolean = service()?.let { (field(it, "isHenkan") as AtomicBoolean).get() } == true

    private data class Mode(val name: String, val bunsetsu: Boolean, val cursor: Boolean,
        val conversionTab: Boolean = false, val floating: Boolean = false, val physical: Boolean = false)

    @Test fun correctionsSurviveConversionSelectionCancellationAndReentry() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("candidate_correction_device_test") == "true")
        AppPreference.init(context)
        val backup = AppPreference.exportAllToJson()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val oldIme = shell("settings get secure default_input_method")
        val target = ComponentName(context, IMEService::class.java).flattenToShortString()
        val enabled = shell("ime list -s").lines().contains(target)
        val info = ui.serviceInfo
        val oldFlags = info.flags
        info.flags = oldFlags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        ui.serviceInfo = info
        var host: CandidateHeightTestHostActivity? = null
        try {
            shell("ime enable $target")
            shell("ime set $target")
            val modes = listOf(Mode("normal", false, false), Mode("bunsetsu", true, false),
                Mode("cursor", true, true), Mode("conversion-tab", true, true, conversionTab = true),
                Mode("floating", true, true, floating = true), Mode("physical", true, true, physical = true))
            val requestedModes = InstrumentationRegistry.getArguments().getString("candidate_correction_modes")?.split(',')
            val hardwareKeyboard = InputDevice.getDeviceIds().toList().mapNotNull(InputDevice::getDevice)
                .any { !it.isVirtual && it.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC }
            val selectedModes = modes.filter {
                if (requestedModes == null) it.physical == hardwareKeyboard else it.name in requestedModes
            }
            require(selectedModes.isNotEmpty()) { "No valid correction test modes" }
            for (incremental in listOf(false, true)) for (omission in listOf(false, true)) for (mode in selectedModes) {
                floatingKeyboard = mode.floating
                check(prefs.edit().clear()
                    .putString("keyboard_order_preference", "[\"TENKEY\"]")
                    .putBoolean("save_last_used_keyboard", false)
                    .putBoolean("allow_fullscreen_mode_preference", false)
                    .putBoolean("keyboard_floating_preference", mode.floating)
                    .putBoolean("candidate_tab_visibility_preference", mode.conversionTab)
                    .putBoolean("conversion_bunsetsu_separation_preference", mode.bunsetsu)
                    .putBoolean("conversion_bunsetsu_cursor_move_preference", mode.cursor)
                    .putBoolean("incremental_conversion_session_preference", incremental)
                    .putBoolean("omission_search_preference", omission)
                    .putBoolean("enable_typo_correction_japanese_flick_keyboard_preference", !omission)
                    .putInt("n_best_preference", 8)
                    .putBoolean("henkan_delete_key_action_preference", false)
                    .putString("physical_keyboard_input_mode_preference", "romaji")
                    .putBoolean("live_conversion_preference", false)
                    .putBoolean("inline_suggestion_enabled_preference", false)
                    .putBoolean("clipboard_preview_enable_preference", false).commit())
                if (host == null) {
                    context.startActivity(Intent(context, CandidateHeightTestHostActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    await("Host did not start") { CandidateHeightTestHostActivity.current.get()?.also { host = it }?.hasWindowFocus() == true }
                } else onMain { host!!.restartEditor() }
                await("Keyboard not ready: $mode") {
                    (if (mode.physical) service() != null else key("key_2") != null) && host!!.editor.text.isEmpty()
                }
                SystemClock.sleep(400)
                val input = if (omission) "かつこう" else "せんせあ"
                val reading = if (omission) "がっこう" else "せんせい"
                val correctedWord = if (omission) "学校" else "先生"
                fun hasCorrection(): Boolean = if (mode.physical) {
                    floatingSuggestions().any { it.word == correctedWord && it.length.toInt() == input.length }
                } else suggestions().any { it.yomi == reading }
                if (mode.physical) {
                    physical(KeyEvent.KEYCODE_KANA)
                    for (letter in if (omission) "katukou" else "sensea") {
                        physical(KeyEvent.KEYCODE_A + (letter - 'a'))
                    }
                } else if (omission) {
                    flick("key_2"); flick("key_4", dy = -1); flick("key_2", dy = 1); flick("key_1", dy = -1)
                } else {
                    flick("key_3", dx = 1); flick("key_11", dy = -1); flick("key_3", dx = 1); flick("key_1")
                }
                await("Raw input did not arrive: $input") { host!!.editor.text.toString() == input }
                if (mode.conversionTab) onMain {
                    val tab = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull {
                        it.findViewById<com.google.android.material.tabs.TabLayout>(R.id.candidate_tab_layout)
                    }!!
                    (0 until tab.tabCount).mapNotNull(tab::getTabAt).first { it.text == "変換" }.select()
                }
                await("Initial correction absent: $incremental/$mode/$input") {
                    host!!.editor.text.toString() == input && hasCorrection()
                }
                fun convert() { if (mode.physical) physical(KeyEvent.KEYCODE_SPACE) else flick("key_space") }
                fun cancel() { if (mode.physical) physical(KeyEvent.KEYCODE_DEL) else flick("key_delete") }
                convert()
                await("Conversion did not activate: $mode") { converting() }
                await("Corrected alternatives disappeared: $incremental/$mode/$input") { hasCorrection() }
                cancel()
                await("Cancel did not restore input: $mode") { !converting() && host!!.editor.text.toString() == input }
                await("Cancelled input did not refresh") { hasCorrection() }
                convert()
                await("Reentry lost correction: $mode") { converting() && hasCorrection() }
                val targetWord = onMain {
                    if (mode.physical) correctedWord else suggestions().first { it.yomi == reading }.string
                }
                repeat(64) {
                    if (onMain { host!!.editor.text.toString() != targetWord }) convert()
                }
                await("Corrected candidate cannot be selected: $mode") { host!!.editor.text.toString() == targetWord }
                if (mode.physical) physical(KeyEvent.KEYCODE_ENTER) else flick("key_enter")
                await("Corrected candidate did not commit: $mode") { !converting() && host!!.editor.text.toString() == targetWord }
                println("CandidateCorrection incremental=$incremental omission=$omission mode=${mode.name} committed=$targetWord")
                // Let popup item animations finish before recreating input with new settings.
                SystemClock.sleep(500)
            }
        } finally {
            onMain { host?.finish() }
            AppPreference.importAllFromJson(backup)
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
            if (!enabled) shell("ime disable $target")
            info.flags = oldFlags
            ui.serviceInfo = info
        }
    }
}
