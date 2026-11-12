package com.kazumaproject.markdownhelperkeyboard

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.KeyboardEditorFragment
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.KeyEditorFragment
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.QwertyKeyEditorView
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.custom_keyboard.view.QwertyAlternativesView
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.AppModule
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against real IME windows. Use an isolated customqwertytest application ID. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class CustomQwertyKeyDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        ins.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
    private fun main(block: () -> Unit) = ins.runOnMainSync(block)
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
    private fun views() = WindowInspector.getGlobalWindowViews().asSequence().flatMap(::descendants)
        .filter { it.isAttachedToWindow && it.isShown && it.width > 0 && it.height > 0 &&
            generateSequence(it as View?) { parent -> parent.parent as? View }.all { parent -> parent.alpha > 0.9f } &&
            it.getGlobalVisibleRect(Rect()) }
    private fun await(message: String, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            main { ready = predicate() }
            if (ready) return
            SystemClock.sleep(50)
        }
        var details = ""
        main {
            details = views().filterIsInstance<FlickKeyboardView>().joinToString { "surface=${it.id}/${it.width}x${it.height}" } +
                views().filterIsInstance<QwertyKeyEditorView>().joinToString { "editor=${it.config}" }
        }
        error("$message: $details")
    }
    private fun key(label: String): Rect {
        var result: Rect? = null
        await("Missing key $label") {
            result = views().filterIsInstance<FlickKeyboardView>().flatMap(::descendants)
                .firstOrNull { it.isShown && ((it as? TextView)?.text?.toString() == label || it.contentDescription == label) }
                ?.let { view -> IntArray(2).also(view::getLocationOnScreen).let { Rect(it[0], it[1], it[0] + view.width, it[1] + view.height) } }
            result != null
        }
        return result!!
    }
    private var downTime = 0L
    private fun send(action: Int, x: Float, y: Float) {
        if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try { check(ins.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
    }
    private fun tap(label: String) {
        val bounds = key(label)
        send(MotionEvent.ACTION_DOWN, bounds.exactCenterX(), bounds.exactCenterY())
        SystemClock.sleep(40)
        send(MotionEvent.ACTION_UP, bounds.exactCenterX(), bounds.exactCenterY())
        SystemClock.sleep(120)
    }
    private fun hold(label: String, slot: Int? = null, cancel: Boolean = false) {
        val key = key(label)
        send(MotionEvent.ACTION_DOWN, key.exactCenterX(), key.exactCenterY())
        var popup: QwertyAlternativesView? = null
        await("Long-press alternatives not visible") {
            popup = views().filterIsInstance<QwertyAlternativesView>().firstOrNull()
            popup != null
        }
        var x = key.exactCenterX()
        var y = key.exactCenterY()
        if (slot != null) main {
            val location = IntArray(2).also(popup!!::getLocationOnScreen)
            x = location[0] + (slot % 2 + 0.5f) * popup!!.width / 2
            y = location[1] + (slot / 2 + 0.5f) * popup!!.height / 2
        }
        if (slot != null) { send(MotionEvent.ACTION_MOVE, x, y); SystemClock.sleep(60) }
        send(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, x, y)
        await("Popup leaked after release") { views().none { it is QwertyAlternativesView } }
    }
    private fun assertText(scenario: ActivityScenario<FastInputHostActivity>, expected: String) {
        var actual = ""
        val deadline = SystemClock.uptimeMillis() + 5000
        do {
            scenario.onActivity { actual = it.editText.text.toString() }
            if (actual == expected) return
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        assertEquals(expected, actual)
    }

    @Test fun actualImeSupportsConfigurableKeysOnDockedFloatingAndSplitSurfaces() = runBlocking<Unit> {
        val context = ins.targetContext
        check(context.packageName.startsWith("com.kazumaproject.customqwertytest")) { "Use an isolated customqwertytest application ID" }
        check(shell("getprop ro.kernel.qemu") == "1") { "Run on an emulator" }
        val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val oldIme = shell("settings get secure default_input_method")
        val wasEnabled = shell("ime list -s").lines().contains(ime)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val db = AppModule.providesLearnDatabase(context)
        val repository = KeyboardRepository(db.keyboardLayoutDao())
        val layoutIds = mutableMapOf<Boolean, Long>()
        val normal = QwertyKeyLayer("a", "KEY", "peek", 2,
            listOf(QwertyPopupSlot("é", "acute"), QwertyPopupSlot(), QwertyPopupSlot("👩‍💻", "emoji"), QwertyPopupSlot("word output", "word")))
        val shifted = normal.copy(output = "keepCase", label = "SHIFTKEY", preview = "shiftPeek",
            slots = normal.slots.mapIndexed { i, slot -> if (i == 0) slot.copy(output = "lowCase") else slot })
        val keys = listOf(
            KeyData("KEY", 0, 0, false, KeyAction.Text("a"), keyType = KeyType.QWERTY, keyId = "a", qwertyConfig = QwertyKeyConfig(normal, shifted)),
            KeyData("NEXT", 0, 1, false, KeyAction.Text("r"), keyType = KeyType.QWERTY, keyId = "r", qwertyConfig = QwertyKeyConfig(QwertyKeyLayer("r", "NEXT", "nextPeek"))),
            KeyData("Plain", 0, 2, false, KeyAction.Text("x"), keyId = "plain"),
            KeyData("shift", 1, 0, false, KeyAction.ShiftKey, isSpecialKey = true, keyId = "shift"),
            KeyData("caps", 1, 1, false, KeyAction.CapLockKey, isSpecialKey = true, keyId = "caps"),
            KeyData("space", 1, 2, false, KeyAction.Space, isSpecialKey = true, keyId = "space"))
        try {
            for (direct in listOf(false, true)) {
                layoutIds[direct] = repository.saveLayout(KeyboardLayout(keys, emptyMap(), 3, 2, isDirectMode = direct), "QWERTY device verification $direct", null)
                val restored = repository.getFullLayout(layoutIds.getValue(direct)).first()
                assertEquals(keys.first().qwertyConfig, restored.keys.first { it.keyId == "a" }.qwertyConfig)
            }
            shell("ime enable $ime")
            shell("ime set $ime")
            for (direct in listOf(false, true)) {
                val layoutId = layoutIds.getValue(direct)
                val stableId = repository.getLayoutsNotFlow().single { it.layoutId == layoutId }.stableId
                for (mode in listOf("normal", "floating", "split")) for (preview in listOf(true, false)) {
                    android.util.Log.i("CustomQwertyDevice", "direct=$direct mode=$mode preview=$preview")
                    check(prefs.edit().clear()
                        .putString("keyboard_order_preference", if (mode == "split") "[\"SPLIT\"]" else "[\"CUSTOM\"]")
                        .putBoolean("save_last_used_keyboard", false)
                        .putBoolean("keyboard_floating_preference", mode == "floating")
                        .putBoolean("custom_keyboard_show_key_preview", preview)
                        .putBoolean("key_popup_use_custom_color_preference", true)
                        .putInt("key_popup_background_color_preference", 0xff243447.toInt())
                        .putInt("key_popup_text_color_preference", 0xffeeeeee.toInt())
                        .putBoolean("remember_last_custom_keyboard_preference", true)
                        .putString("last_used_custom_keyboard_stable_id", stableId)
                        .putBoolean("independent_multi_touch_preference", true)
                        .putInt("long_press_timeout_preference", 300)
                        .putBoolean("live_conversion_preference", false)
                        .putBoolean("clipboard_preview_enable_preference", false)
                        .putString("split_keyboard_main_type", "CUSTOM")
                        .putString("split_keyboard_sub_type", "CUSTOM")
                        .putString("split_keyboard_main_custom", stableId)
                        .putString("split_keyboard_sub_custom", stableId)
                        .putFloat("split_keyboard_main_portrait_width", 180f)
                        .putFloat("split_keyboard_sub_portrait_width", 180f)
                        .putString(KeyboardSkinId.PREFERENCE_KEY, if (preview) KeyboardSkinId.DEFAULT.preferenceValue else KeyboardSkinId.CUPERTINO_LIGHT.preferenceValue)
                        .commit())
                    ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { scenario ->
                        await("Keyboard surface count") { views().filterIsInstance<FlickKeyboardView>().count() == if (mode == "split") 2 else 1 }
                        // Await the initial IME layout and skin transition before the first gesture.
                        var previous: Rect? = null
                        var stable = 0
                        val deadline = SystemClock.uptimeMillis() + 10000
                        while (stable < 6 && SystemClock.uptimeMillis() < deadline) {
                            val bounds = key("KEY")
                            stable = if (bounds == previous) stable + 1 else 0
                            previous = bounds
                            SystemClock.sleep(100)
                        }
                        check(stable >= 6) { "Keyboard geometry did not settle" }
                        tap("KEY")
                        assertText(scenario, "a")
                        val bounds = key("KEY")
                        send(MotionEvent.ACTION_DOWN, bounds.exactCenterX(), bounds.exactCenterY())
                        SystemClock.sleep(80)
                        main {
                            val bubble = views().filterIsInstance<TextView>().firstOrNull { it.text.toString() == "peek" }
                            assertEquals("preview setting", preview, bubble != null)
                            if (preview) assertEquals(0xffeeeeee.toInt(), bubble!!.currentTextColor)
                        }
                        send(MotionEvent.ACTION_CANCEL, bounds.exactCenterX(), bounds.exactCenterY())
                        assertText(scenario, "a")
                        hold("KEY")
                        assertText(scenario, "aé")
                        hold("KEY", slot = 2)
                        assertText(scenario, "aé👩‍💻")
                        hold("KEY", slot = 1)
                        assertText(scenario, "aé👩‍💻")
                        hold("KEY", cancel = true)
                        assertText(scenario, "aé👩‍💻")
                        tap("shift")
                        tap("SHIFTKEY")
                        assertText(scenario, "aé👩‍💻keepCase")
                        key("KEY")
                        tap("caps")
                        hold("SHIFTKEY")
                        assertText(scenario, "aé👩‍💻keepCaselowCase")
                        key("SHIFTKEY")
                        tap("caps")
                        key("KEY")
                        tap("Plain")
                        assertText(scenario, "aé👩‍💻keepCaselowCasex")
                        if (mode == "normal" && preview) {
                            overlapHoldAndTap()
                            assertText(scenario, "aé👩‍💻keepCaselowCasexré")
                        }
                    }
                }
            }
        } finally {
            layoutIds.values.forEach { repository.deleteLayoutConfirmed(it) }
            if (oldIme.isNotEmpty() && oldIme != "null") shell("ime set $oldIme")
            if (!wasEnabled) shell("ime disable $ime")
            db.close()
        }
    }
    private fun overlapHoldAndTap() {
        val first = key("KEY")
        val second = key("NEXT")
        val started = SystemClock.uptimeMillis()
        fun pointers(action: Int, index: Int, two: Boolean) {
            val positions = if (two) listOf(first, second) else listOf(first)
            val properties = positions.mapIndexed { i, _ -> MotionEvent.PointerProperties().apply {
                id = if (i == 0) 3 else 19
                toolType = MotionEvent.TOOL_TYPE_FINGER
            } }.toTypedArray()
            val coordinates = positions.map { rect -> MotionEvent.PointerCoords().apply {
                x = rect.exactCenterX(); y = rect.exactCenterY(); pressure = 1f; size = 1f
            } }.toTypedArray()
            val event = MotionEvent.obtain(started, SystemClock.uptimeMillis(),
                action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), positions.size,
                properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { check(ins.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        pointers(MotionEvent.ACTION_DOWN, 0, false)
        await("First pointer hold popup") { views().any { it is QwertyAlternativesView } }
        pointers(MotionEvent.ACTION_POINTER_DOWN, 1, true)
        SystemClock.sleep(40)
        pointers(MotionEvent.ACTION_POINTER_UP, 1, true)
        await("First hold survived the other key") { views().any { it is QwertyAlternativesView } }
        pointers(MotionEvent.ACTION_UP, 0, false)
    }

    @Test fun editorConvertsAnExistingKeyAndRetainsDraftAcrossRecreation() = runBlocking<Unit> {
        val context = ins.targetContext
        check(context.packageName.startsWith("com.kazumaproject.customqwertytest"))
        val db = AppModule.providesLearnDatabase(context)
        val repository = KeyboardRepository(db.keyboardLayoutDao())
        val original = KeyData("Old", 0, 0, false, KeyAction.Text("a"), keyId = "editor-key")
        val id = repository.saveLayout(KeyboardLayout(listOf(original), emptyMap(), 1, 1), "Editor verification", null)
        val expected = QwertyKeyConfig(QwertyKeyLayer("edited", "Key label", "Preview", 2,
            listOf(QwertyPopupSlot("one", "First"), QwertyPopupSlot(), QwertyPopupSlot("👩‍💻"))))
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                fun host(): NavHostFragment = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().first()
                    .supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                await("Settings initialized") { runCatching { host().navController.currentDestination != null }.getOrDefault(false) }
                main { host().navController.navigate(R.id.keyboardEditorFragment, Bundle().apply { putLong("layoutId", id) }) }
                await("Layout editor loaded") {
                    val fragment = host().childFragmentManager.primaryNavigationFragment as? KeyboardEditorFragment
                    fragment?.view?.let { descendants(it).filterIsInstance<TextView>().any { text -> text.text.toString() == "Old" } } == true
                }
                main {
                    val editor = host().childFragmentManager.primaryNavigationFragment as KeyboardEditorFragment
                    editor.onKeySelected("editor-key")
                    editor.onKeySelected("editor-key")
                }
                await("Key editor initialized") {
                    val fragment = host().childFragmentManager.primaryNavigationFragment as? KeyEditorFragment
                    fragment?.view?.findViewById<android.widget.EditText>(R.id.key_label_edittext)?.text?.toString() == "Old" &&
                        fragment.view?.findViewById<View>(R.id.button_done)?.isEnabled == true
                }
                main {
                    val fragment = host().childFragmentManager.primaryNavigationFragment as KeyEditorFragment
                    fragment.requireView().findViewById<View>(R.id.chip_qwerty).performClick()
                    fragment.requireView().findViewById<QwertyKeyEditorView>(R.id.qwerty_key_editor).setConfig(expected)
                    assertTrue(fragment.requireView().findViewById<com.google.android.material.chip.Chip>(R.id.chip_qwerty).isChecked)
                    assertTrue(fragment.requireView().findViewById<View>(R.id.button_done).isEnabled)
                }
                scenario.recreate()
                await("QWERTY draft restored") {
                    val fragment = host().childFragmentManager.primaryNavigationFragment as? KeyEditorFragment
                    fragment?.view?.findViewById<QwertyKeyEditorView>(R.id.qwerty_key_editor)?.config == expected
                }
                main {
                    val fragment = host().childFragmentManager.primaryNavigationFragment as KeyEditorFragment
                    val done = fragment.requireView().findViewById<View>(R.id.button_done)
                    assertTrue(done.isEnabled)
                    done.performClick()
                }
                await("Returned to layout editor") { host().childFragmentManager.primaryNavigationFragment is KeyboardEditorFragment }
                await("Save action ready") { views().any { it.id == R.id.action_save } }
                main { views().first { it.id == R.id.action_save }.performClick() }
                val deadline = SystemClock.uptimeMillis() + 10000
                var saved: KeyboardLayout
                do {
                    saved = repository.getFullLayout(id).first()
                    if (saved.keys.single().keyType == KeyType.QWERTY) break
                    SystemClock.sleep(100)
                } while (SystemClock.uptimeMillis() < deadline)
                assertEquals(KeyType.QWERTY, saved.keys.single().keyType)
                assertEquals(expected, saved.keys.single().qwertyConfig)
                assertEquals(original.row, saved.keys.single().row)
                assertEquals(original.column, saved.keys.single().column)
            }
        } finally { repository.deleteLayoutConfirmed(id); db.close() }
    }

    @Test fun romajiInputAndLayoutDuplicationRetainTheConfiguredOutputs() = runBlocking<Unit> {
        val context = ins.targetContext
        check(context.packageName.startsWith("com.kazumaproject.customqwertytest"))
        val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val oldIme = shell("settings get secure default_input_method")
        val wasEnabled = shell("ime list -s").lines().contains(ime)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val db = AppModule.providesLearnDatabase(context)
        val repository = KeyboardRepository(db.keyboardLayoutDao())
        val normal = QwertyKeyLayer("a", "KEY", columns = 2, slots = listOf(
            QwertyPopupSlot("ka", "literal"), QwertyPopupSlot(), QwertyPopupSlot("👩‍💻")))
        val config = QwertyKeyConfig(normal, normal.copy(label = "SHIFTKEY", output = "a"))
        val keys = listOf(
            KeyData("KEY", 0, 0, false, KeyAction.Text("a"), keyType = KeyType.QWERTY, keyId = "a", qwertyConfig = config),
            KeyData("shift", 0, 1, false, KeyAction.ShiftKey, isSpecialKey = true, keyId = "shift"))
        val id = repository.saveLayout(KeyboardLayout(keys, emptyMap(), 2, 1, isRomaji = true), "Romaji verification", null)
        var duplicateId: Long? = null
        try {
            val before = repository.getLayoutsNotFlow().map { it.layoutId }.toSet()
            repository.duplicateLayout(id)
            duplicateId = repository.getLayoutsNotFlow().single { it.layoutId !in before }.layoutId
            assertEquals(config, repository.getFullLayout(duplicateId!!).first().keys.first { it.keyId == "a" }.qwertyConfig)
            repository.deleteLayoutConfirmed(duplicateId!!)
            duplicateId = null
            val stableId = repository.getLayoutsNotFlow().single { it.layoutId == id }.stableId
            check(prefs.edit().clear().putString("keyboard_order_preference", "[\"CUSTOM\"]")
                .putBoolean("save_last_used_keyboard", false)
                .putBoolean("remember_last_custom_keyboard_preference", true)
                .putString("last_used_custom_keyboard_stable_id", stableId)
                .putBoolean("live_conversion_preference", false)
                .putBoolean("custom_keyboard_two_words_preference", true)
                .putInt("long_press_timeout_preference", 300).commit())
            shell("ime enable $ime")
            shell("ime set $ime")
            ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { scenario ->
                key("KEY")
                SystemClock.sleep(800)
                tap("KEY")
                assertText(scenario, "あ")
                tap("shift")
                tap("SHIFTKEY")
                assertText(scenario, "あa")
                key("KEY")
                hold("KEY")
                assertText(scenario, "あaka")
            }
        } finally {
            duplicateId?.let { repository.deleteLayoutConfirmed(it) }
            repository.deleteLayoutConfirmed(id)
            if (oldIme.isNotEmpty() && oldIme != "null") shell("ime set $oldIme")
            if (!wasEnabled) shell("ime disable $ime")
            db.close()
        }
    }

}
