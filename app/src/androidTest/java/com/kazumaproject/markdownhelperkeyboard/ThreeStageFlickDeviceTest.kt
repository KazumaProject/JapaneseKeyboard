package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.inputmethod.BaseInputConnection
import android.view.accessibility.AccessibilityNodeInfo
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection.*
import com.kazumaproject.custom_keyboard.data.copyWithKeys
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export.KeyboardLayoutImportResult
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export.KeyboardLayoutJsonExporter
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export.KeyboardLayoutJsonImporter
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.HierarchicalFlickEditorState
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.KeyboardEditorViewModel
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.AppModule
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Run only with an isolated threestagetest application ID and an explicitly selected device. */
@RunWith(AndroidJUnit4::class)
class ThreeStageFlickDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    private fun keyBounds(label: String, floating: Boolean): Rect {
        fun collect(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = listOf(node) +
            (0 until node.childCount).flatMap { node.getChild(it)?.let(::collect).orEmpty() }
        val surfaceId = if (floating) "custom_layout_floating" else "custom_layout_default"
        val deadline = SystemClock.uptimeMillis() + 60000
        while (SystemClock.uptimeMillis() < deadline) {
            val nodes = automation.windows.flatMap { it.root?.let(::collect).orEmpty() }
            val keys = nodes.filter {
                it.isVisibleToUser && it.viewIdResourceName?.endsWith("/$surfaceId") == true
            }.flatMap(::collect)
            keys.firstOrNull {
                it.packageName?.toString() == instrumentation.targetContext.packageName &&
                    it.isVisibleToUser && it.isClickable && it.text?.toString()?.trim() == label
            }?.let { return Rect().also(it::getBoundsInScreen) }
            SystemClock.sleep(100)
        }
        error("Missing custom key: $label on $surfaceId")
    }

    private fun settledKey(label: String, floating: Boolean): Rect {
        var previous: Rect? = null
        repeat(30) {
            val bounds = keyBounds(label, floating)
            if (bounds == previous) return bounds
            previous = bounds
            SystemClock.sleep(150)
        }
        error("Custom key did not settle")
    }

    private fun awaitOutput(scenario: ActivityScenario<FastInputHostActivity>, direct: Boolean, floating: Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10000
        var text = ""
        var composing = false
        do {
            scenario.onActivity {
                text = it.editText.text.toString()
                composing = BaseInputConnection.getComposingSpanStart(it.editText.text) >= 0
            }
            if (text == "ぞ" && composing == !direct) return
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        assertEquals("direct=$direct floating=$floating", "ぞ", text)
        assertEquals("configured direct=$direct floating=$floating", !direct, composing)
    }

    private fun gesture(bounds: Rect, cancel: Boolean = false) {
        val x = bounds.exactCenterX()
        val y = bounds.exactCenterY()
        val distance = maxOf(80f, bounds.width() * 0.6f)
        val start = SystemClock.uptimeMillis()
        val points = listOf(
            Triple(MotionEvent.ACTION_DOWN, x, y),
            Triple(MotionEvent.ACTION_MOVE, x + distance, y),
            Triple(MotionEvent.ACTION_MOVE, x, y + distance),
            Triple(MotionEvent.ACTION_MOVE, x - distance, y),
            Triple(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, x - distance, y)
        )
        points.forEach { (action, px, py) ->
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, px, py, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { check(automation.injectInputEvent(event, true)) } finally { event.recycle() }
            SystemClock.sleep(30)
        }
        SystemClock.sleep(150)
    }

    @Test fun savedAndRestoredCustomHierarchyInputsThroughTheRealIme() = runBlocking<Unit> {
        val context = instrumentation.targetContext
        assumeTrue("Use an isolated threestagetest application ID",
            context.packageName.startsWith("com.kazumaproject.threestagetest"))
        val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val oldIme = shell("settings get secure default_input_method").trim()
        val wasEnabled = shell("ime list -s").lineSequence().any { it.trim() == ime }
        val db = AppModule.providesLearnDatabase(context)
        try {
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            }
            val repository = KeyboardRepository(db.keyboardLayoutDao())
            repository.getLayoutsNotFlow().forEach { repository.deleteLayoutConfirmed(it.layoutId) }
            val editor = KeyboardEditorViewModel(repository)
            editor.applyTemplate(editor.availableTemplates.single { it.nameResId == R.string.template_three_stage_flick }.layout)
            val layout = editor.uiState.value.layout
            val key = layout.keys.single { it.label == "か" }
            val tree = HierarchicalFlickEditorState(layout.hierarchicalFlickMaps.getValue(key.keyId!!))
            tree.open(RIGHT)
            tree.makeBranch(DOWN)
            tree.open(DOWN)
            tree.setOutput(LEFT, "ぞ")
            assertTrue(editor.updateKeyAndMappings(key, emptyMap(), emptyMap(), emptyMap(), emptyMap(), hierarchicalFlick = tree.definition))
            val id = repository.saveLayout(editor.uiState.value.layout, "Three-stage device test", null)
            val reloaded = repository.getFullLayout(id).first()
            assertEquals(editor.uiState.value.layout.hierarchicalFlickMaps, reloaded.hierarchicalFlickMaps)
            val backup = KeyboardLayoutJsonExporter.toJson(repository.getAllFullLayoutsForExport())
            val parsed = KeyboardLayoutJsonImporter.parse(backup) as KeyboardLayoutImportResult.Success
            assertTrue(repository.importLayouts(parsed.layouts) is KeyboardLayoutImportResult.Success)
            val importedLayout = repository.getLayoutsNotFlow().single { it.layoutId != id }
            val restored = repository.getFullLayout(importedLayout.layoutId).first()
            assertEquals(reloaded.hierarchicalFlickMaps, restored.hierarchicalFlickMaps)
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            shell("ime enable $ime")
            shell("ime set $ime")
            for (direct in listOf(false, true)) for (floating in listOf(false, true)) {
                val label = "3F${if (direct) 1 else 0}${if (floating) 1 else 0}"
                val configured = restored.copy(isDirectMode = direct).copyWithKeys(restored.keys.map {
                    if (it.keyId == key.keyId) it.copy(label = label) else it
                })
                repository.saveLayout(configured, importedLayout.name, importedLayout.layoutId)
                check(prefs.edit()
                    .putString("keyboard_order_preference", "[\"CUSTOM\"]")
                    .putBoolean("save_last_used_keyboard", false)
                    .putBoolean("remember_last_custom_keyboard_preference", true)
                    .putString("last_used_custom_keyboard_stable_id", importedLayout.stableId)
                    .putBoolean("remember_custom_keyboard_input_mode_preference", false)
                    .putBoolean("keyboard_floating_preference", floating)
                    .putBoolean("live_conversion_preference", false)
                    .commit())
                android.util.Log.i("ThreeStageFlickDeviceTest", "direct=$direct floating=$floating")
                ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { scenario ->
                    scenario.onActivity { it.restartEditorInput(clearText = true) }
                    instrumentation.waitForIdleSync()
                    val bounds = settledKey(label, floating)
                    gesture(bounds, cancel = true)
                    scenario.onActivity { assertEquals("", it.editText.text.toString()) }
                    gesture(bounds)
                    awaitOutput(scenario, direct, floating)
                }
            }
        } finally {
            if (oldIme.isNotEmpty() && oldIme != "null") shell("ime set $oldIme")
            if (!wasEnabled) shell("ime disable $ime")
            db.close()
        }
    }
}
