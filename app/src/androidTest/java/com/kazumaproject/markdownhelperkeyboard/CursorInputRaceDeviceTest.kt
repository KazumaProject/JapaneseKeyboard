package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.text.TextWatcher
import android.text.Editable
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.BaseInputConnection
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real UI regression. Only resets the isolated floatingqa app; restores the active IME. */
@RunWith(AndroidJUnit4::class)
class CursorInputRaceDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ui get() = ins.uiAutomation
    private val ctx get() = ins.targetContext
    private fun shell(s: String) = ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(s)).bufferedReader().use { it.readText().trim() }
    private fun find(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        fun visit(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (n.isVisibleToUser && predicate(n)) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { visit(it)?.let { v -> return v } }
            return null
        }
        for (w in ui.windows) w.root?.let { visit(it)?.let { v -> return v } }
        return null
    }
    private fun node(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 60000
        do { find(predicate)?.let { return it }; SystemClock.sleep(100) } while (SystemClock.uptimeMillis() < deadline)
        error("Missing test control")
    }
    private fun rect(n: AccessibilityNodeInfo) = Rect().also(n::getBoundsInScreen)
    private var down = 0L
    private fun touch(action: Int, x: Float, y: Float) {
        if (action == MotionEvent.ACTION_DOWN) down = SystemClock.uptimeMillis()
        val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        e.source = InputDevice.SOURCE_TOUCHSCREEN
        // Keep event timestamps representative of a quick gesture; waiting for each
        // event to finish dispatching can stretch a 100 ms swipe into seconds.
        check(ui.injectInputEvent(e, false)); e.recycle()
    }
    private fun tap(r: Rect, hold: Long = 40) {
        touch(MotionEvent.ACTION_DOWN, r.exactCenterX(), r.exactCenterY())
        SystemClock.sleep(hold)
        touch(MotionEvent.ACTION_UP, r.exactCenterX(), r.exactCenterY())
    }
    @Test fun cursorMovementImmediatelyFollowedByTypingPreservesTheCharacter() {
        check(ctx.packageName.startsWith("com.kazumaproject.floatingqa"))
        val args = InstrumentationRegistry.getArguments()
        val mode = args.getString("mode") ?: "panel"
        val variant = args.getString("variant") ?: "stack"
        val trials = (args.getString("trials") ?: "50").toInt()
        val swipeTime = (args.getString("swipeTime") ?: "100").toLong()
        require(mode in listOf("panel", "softwareHold", "softwareTap", "none", "externalMove", "tapCycle"))
        require(trials > 0 && swipeTime >= 0)
        val report = File(ctx.filesDir, "cause-$variant-$mode.txt")
        report.writeText("variant=$variant mode=$mode trials=$trials swipeTime=$swipeTime\n")
        ui.serviceInfo = ui.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        val oldIme = shell("settings get secure default_input_method")
        val target = "${ctx.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        prefs.edit().clear().putBoolean("floating_cursor_enabled", mode == "panel")
            .putBoolean("learn_dictionary_preference", false)
            .putString("keyboard_order_preference", "[\"TENKEY\"]")
            .putBoolean("keyboard_floating_preference", false)
            .putBoolean("save_last_used_keyboard", false)
            .putBoolean("flick_editor_preview_preference", args.getString("preview") == "true").commit()
        try {
            shell("input keyevent WAKEUP"); shell("wm dismiss-keyguard")
            ActivityScenario.launch<FastInputHostActivity>(Intent(ctx, FastInputHostActivity::class.java)
                .putExtra("stripStyles", args.getString("stripStyles") == "true")
                .putExtra("refuseExtraction", args.getString("refuseExtraction") == "true")).use { scenario ->
                var host: FastInputHostActivity? = null
                scenario.onActivity { host = it }
                fun onEditor(action: (FastInputHostActivity) -> Unit) = ins.runOnMainSync { action(requireNotNull(host)) }
                try {
                    shell("ime enable $target"); shell("ime set $target")
                    onEditor { it.restartEditorInput(true) }
                    node { it.viewIdResourceName == "${ctx.packageName}:id/key_1" }
                    if (mode == "panel") node { it.contentDescription == ctx.getString(R.string.floating_cursor_selection_off) }
                    var caseIndex = -1
                    onEditor { it.editText.addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            Log.i("CursorCause", "TEXT variant=$variant case=$caseIndex length=${s?.length} a=${s?.count { c -> c == 'あ' }} changed=$start,$before,$count")
                        }
                        override fun afterTextChanged(s: Editable?) {}
                    }) }
                    var lost = 0
                    var endedComposition = 0
                    for (i in 0 until trials) {
                        caseIndex = i
                        onEditor { it.editText.setText("x".repeat(100)); it.restartEditorInput(false) }
                        onEditor { it.editText.setSelection(50) }
                        SystemClock.sleep(200)
                        // restartInput changes candidate height asynchronously. Read the key
                        // after that layout settles, never reuse a center from the old layout.
                        var key = rect(node { it.viewIdResourceName == "${ctx.packageName}:id/key_1" && it.text?.toString() == "あ" })
                        val deadline = SystemClock.uptimeMillis() + 60000
                        var stable = 0
                        while (stable < 2) {
                            check(SystemClock.uptimeMillis() < deadline) { "Kana key layout did not settle" }
                            SystemClock.sleep(100)
                            val next = rect(node { it.viewIdResourceName == "${ctx.packageName}:id/key_1" && it.text?.toString() == "あ" })
                            stable = if (next == key) stable + 1 else 0
                            key = next
                        }
                        val pad = if (mode == "panel") rect(node { it.contentDescription == ctx.getString(R.string.floating_cursor_trackpad) }) else null
                        val left = if (mode == "softwareHold" || mode == "softwareTap") rect(node { it.viewIdResourceName == "${ctx.packageName}:id/key_soft_left" }) else null
                        Log.i("CursorCause", "BEGIN variant=$variant mode=$mode case=$i")
                        when (mode) {
                            "panel" -> {
                                val r = requireNotNull(pad)
                                touch(MotionEvent.ACTION_DOWN, r.exactCenterX(), r.exactCenterY())
                                if (swipeTime == 0L) touch(MotionEvent.ACTION_MOVE, 1f, r.exactCenterY())
                                else {
                                    SystemClock.sleep(swipeTime / 5)
                                    repeat(5) { step ->
                                        touch(MotionEvent.ACTION_MOVE, r.exactCenterX() + (1f-r.exactCenterX()) * (step+1)/5f, r.exactCenterY())
                                        SystemClock.sleep(swipeTime * 4 / 25)
                                    }
                                }
                                touch(MotionEvent.ACTION_UP, 1f, r.exactCenterY())
                            }
                            "softwareHold" -> tap(requireNotNull(left), 1200)
                            "softwareTap" -> tap(requireNotNull(left))
                        }
                        Log.i("CursorCause", "TAP_DOWN variant=$variant mode=$mode case=$i")
                        tap(key)
                        Log.i("CursorCause", "TAP_UP variant=$variant mode=$mode case=$i")
                        if (mode == "tapCycle") {
                            SystemClock.sleep(100)
                            tap(key)
                            SystemClock.sleep(300)
                            onEditor { assertEquals("Repeated tap did not cycle kana", "い", it.editText.text.toString().replace("x", "")) }
                            SystemClock.sleep(1200)
                            tap(key)
                        }
                        SystemClock.sleep(1000)
                        if (mode == "externalMove") {
                            // A real editor can finish composition and move its caret itself.
                            // The IME must accept this current notification, rather than ignore
                            // all no-composition updates to hide the cursor/input race.
                            onEditor {
                                check(requireNotNull(it.editText.onCreateInputConnection(android.view.inputmethod.EditorInfo())).finishComposingText())
                                assertEquals(-1, BaseInputConnection.getComposingSpanStart(it.editText.text))
                                it.editText.setSelection(0)
                            }
                            SystemClock.sleep((args.getString("externalPause") ?: "0").toLong())
                            tap(key)
                            SystemClock.sleep(1000)
                        }
                        onEditor {
                            val t = it.editText.text
                            val a = t.count { c -> c == 'あ' }
                            val expectedText = when (mode) {
                                "externalMove" -> "ああ"
                                "tapCycle" -> "いあ"
                                else -> "あ"
                            }
                            val incorrectCaret = mode == "externalMove" &&
                                (it.editText.selectionEnd != 1 ||
                                    BaseInputConnection.getComposingSpanStart(t) != 0 ||
                                    BaseInputConnection.getComposingSpanEnd(t) != 1)
                            if (t.toString().replace("x", "") != expectedText ||
                                t.length != 100 + expectedText.length ||
                                t.toString().replace("あ", "").replace("い", "") != "x".repeat(100) ||
                                incorrectCaret
                            ) lost++
                            if (BaseInputConnection.getComposingSpanStart(t) < 0) endedComposition++
                            val line = "case=$i a=$a length=${t.length} caret=${it.editText.selectionEnd} nonX=${t.toString().replace("x", "")} composing=${BaseInputConnection.getComposingSpanStart(t)},${BaseInputConnection.getComposingSpanEnd(t)}"
                            report.appendText(line+"\n"); Log.i("CursorCause", "RESULT variant=$variant mode=$mode $line")
                        }
                    }
                    report.appendText("lost=$lost/$trials\n")
                    Log.i("CursorCause", "SUMMARY variant=$variant mode=$mode lost=$lost/$trials")
                    report.appendText("endedComposition=$endedComposition/$trials\n")
                    assertEquals("Input lost after $mode; see ${report.name}", 0, lost)
                    assertEquals("Cursor action committed new input after $mode", 0, endedComposition)
                } finally {
                    onEditor { it.finish() }
                    shell("ime set $oldIme")
                }
            }
        } finally { shell("ime set $oldIme") }
    }
}
