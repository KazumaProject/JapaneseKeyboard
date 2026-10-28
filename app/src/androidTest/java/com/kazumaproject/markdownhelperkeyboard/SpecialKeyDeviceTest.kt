package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyActionOverrideEntity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real IME and editor test. Run with investigation/custom-toggle.init.gradle. */
@RunWith(AndroidJUnit4::class)
class SpecialKeyDeviceTest {
    private val ins get() = InstrumentationRegistry.getInstrumentation()
    private val automation get() = ins.uiAutomation
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val all = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            all.add(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        automation.windows.filter { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            .forEach { it.root?.let(::visit) }
        return all
    }

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(80)
        }
        fail("Timed out: $description")
    }

    private fun key(label: String): Rect {
        var found: Rect? = null
        await("key $label") {
            val match = nodes().firstOrNull {
                it.packageName?.toString() == ins.targetContext.packageName && it.isVisibleToUser && it.isClickable &&
                    (it.text?.toString() == label || it.contentDescription?.toString() == label)
            }
            match?.let { found = Rect().also(it::getBoundsInScreen) }
            found != null
        }
        return requireNotNull(found)
    }

    private fun tap(label: String) {
        var rect: Rect? = null
        var unchanged = 0
        await("stable key $label") {
            val current = key(label)
            unchanged = if (current == rect) unchanged + 1 else 0
            rect = current
            unchanged >= 3
        }
        val target = requireNotNull(rect)
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                target.exactCenterX(), target.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            check(automation.injectInputEvent(event, true))
            event.recycle()
            SystemClock.sleep(35)
        }
    }

    private fun editorState(scenario: ActivityScenario<FastInputHostActivity>): Triple<String, Int, Int> {
        var result = Triple("", -1, -1)
        scenario.onActivity { result = Triple(it.editText.text.toString(), it.editText.selectionStart, it.editText.selectionEnd) }
        return result
    }

    private fun layout(direct: Boolean): KeyboardLayout {
        val actions = listOf(
            "Ctrl" to KeyAction.CtrlLock, "Alt" to KeyAction.AltLock,
            "Next" to KeyAction.NextField, "Previous" to KeyAction.PreviousField,
            "a" to KeyAction.Text("a"), "b" to KeyAction.Text("b"),
            "SelectLeft" to KeyAction.SelectLeft, "SelectRight" to KeyAction.SelectRight,
            "Delete" to KeyAction.Delete, "Undo" to KeyAction.UndoLastDelete,
            "Home" to KeyAction.Home, "Tab" to KeyAction.Tab
        )
        val keys = actions.mapIndexed { index, (label, action) ->
            KeyData(label, index / 4, index % 4, isFlickable = false, action = action,
                isSpecialKey = action !is KeyAction.Text, keyId = "test_$index", keyType = KeyType.NORMAL,
                drawableResId = KeyActionMapper.iconResIdForAction(action))
        }
        return KeyboardLayout(keys, emptyMap(), columnCount = 4, rowCount = 3, isDirectMode = direct)
    }

    @Test fun actualIme_navigationModifiersSelectionAndDeletionUndo() = runBlocking {
        val context = ins.targetContext
        check(context.packageName.startsWith("com.kazumaproject.customtoggletest"))
        val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val oldIme = shell("settings get secure default_input_method").trim()
        val wasEnabled = shell("ime list -s").lineSequence().any { it.trim() == ime }
        val db = EntryPointAccessors.fromApplication(
            context.applicationContext, BunsetsuTestDatabaseEntryPoint::class.java
        ).database()
        val repository = KeyboardRepository(db.keyboardLayoutDao())
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val lockedCtrl = context.getString(com.kazumaproject.custom_keyboard.R.string.modifier_locked, "Ctrl")
        val lockedAlt = context.getString(com.kazumaproject.custom_keyboard.R.string.modifier_locked, "Alt")
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        try {
            val id = repository.saveLayout(layout(true), "Special key test", repository.getLayoutsNotFlow().firstOrNull()?.layoutId)
            check(prefs.edit().putString("keyboard_order_preference", "[\"CUSTOM\"]")
                .putBoolean("save_last_used_keyboard", false).putBoolean("keyboard_floating_preference", false)
                .putBoolean("undo_enable_preference", false).putBoolean("delete_key_flick_down_preference", false)
                .putBoolean("live_conversion_preference", false).commit())
            shell("ime enable $ime")
            shell("ime set $ime")
            ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { scenario ->
                key("Ctrl")
                scenario.onActivity { it.editText.setSingleLine(); it.editText.setText("abc"); it.editText.setSelection(3) }
                tap("Next") // No navigation was advertised; no Tab or Enter may be sent.
                SystemClock.sleep(300)
                assertEquals(Triple("abc", 3, 3), editorState(scenario))

                tap("Ctrl")
                key(lockedCtrl)
                tap("a")
                await("Ctrl+A selects text") { editorState(scenario).let { it.second == 0 && it.third == 3 } }
                tap(lockedCtrl)
                key("Ctrl")
                tap("Alt")
                key(lockedAlt)
                tap(lockedAlt)
                key("Alt")

                scenario.onActivity { it.editText.setText("A😀B"); it.editText.setSelection(it.editText.length()) }
                tap("SelectLeft")
                await("first selection step") { editorState(scenario).let { minOf(it.second, it.third) == 3 && maxOf(it.second, it.third) == 4 } }
                tap("SelectLeft")
                await("selection spans emoji and B") { editorState(scenario).let { minOf(it.second, it.third) == 1 && maxOf(it.second, it.third) == 4 } }
                tap("SelectRight")
                await("selection contracts from the left") { editorState(scenario).let { minOf(it.second, it.third) == 3 && maxOf(it.second, it.third) == 4 } }
                tap("SelectLeft")
                await("selection again spans emoji and B") { editorState(scenario).let { minOf(it.second, it.third) == 1 && maxOf(it.second, it.third) == 4 } }
                tap("Delete")
                await("selection deleted") { editorState(scenario).first == "A" }
                tap("Undo")
                await("selection deletion restored") { editorState(scenario).first == "A😀B" }

                lateinit var second: EditText
                val editorActions = mutableListOf<Int>()
                val editorKeys = mutableListOf<Int>()
                scenario.onActivity { activity ->
                    second = object : EditText(activity) {
                        override fun onEditorAction(actionCode: Int) {
                            editorActions.add(actionCode)
                            super.onEditorAction(actionCode)
                        }
                        override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
                            editorKeys.add(keyCode)
                            return super.onKeyDown(keyCode, event)
                        }
                    }.apply {
                        hint = "Second field"
                        setSingleLine()
                        imeOptions = EditorInfo.IME_ACTION_DONE
                        setText("second")
                    }
                    activity.editText.imeOptions = EditorInfo.IME_ACTION_NEXT
                    activity.editText.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, (160 * activity.resources.displayMetrics.density).toInt())
                    (activity.editText.parent as LinearLayout).addView(second)
                    activity.editText.requestFocus()
                }
                await("the second editor is laid out below the first") {
                    var ready = false
                    scenario.onActivity { ready = second.isLaidOut && it.editText.focusSearch(android.view.View.FOCUS_DOWN) === second }
                    ready
                }
                scenario.onActivity { it.getSystemService(InputMethodManager::class.java).restartInput(it.editText) }
                SystemClock.sleep(500)
                tap("Ctrl")
                key(lockedCtrl)
                tap("Next")
                await("next field focus") { var focused = false; scenario.onActivity { focused = second.hasFocus() }; focused }
                key("Ctrl") // Field transition reset the modifier.
                tap("Previous")
                await("previous field focus") { var focused = false; scenario.onActivity { focused = it.editText.hasFocus() }; focused }
                assertTrue(editorActions.contains(EditorInfo.IME_ACTION_PREVIOUS))
                assertFalse(editorKeys.contains(KeyEvent.KEYCODE_TAB))
                assertFalse(editorKeys.contains(KeyEvent.KEYCODE_ENTER))

                // A standard EditText without explicit IME options advertises PREVIOUS on
                // Android 35, but TextView's default onEditorAction converts it to Enter.
                val defaultActions = mutableListOf<Int>()
                val defaultKeys = mutableListOf<Int>()
                lateinit var defaultEditor: EditText
                scenario.onActivity { activity ->
                    val container = activity.editText.parent as LinearLayout
                    container.removeView(second)
                    defaultEditor = object : EditText(activity) {
                        override fun onEditorAction(actionCode: Int) {
                            defaultActions.add(actionCode)
                            super.onEditorAction(actionCode)
                        }
                        override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
                            defaultKeys.add(keyCode)
                            return super.onKeyDown(keyCode, event)
                        }
                    }.apply { setSingleLine(); setText("default") }
                    container.addView(defaultEditor)
                }
                await("default editor is laid out") { var ready = false; scenario.onActivity { ready = defaultEditor.isLaidOut }; ready }
                scenario.onActivity { activity ->
                    defaultEditor.requestFocus()
                    activity.getSystemService(InputMethodManager::class.java).restartInput(defaultEditor)
                }
                await("default editor focused") { var focused = false; scenario.onActivity { focused = defaultEditor.hasFocus() }; focused }
                SystemClock.sleep(500)
                tap("Previous")
                await("default editor receives the previous action and handles Enter") {
                    defaultActions.contains(EditorInfo.IME_ACTION_PREVIOUS) && defaultKeys.contains(KeyEvent.KEYCODE_ENTER)
                }
                assertFalse(defaultKeys.contains(KeyEvent.KEYCODE_TAB))
            }

            repository.saveLayout(layout(false), "Special key test", id)
            ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { scenario ->
                key("a")
                tap("a")
                await("preedit") { editorState(scenario).first == "a" }
                val before = editorState(scenario)
                tap("Home")
                tap("Ctrl")
                key(lockedCtrl)
                tap("a")
                SystemClock.sleep(300)
                assertEquals(before, editorState(scenario))
            }

            val overrides = db.sumireSpecialKeyActionOverrideDao()
            for ((keyId, action) in listOf(
                "space_convert_key" to KeyAction.SelectLeft,
                "enter_key" to KeyAction.UndoLastDelete,
                "switch_next_ime" to KeyAction.CtrlLock
            )) {
                overrides.upsert(SumireSpecialKeyActionOverrideEntity(
                    layoutType = "toggle", inputMode = "HIRAGANA", keyId = keyId, direction = "TAP",
                    overrideType = "KEY_ACTION", actionString = KeyActionMapper.fromKeyAction(action),
                    inputText = null, updatedAt = System.currentTimeMillis()
                ))
            }
            check(prefs.edit().putString("keyboard_order_preference", "[\"SUMIRE\"]")
                .putString("sumire_input_method_preference", "toggle").commit())
            ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { scenario ->
                val select = context.getString(com.kazumaproject.custom_keyboard.R.string.action_select_left)
                val undo = context.getString(com.kazumaproject.custom_keyboard.R.string.action_undo_last_delete)
                key(select)
                tap("Ctrl")
                key(lockedCtrl)
                tap(lockedCtrl)
                key("Ctrl")
                scenario.onActivity { it.editText.setText("abcd"); it.editText.setSelection(4) }
                tap(select)
                await("Sumire selection") { editorState(scenario).let { minOf(it.second, it.third) == 3 && maxOf(it.second, it.third) == 4 } }
                tap("Del")
                await("Sumire deletion") { editorState(scenario).first == "abc" }
                tap(undo)
                await("Sumire undo") { editorState(scenario).first == "abcd" }
            }
        } finally {
            if (oldIme.isNotEmpty() && oldIme != "null") shell("ime set $oldIme")
            if (!wasEnabled) shell("ime disable $ime")
        }
    }
}
