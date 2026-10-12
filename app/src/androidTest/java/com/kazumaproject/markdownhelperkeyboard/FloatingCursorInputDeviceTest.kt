package com.kazumaproject.markdownhelperkeyboard

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inspector.WindowInspector
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.core.domain.flick.FlickTextPreviewEvent
import com.kazumaproject.core.domain.flick.FlickTextPreviewListener
import com.kazumaproject.core.domain.flick.FlickTextSelection
import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.listener.QWERTYKeyListener
import com.kazumaproject.core.domain.qwerty.QWERTYKey
import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Isolated API 35 editor/IME integration; build with investigation/floating-panel.init.gradle.
 * Dispatches real panel touch-down and the production input/release callbacks deterministically.
 */
@RunWith(AndroidJUnit4::class)
class FloatingCursorInputDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context get() = ins.targetContext
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        ins.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }

    private fun field(target: Any, name: String): Any? {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            try { return type.getDeclaredField(name).apply { isAccessible = true }.get(target) }
            catch (_: NoSuchFieldException) { type = type.superclass }
        }
        error("Missing field $name")
    }
    private fun call(target: Any, name: String, vararg arguments: Any?) = target.javaClass.declaredMethods
        .single { it.name == name }.apply { isAccessible = true }.invoke(target, *arguments)
    @Suppress("UNCHECKED_CAST")
    private fun input(ime: IMEService) = field(ime, "_inputString") as MutableStateFlow<String>
    @Suppress("UNCHECKED_CAST")
    private fun mode(ime: IMEService, mode: TenKeyQWERTYMode) {
        (field(ime, "_tenKeyQWERTYMode") as MutableStateFlow<TenKeyQWERTYMode>).value = mode
    }
    private fun binding(ime: IMEService) = requireNotNull(field(ime, "mainLayoutBinding"))
    private fun customListener(ime: IMEService) = requireNotNull(
        field(requireNotNull(field(binding(ime), "customLayoutDefault")), "listener"))
    private fun qwertyListener(ime: IMEService) = field(
        requireNotNull(field(binding(ime), "qwertyView")), "qwertyKeyListener") as QWERTYKeyListener

    private fun runIme(keyboard: String, preview: Boolean = false, test: (IMEService) -> Unit) {
        check(context.packageName.startsWith("com.kazumaproject.floatingqa"))
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear().putString("keyboard_order_preference", "[\"$keyboard\"]")
            .putBoolean("floating_cursor_enabled", true)
            .putBoolean("learn_dictionary_preference", false)
            .putBoolean("save_last_used_keyboard", false)
            .putBoolean("flick_editor_preview_preference", preview).commit()
        val target = "${context.packageName}/${IMEService::class.java.name}"
        val oldIme = shell("settings get secure default_input_method")
        val wasEnabled = shell("settings get secure enabled_input_methods").contains(target)
        // Launch before switching IMEs; awaiting activity-idle with an active IME can hang.
        shell("ime set com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME")
        val host = ins.startActivitySync(Intent(context, FastInputHostActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as FastInputHostActivity
        try {
            shell("ime enable $target"); shell("ime set $target")
            ins.runOnMainSync { host.restartEditorInput(true) }
            var ime: IMEService? = null
            val deadline = SystemClock.uptimeMillis() + 60000
            while (ime == null) {
                ins.runOnMainSync {
                    fun service(context: Context): IMEService? = when (context) {
                        is IMEService -> context
                        is ContextWrapper -> if (context.baseContext !== context) service(context.baseContext) else null
                        else -> null
                    }
                    ime = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull { service(it.context) }
                        ?.takeIf { field(it, "mainLayoutBinding") != null &&
                            field(it, "floatingCursor")?.let { cursor -> field(cursor, "view") } != null }
                }
                check(SystemClock.uptimeMillis() < deadline) { "IME/panel did not initialize" }
                SystemClock.sleep(100)
            }
            SystemClock.sleep(500)
            test(requireNotNull(ime))
        } finally {
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
            if (!wasEnabled) shell("ime disable $target")
            ins.runOnMainSync { host.finish() }
        }
    }

    private fun heldArrowThenInput(ime: IMEService, write: () -> Unit, expected: String? = null) {
        var panel: Any? = null
        ins.runOnMainSync {
            assertFalse(field(ime, "cursorSelectionMode") as Boolean)
            panel = field(requireNotNull(field(ime, "floatingCursor")), "view")
            val arrows = field(requireNotNull(panel), "arrows") as Map<*, *>
            val left = arrows.entries.single { it.key.toString() == "LEFT" }.value as View
            val time = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN,
                left.width / 2f, left.height / 2f, 0)
            left.dispatchTouchEvent(down); down.recycle()
            assertNotNull(field(requireNotNull(panel), "repeatAction"))
            write()
        }
        SystemClock.sleep(750)
        ins.runOnMainSync {
            assertNull("Typing must stop the held panel arrow", field(requireNotNull(panel), "repeatAction"))
            if (expected != null) assertEquals("The old repeat must not move newly typed preedit", expected, input(ime).value)
        }
    }

    @Test fun sumireTextStopsPanel() = runIme("SUMIRE") { ime ->
        heldArrowThenInput(ime, { call(customListener(ime), "onAction", KeyAction.Text("あ"), false) }, "あ")
    }
    @Test fun numberTextStopsPanel() = runIme("SUMIRE") { ime ->
        ins.runOnMainSync { mode(ime, TenKeyQWERTYMode.Number) }
        heldArrowThenInput(ime, { call(customListener(ime), "onAction", KeyAction.Text("1"), false) }, "1")
    }
    @Test fun customTextStopsPanel() = runIme("SUMIRE") { ime ->
        ins.runOnMainSync { mode(ime, TenKeyQWERTYMode.Custom) }
        heldArrowThenInput(ime, { call(customListener(ime), "onAction", KeyAction.Text("あ"), false) }, "あ")
    }
    @Test fun customToggleStopsPanel() = runIme("SUMIRE") { ime ->
        ins.runOnMainSync { mode(ime, TenKeyQWERTYMode.Custom) }
        heldArrowThenInput(ime, { call(customListener(ime), "onToggleText", "kana", listOf("あ", "い")) }, "あ")
    }
    @Test fun tenkeyTextStopsPanel() = runIme("TENKEY") { ime ->
        heldArrowThenInput(ime, { call(ime, "handleTapAndFlick", Key.KeyA, 'あ', input(ime).value,
            StringBuilder(), false, GestureType.Tap, emptyList<Candidate>(), binding(ime)) }, "あ")
    }
    @Test fun qwertyTapStopsPanel() = runIme("QWERTY") { ime ->
        heldArrowThenInput(ime, { qwertyListener(ime).onReleasedQWERTYKey(QWERTYKey.QWERTYKeyA, 'a', null) })
    }
    @Test fun qwertyUpFlickStopsPanel() = runIme("QWERTY") { ime ->
        heldArrowThenInput(ime, { qwertyListener(ime).onFlickUPQWERTYKey(QWERTYKey.QWERTYKeyA, 'a', listOf('a')) })
    }
    @Test fun qwertyDownFlickStopsPanel() = runIme("QWERTY") { ime ->
        heldArrowThenInput(ime, { qwertyListener(ime).onFlickDownQWERTYKey(QWERTYKey.QWERTYKeyA, 'a') })
    }
    @Test fun physicalTypingStopsPanel() = runIme("TENKEY") { ime ->
        heldArrowThenInput(ime, { ime.onKeyDown(KeyEvent.KEYCODE_A, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)) })
    }
    @Test fun glideTextStopsPanel() = runIme("QWERTY") { ime ->
        heldArrowThenInput(ime, { call(ime, "showQwertyGlideCandidates",
            listOf(Candidate("hello", 1, 5u, 0)), true) }, "hello")
    }
    @Test fun voiceTextStopsPanel() = runIme("TENKEY") { ime ->
        heldArrowThenInput(ime, { call(ime, "applyVoiceInputText", "音声") }, "音声")
    }
    @Test fun sumirePreviewStopsPanelBeforeCommit() = runIme("SUMIRE", preview = true) { ime ->
        heldArrowThenInput(ime, { (field(ime, "sumireFlickTextPreviewListener") as FlickTextPreviewListener)
            .onFlickTextPreview(FlickTextPreviewEvent.Started(1, FlickTextSelection("あ", false))) })
    }

    @Test fun deliberatePreeditMovementKeepsRepeating() = runIme("TENKEY") { ime ->
        val text = "あいうえおかきくけこ"
        ins.runOnMainSync { input(ime).value = text }
        SystemClock.sleep(300)
        var panel: Any? = null
        ins.runOnMainSync {
            panel = field(requireNotNull(field(ime, "floatingCursor")), "view")
            val arrows = field(requireNotNull(panel), "arrows") as Map<*, *>
            val left = arrows.entries.single { it.key.toString() == "LEFT" }.value as View
            val time = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 1f, 1f, 0)
            left.dispatchTouchEvent(down); down.recycle()
        }
        SystemClock.sleep(650)
        ins.runOnMainSync {
            assertNotNull("Rendering cursor movement must not cancel its own repeat", field(requireNotNull(panel), "repeatAction"))
            assertTrue(input(ime).value.length < text.length - 1)
            @Suppress("UNCHECKED_CAST")
            val tail = field(ime, "stringInTail") as AtomicReference<String>
            assertEquals(text, input(ime).value + tail.get())
            call(requireNotNull(panel), "cancelInput")
        }
    }

    private fun holdRelease(ime: IMEService, release: (Boolean) -> Unit) {
        val text = "あいうえおかきくけこ"
        for (left in listOf(true, false)) {
            val flag = if (left) "leftCursorKeyLongKeyPressed" else "rightCursorKeyLongKeyPressed"
            ins.runOnMainSync {
                @Suppress("UNCHECKED_CAST")
                val tail = field(ime, "stringInTail") as AtomicReference<String>
                tail.set(if (left) "" else "さしす")
                input(ime).value = text
                ime.setComposingText(text + tail.get(), 1)
            }
            SystemClock.sleep(300)
            ins.runOnMainSync {
                // Freeze at finger-up; production release must consume the hold marker.
                (field(ime, flag) as AtomicBoolean).set(true)
                release(left)
            }
            SystemClock.sleep(300)
            ins.runOnMainSync {
                assertEquals("Hold release must not dispatch a fresh tap: $flag", text, input(ime).value)
                assertFalse((field(ime, flag) as AtomicBoolean).get())
            }
        }
    }

    @Test fun leftAndRightHoldReleaseDoNotAddAPreeditStep() = runIme("TENKEY") { ime ->
        holdRelease(ime) { left ->
            call(ime, "handleTapAndFlick", if (left) Key.SideKeyCursorLeft else Key.SideKeyCursorRight,
                null, input(ime).value, StringBuilder(), false, GestureType.Tap, emptyList<Candidate>(), binding(ime))
        }
    }
    @Test fun qwertyHoldReleaseDoesNotAddAPreeditStep() = runIme("QWERTY") { ime ->
        holdRelease(ime) { left -> qwertyListener(ime).onReleasedQWERTYKey(
            if (left) QWERTYKey.QWERTYKeyCursorLeft else QWERTYKey.QWERTYKeyCursorRight, null, null) }
    }
    @Test fun customHoldReleaseDoesNotAddAPreeditStep() = runIme("SUMIRE") { ime ->
        holdRelease(ime) { left -> call(customListener(ime), "onAction",
            if (left) KeyAction.MoveCursorLeft else KeyAction.MoveCursorRight, false) }
    }
}
