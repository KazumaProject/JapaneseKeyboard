package com.kazumaproject.markdownhelperkeyboard.enter

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Records real IME key presses. A recording is not automatically an approved baseline. */
@RunWith(AndroidJUnit4::class)
class EnterProbeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val args = InstrumentationRegistry.getArguments()
    private val context = instrumentation.targetContext
    private val ui = instrumentation.uiAutomation
    private var imePackage = ""

    @Test fun recordEnterCases() {
        assumeTrue("Run explicitly with tools/enter/run_device.py", args.containsKey("probeMode"))
        val mode = requireNotNull(args.getString("probeMode"))
        require(mode in listOf("record", "compare", "inventory"))
        File(context.getExternalFilesDir(null), "enter-cases.json").writeText(
            JSONArray(EnterProbeCase.all().map { case -> JSONObject()
                .put("caseId", case.id).put("state", case.state).put("operation", case.operation)
                .put("inputType", case.inputType).put("imeOptions", case.imeOptions)
            }).toString(2)
        )
        if (mode == "inventory") return
        val originalIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val requestedIme = args.getString("probeIme") ?: if (mode == "compare") {
            "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        } else originalIme
        imePackage = requestedIme.substringBefore('/')
        require(mode != "compare" || imePackage == context.packageName) {
            "Compare mode must exercise this app's IME, not Gboard against itself"
        }
        require(requestedIme.matches(Regex("[A-Za-z0-9_.$/]+")))
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val originalPreferences = preferences.all
        val changedKeys = mutableSetOf<String>()
        val oldFlags = ui.serviceInfo.flags
        ui.serviceInfo = ui.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        try {
            cancelTouch()
            if (imePackage == context.packageName) {
                val edit = preferences.edit()
                args.getString("probeSurface")?.let { surface ->
                    require(surface in listOf("normal", "floating"))
                    changedKeys.add("keyboard_floating_preference")
                    edit.putBoolean("keyboard_floating_preference", surface == "floating")
                }
                args.getString("probeKeyboard")?.let { keyboard ->
                    val keyboards = listOf("TENKEY", "SUMIRE", "QWERTY", "ROMAJI", "GOJUON", "CUSTOM")
                    require(keyboard in keyboards && keyboard != "CUSTOM")
                    changedKeys.addAll(listOf("keyboard_order_preference", "save_last_used_keyboard", "save_last_used_keyboard_int"))
                    edit.putString("keyboard_order_preference", JSONArray(listOf(keyboard) + keyboards.filter { it != keyboard }).toString())
                    edit.putBoolean("save_last_used_keyboard", false).putInt("save_last_used_keyboard_int", 0)
                }
                if (args.getString("probeBunsetsu") == "true") {
                    for (key in listOf("conversion_bunsetsu_separation_preference", "conversion_bunsetsu_cursor_move_preference")) {
                        changedKeys.add(key)
                        edit.putBoolean(key, true)
                    }
                }
                check(edit.commit())
            }
            println("ENTER_PROBE originalIme=$originalIme requestedIme=$requestedIme")
            if (requestedIme != originalIme) shell("ime set $requestedIme")
            await { Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) == requestedIme }
            val filter = Regex(args.getString("probeCases") ?: "action-(1|3|6)-flags-(0|5)-committed")
            val cases = EnterProbeCase.all().filter { filter.matches(it.id) }
            assertTrue("No cases matched $filter", cases.isNotEmpty())
            cases.forEach { record(it) }
        } finally {
            cancelTouch()
            val restore = preferences.edit()
            changedKeys.forEach { key ->
                when (val value = originalPreferences[key]) {
                    null -> restore.remove(key)
                    is String -> restore.putString(key, value)
                    is Boolean -> restore.putBoolean(key, value)
                    is Int -> restore.putInt(key, value)
                }
            }
            check(restore.commit())
            if (requestedIme != originalIme) shell("ime set $originalIme")
            ui.serviceInfo = ui.serviceInfo.apply { flags = oldFlags }
        }
    }

    private fun record(case: EnterProbeCase) {
        val intent = Intent(context, EnterProbeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("caseId", case.id).putExtra("inputType", case.inputType)
            .putExtra("imeOptions", case.imeOptions).putExtra("actionId", case.actionId)
            .putExtra("actionLabel", case.actionLabel).putExtra("text", case.text)
            .putExtra("selectionStart", case.selectionStart).putExtra("selectionEnd", case.selectionEnd)
        ActivityScenario.launch<EnterProbeActivity>(intent).use { scenario ->
            var before = JSONObject()
            val results = mutableListOf<JSONObject>()
            val setupCheckpoints = mutableListOf<String>()
            try {
                await { imeRoots().isNotEmpty() }
                await {
                    var connected = false
                    scenario.onActivity { connected = it.actualEditorInfo.length() > 0 }
                    connected
                }
                settle(scenario)
                // Setup always uses actual IME taps; setting composing spans in the editor is not IME state.
                if (case.state != "committed") {
                    val setup = requireNotNull(args.getString("probeSetup")) {
                        "${case.id}: provide probeSetup (semicolon-separated IME node selectors)"
                    }
                    setup.split(';').forEach { operation ->
                        if (operation.startsWith("expect:")) {
                            val expected = operation.substringAfter(':')
                            assertEquals("${case.id}: setup checkpoint", expected, settle(scenario).getString("text"))
                            setupCheckpoints.add(expected)
                        } else if (operation == "left" || operation == "right") {
                            val time = SystemClock.uptimeMillis()
                            for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                                assertTrue(ui.injectInputEvent(KeyEvent(time, SystemClock.uptimeMillis(), action,
                                    if (operation == "left") KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT, 0, 0, -1, 0, 0, InputDevice.SOURCE_KEYBOARD), true))
                            }
                        } else if (operation.startsWith("selection:")) {
                            val position = operation.substringAfter(':').toInt()
                            scenario.onActivity { it.editor.setSelection(position) }
                        } else tapKey(operation)
                        settle(scenario)
                    }
                }
                before = settle(scenario)
                val keyboardBefore = keyboardNodes()
                if (imePackage == context.packageName) {
                    when (args.getString("probeSurface")) {
                        "floating" -> assertNotNull("Floating Enter path not visible", keyBounds("floating_keyboard_content"))
                        "normal" -> assertNull("Unexpected floating keyboard", keyBounds("floating_keyboard_content"))
                    }
                }
                if (case.state == "committed") {
                    assertEquals("${case.id}: initial text", case.text, before.getString("text"))
                    assertEquals("${case.id}: initial selection start", case.selectionStart, before.getInt("selectionStart"))
                    assertEquals("${case.id}: initial selection end", case.selectionEnd, before.getInt("selectionEnd"))
                    assertEquals("${case.id}: unexpected composition", -1, before.getInt("composingStart"))
                }
                if (case.state != "committed") {
                    assertTrue("${case.id}: setup did not create composing text", before.getInt("composingStart") >= 0)
                    val expected = requireNotNull(args.getString("probeBeforeText")) {
                        "Provide probeBeforeText to verify preparation before Enter"
                    }
                    assertEquals(case.id, expected, before.getString("text"))
                    if (case.state == "composing-middle") {
                        // Gboard keeps the editor selection at the composing end even when its
                        // internal cursor is inside the reading. Verify an insertion/deletion
                        // round trip at that position instead of assuming Android cursor offsets.
                        assertEquals("${case.id}: missing internal-cursor proof", case.setupCheckpoints, setupCheckpoints)
                    }
                    if (case.state == "converting" || case.state == "segment") {
                        val marker = requireNotNull(args.getString("probeStateMarker")) {
                            "Provide a visible conversion-state node selector"
                        }
                        await { keyBounds(marker) != null }
                    }
                }
                scenario.onActivity { it.clearEvents() }
                when (case.operation) {
                    "tap-twice", "tap-thrice" -> repeat(if (case.operation == "tap-thrice") 3 else 2) { tapEnter(); results.add(settle(scenario)) }
                    "rapid" -> { repeat(3) { tapEnter() }; results.add(settle(scenario)) }
                    "long" -> { tapEnter(800); results.add(settle(scenario)) }
                    else -> {
                        val meta = when (case.operation) {
                            "shift" -> KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
                            "ctrl" -> KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
                            "alt" -> KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
                            else -> 0
                        }
                        val code = if (case.operation == "numpad") KeyEvent.KEYCODE_NUMPAD_ENTER else KeyEvent.KEYCODE_ENTER
                        val time = SystemClock.uptimeMillis()
                        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                            assertTrue(ui.injectInputEvent(KeyEvent(time, SystemClock.uptimeMillis(), action,
                                code, 0, meta, -1, 0, 0, InputDevice.SOURCE_KEYBOARD), true))
                        }
                        results.add(settle(scenario))
                    }
                }
                scenario.onActivity { activity ->
                    val file = activity.saveObservation(before, results, case.operation)
                    val data = JSONObject(file.readText())
                        .put("surface", args.getString("probeSurface") ?: "current-unverified")
                        .put("keyboard", args.getString("probeKeyboard") ?: "current-unverified")
                        .put("enterSelector", args.getString("probeEnter") ?: "auto")
                        .put("setup", args.getString("probeSetup") ?: "")
                        .put("setupCheckpoints", JSONArray(setupCheckpoints))
                        .put("stateMarker", args.getString("probeStateMarker") ?: "")
                        .put("bunsetsu", args.getString("probeBunsetsu") == "true")
                        .put("keyboardBefore", keyboardBefore)
                    file.writeText(data.toString(2))
                    if (args.getString("probeMode") == "compare") {
                        val directory = File(activity.getExternalFilesDir(null), "enter-baselines")
                        val reference = File(directory, "${case.id}.json")
                        assertTrue("Missing reviewed Gboard baseline: ${case.id}", reference.isFile)
                        EnterObservationComparison.assertMatches(JSONObject(reference.readText()), data)
                        file.writeText(data.put("comparison", "matched").toString(2))
                    }
                }
            } catch (failure: Throwable) {
                val keyboardAtFailure = keyboardNodes()
                scenario.onActivity {
                    val file = it.saveObservation(before, results + it.snapshot(), "FAILED:${case.operation}")
                    val data = JSONObject(file.readText()).put("failure", failure.toString()).put("keyboardAtFailure", keyboardAtFailure)
                    file.writeText(data.toString(2))
                }
                throw failure
            }
        }
    }

    private fun imeRoots(): List<AccessibilityNodeInfo> = ui.windows.mapNotNull { it.root }.filter {
        it.packageName?.toString() == imePackage &&
            it.findAccessibilityNodeInfosByViewId("android:id/input").isEmpty()
    }

    private fun keyboardNodes(): JSONArray {
        val result = JSONArray()
        fun visit(node: AccessibilityNodeInfo) {
            if (node.isVisibleToUser && (node.text != null || node.contentDescription != null || node.viewIdResourceName != null)) {
                val rect = Rect().also { node.getBoundsInScreen(it) }
                result.put(JSONObject().put("text", node.text ?: "")
                    .put("description", node.contentDescription ?: "")
                    .put("id", node.viewIdResourceName ?: "").put("bounds", rect.toShortString()))
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        imeRoots().forEach(::visit)
        return result
    }

    private fun tapEnter(holdMs: Long = 0) {
        val explicit = args.getString("probeEnter")
        val selectors = if (explicit != null && explicit != "builtin-number-enter") listOf(explicit) else listOf(
            "key_pos_ime_action", "key_enter", "key_return", "enter_key", "改行", "確定", "検索", "送信", "次へ", "完了",
            "実行", "前へ", "Previous", "Enter", "Return", "Search", "Send", "Next", "Done", "Go", "Probe action",
        )
        var bounds: Rect? = null
        await {
            bounds = selectors.firstNotNullOfOrNull { keyBounds(it) }
                ?: if (explicit == "builtin-number-enter") builtinNumberEnterBounds() else null
            bounds != null
        }
        tap(requireNotNull(bounds), holdMs)
    }

    /** Opt-in profile for KeyboardDefaultLayouts.createNumberLayout's 4x4 grid.
     * Its icon-only Enter has no accessibility node. Verify digit anchors before tapping
     * row 4 / column 4, using live geometry rather than device-specific coordinates.
     */
    private fun builtinNumberEnterBounds(): Rect? {
        if (imePackage != context.packageName) return null
        val root = keyBounds("custom_layout_default") ?: keyBounds("custom_layout_floating") ?: return null
        val two = keyBounds("2") ?: return null
        val three = keyBounds("3") ?: return null
        val zero = keyBounds("0") ?: return null
        if ((1..9).any { keyBounds(it.toString()) == null }) return null
        if (kotlin.math.abs(two.centerY() - three.centerY()) > 3 ||
            kotlin.math.abs(two.centerX() - zero.centerX()) > 3 || zero.centerY() <= three.centerY()) return null
        val x = three.centerX() + (three.centerX() - two.centerX())
        val y = zero.centerY()
        if (!root.contains(x, y)) return null
        return Rect(x - 1, y - 1, x + 1, y + 1)
    }

    private fun tapKey(selector: String) {
        var bounds: Rect? = null
        await { bounds = keyBounds(selector); bounds != null }
        tap(requireNotNull(bounds), 0)
    }

    private fun keyBounds(selector: String): Rect? {
        fun find(node: AccessibilityNodeInfo): Rect? {
            val matches = node.viewIdResourceName?.substringAfterLast('/') == selector ||
                node.contentDescription?.toString() == selector || node.text?.toString() == selector
            if (matches && node.isVisibleToUser) return Rect().also { node.getBoundsInScreen(it) }.takeUnless { it.isEmpty }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                find(child)?.let { return it }
            }
            return null
        }
        return imeRoots().firstNotNullOfOrNull(::find)
    }

    private fun cancelTouch() {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            .apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        try { ui.injectInputEvent(event, true) } finally { event.recycle() }
    }

    private fun tap(bounds: Rect, holdMs: Long) {
        val down = SystemClock.uptimeMillis()
        fun inject(action: Int): Boolean {
            val properties = arrayOf(MotionEvent.PointerProperties().apply {
                id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER
            })
            val coordinates = arrayOf(MotionEvent.PointerCoords().apply {
                x = bounds.exactCenterX(); y = bounds.exactCenterY(); pressure = 1f; size = 1f
            })
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1,
                properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            return try { ui.injectInputEvent(event, true) } finally { event.recycle() }
        }
        var released = false
        try {
            assertTrue("Enter touch DOWN rejected", inject(MotionEvent.ACTION_DOWN))
            SystemClock.sleep(holdMs.coerceAtLeast(18))
            released = inject(MotionEvent.ACTION_UP)
            assertTrue("Enter touch UP rejected", released)
        } finally {
            if (!released) cancelTouch()
        }
    }

    private fun settle(scenario: ActivityScenario<EnterProbeActivity>): JSONObject {
        var last = ""
        var stableSince = SystemClock.uptimeMillis()
        var snapshot = JSONObject()
        await {
            scenario.onActivity { snapshot = it.snapshot() }
            val current = snapshot.toString()
            if (last != current) { last = current; stableSince = SystemClock.uptimeMillis() }
            SystemClock.uptimeMillis() - stableSince >= 350
        }
        return snapshot
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < deadline) { "Enter probe timed out; check IME UI / selectors / setup" }
            SystemClock.sleep(50)
        }
    }

    private fun shell(command: String) {
        ui.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    }
}
