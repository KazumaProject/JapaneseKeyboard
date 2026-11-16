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
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.KeyboardEditorFragment
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.KeyEditorFragment
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.CustomFreeFlickEditorDialog
import com.kazumaproject.markdownhelperkeyboard.sumire_popup.PopupMapEditorFragment
import com.kazumaproject.markdownhelperkeyboard.sumire_popup.FreeFlickPreferences
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.AppModule
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises production editing and IME input in an isolated install. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class CustomFreeFlickKeyDeviceTest {
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
            details = views().filterIsInstance<FlickKeyboardView>().joinToString { "surface=${it.id}/${it.width}x${it.height}" }
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
    @Test fun editorConvertsKeyAndRestoresTheMapDraftAcrossRecreation() = runBlocking<Unit> {
        val context = ins.targetContext
        check(context.packageName.endsWith(".customfreeflicktest"))
        val db = AppModule.providesLearnDatabase(context)
        val repository = KeyboardRepository(db.keyboardLayoutDao())
        val original = KeyData("Old", 0, 0, false, KeyAction.Text("a"), keyId = "editor-key")
        val id = repository.saveLayout(KeyboardLayout(listOf(original), emptyMap(), 1, 1), "Free Flick editor verification", null)
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                fun host(): NavHostFragment = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().first()
                    .supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                fun keyEditor() = host().childFragmentManager.primaryNavigationFragment as KeyEditorFragment
                fun mapEditor(): PopupMapEditorFragment? {
                    val dialog = keyEditor().childFragmentManager.findFragmentByTag("freeFlickEditor") as? CustomFreeFlickEditorDialog
                    return dialog?.childFragmentManager?.findFragmentById(R.id.custom_free_flick_editor_host) as? PopupMapEditorFragment
                }
                await("Settings initialized") { runCatching { host().navController.currentDestination != null }.getOrDefault(false) }
                main { host().navController.navigate(R.id.keyboardEditorFragment, Bundle().apply { putLong("layoutId", id) }) }
                await("Layout editor loaded") {
                    val fragment = host().childFragmentManager.primaryNavigationFragment as? KeyboardEditorFragment
                    fragment?.view?.let { descendants(it).filterIsInstance<TextView>().any { text -> text.text.toString() == "Old" } } == true
                }
                main {
                    val editor = host().childFragmentManager.primaryNavigationFragment as KeyboardEditorFragment
                    editor.onKeySelected("editor-key"); editor.onKeySelected("editor-key")
                }
                await("Key editor initialized") { runCatching { keyEditor().view?.findViewById<View>(R.id.button_done)?.isEnabled == true }.getOrDefault(false) }
                main {
                    keyEditor().requireView().findViewById<View>(R.id.chip_free_flick).performClick()
                    keyEditor().requireView().findViewById<View>(R.id.edit_free_flick_map).performClick()
                }
                await("Map editor opened") { runCatching { mapEditor()?.view != null }.getOrDefault(false) }
                main {
                    descendants(mapEditor()!!.requireView()).filterIsInstance<TextInputLayout>()
                        .single { it.hint == context.getString(R.string.popup_input) }.editText!!.setText("edited😀")
                }
                scenario.recreate()
                await("Map draft restored") { runCatching {
                    descendants(mapEditor()!!.requireView()).filterIsInstance<TextInputLayout>()
                        .any { it.hint == context.getString(R.string.popup_input) && it.editText?.text.toString() == "edited😀" }
                }.getOrDefault(false) }
                main {
                    descendants(mapEditor()!!.requireView()).filterIsInstance<MaterialButton>()
                        .single { it.text == context.getString(R.string.popup_apply) }.performClick()
                }
                await("Map applied to key draft") { keyEditor().childFragmentManager.findFragmentByTag("freeFlickEditor") == null }
                scenario.recreate()
                await("Key type restored") { runCatching {
                    keyEditor().requireView().findViewById<com.google.android.material.chip.Chip>(R.id.chip_free_flick).isChecked &&
                        keyEditor().requireView().findViewById<View>(R.id.button_done).isEnabled
                }.getOrDefault(false) }
                main { keyEditor().requireView().findViewById<View>(R.id.button_done).performClick() }
                await("Returned to layout editor") { host().childFragmentManager.primaryNavigationFragment is KeyboardEditorFragment }
                await("Save action ready") { views().any { it.id == R.id.action_save } }
                main { views().first { it.id == R.id.action_save }.performClick() }
                val deadline = SystemClock.uptimeMillis() + 10000
                var saved: KeyboardLayout
                do {
                    saved = repository.getFullLayout(id).first()
                    if (saved.keys.single().keyType == KeyType.FREE_FLICK) break
                    SystemClock.sleep(100)
                } while (SystemClock.uptimeMillis() < deadline)
                val key = saved.keys.single()
                assertEquals(KeyType.FREE_FLICK, key.keyType)
                assertEquals("edited😀", key.popupMapConfig!!.map(key.popupMapConfig!!.startMapId)!!.tapText)
                assertEquals("editor-key", key.keyId)
            }
        } finally { repository.deleteLayoutConfirmed(id); db.close() }
    }

    @Test fun actualCustomImeCommitsTapTextAndMapTransitionsInBothPresentations() = runBlocking<Unit> {
        val context = ins.targetContext
        check(context.packageName.endsWith(".customfreeflicktest"))
        val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val oldIme = shell("settings get secure default_input_method")
        val wasEnabled = shell("ime list -s").lines().contains(ime)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val db = AppModule.providesLearnDatabase(context)
        val repository = KeyboardRepository(db.keyboardLayoutDao())
        val config = PopupKeyConfig("free", "base", listOf(
            PopupMap("base", "Base", "tap😀", listOf(
                PopupRegion("text", PopupBounds(-1.5f, -.5f, -.5f, .5f), PopupAction(text = "left")),
                PopupRegion("switch", PopupBounds(.5f, -.5f, 1.5f, .5f), PopupAction(PopupActionType.SWITCH, targetMapId = "next")))),
            PopupMap("next", "Next", "nextかな", listOf(
                PopupRegion("back", PopupBounds(-.5f, -1.5f, .5f, -.5f), PopupAction(PopupActionType.BACK))))))
        val keys = (0 until 6).map { index ->
            if (index == 4) KeyData("FREE", 1, 1, true, KeyAction.Text("tap😀"), keyType = KeyType.FREE_FLICK,
                keyId = "free", popupMapConfig = config)
            else KeyData("P$index", index / 3, index % 3, false, KeyAction.Text("p"), keyId = "plain$index")
        }
        val id = repository.saveLayout(KeyboardLayout(keys, emptyMap(), 3, 2), "Free Flick input verification", null)
        try {
            val stableId = repository.getLayoutsNotFlow().single { it.layoutId == id }.stableId
            shell("ime enable $ime"); shell("ime set $ime")
            for (presentation in PopupMapPresentation.entries) {
                check(prefs.edit().clear().putString("keyboard_order_preference", "[\"CUSTOM\"]")
                    .putBoolean("save_last_used_keyboard", false).putBoolean("keyboard_floating_preference", false)
                    .putBoolean("remember_last_custom_keyboard_preference", true).putString("last_used_custom_keyboard_stable_id", stableId)
                    .putBoolean("live_conversion_preference", false).putBoolean("clipboard_preview_enable_preference", false)
                    .putBoolean(FreeFlickPreferences.NEAREST, false).putString(FreeFlickPreferences.PRESENTATION, presentation.preferenceValue).commit())
                ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { scenario ->
                    scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                    await("Custom keyboard visible") { views().filterIsInstance<TextView>().any { it.text == "FREE" } }
                    SystemClock.sleep(500)
                    fun gesture(vararg points: Pair<Float, Float>, cancel: Boolean = false) {
                        val bounds = key("FREE")
                        send(MotionEvent.ACTION_DOWN, bounds.exactCenterX(), bounds.exactCenterY())
                        points.forEach { (x, y) -> send(MotionEvent.ACTION_MOVE,
                            bounds.exactCenterX() + x * bounds.width(), bounds.exactCenterY() + y * bounds.height()); SystemClock.sleep(40) }
                        val last = points.lastOrNull() ?: (0f to 0f)
                        send(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP,
                            bounds.exactCenterX() + last.first * bounds.width(), bounds.exactCenterY() + last.second * bounds.height())
                    }
                    var expected = ""
                    fun text(value: String) {
                        expected += value
                        await("IME output $expected") {
                            var actual = ""; scenario.onActivity { actual = it.editText.text.toString() }; actual == expected
                        }
                    }
                    gesture(); text("tap😀")
                    gesture(-1f to 0f); text("left")
                    gesture(1f to 0f, 0f to 0f); text("nextかな")
                    gesture(1f to 0f, 0f to -1f, 0f to 0f); text("tap😀")
                    gesture(-1f to 0f, cancel = true); text("")
                }
            }
        } finally {
            repository.deleteLayoutConfirmed(id)
            if (oldIme.isNotEmpty() && oldIme != "null") shell("ime set $oldIme")
            if (!wasEnabled) shell("ime disable $ime")
            db.close()
        }
    }
}
