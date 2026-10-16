package com.kazumaproject.markdownhelperkeyboard.gemma.floating

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.text.InputType
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.BaseInputConnection
import android.view.inspector.WindowInspector
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.FastInputHostActivity
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaTranslationManager
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class GemmaFloatingWindowDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val ui get() = instrumentation.uiAutomation
    private val context get() = instrumentation.targetContext
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command))
        .bufferedReader().use { it.readText().trim() }
    private fun node(id: String): AccessibilityNodeInfo? {
        if (android.os.Build.VERSION.SDK_INT >= 33) ui.clearCache()
        return ui.windows.asSequence().mapNotNull { it.root }
            .flatMap { it.findAccessibilityNodeInfosByViewId("${context.packageName}:id/$id").asSequence() }
            .firstOrNull { it.isVisibleToUser }
    }
    private fun bounds(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)
    private fun viewBounds(id: Int, parent: Boolean = false): Rect {
        var result = Rect()
        instrumentation.runOnMainSync {
            val child = WindowInspector.getGlobalWindowViews().asSequence()
                .mapNotNull { it.findViewById<View>(id) }.first { it.isShown }
            val view = if (parent) child.parent as View else child
            val location = IntArray(2).also(view::getLocationOnScreen)
            result = Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
        }
        return result
    }
    private fun await(message: String, timeout: Long = 120_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (!condition()) {
            if (SystemClock.uptimeMillis() >= deadline) {
                diagnostics(message)
                error(message)
            }
            SystemClock.sleep(50)
        }
    }
    private fun diagnostics(message: String) {
        instrumentation.runOnMainSync {
            val data = JSONObject().put("message", message)
            runCatching {
                val candidate = WindowInspector.getGlobalWindowViews().asSequence()
                    .mapNotNull { it.findViewById<View>(R.id.suggestion_recycler_view) }.first { it.isShown }
                var owner = candidate.context
                while (owner is ContextWrapper && owner !is IMEService) owner = owner.baseContext
                val service = owner as IMEService
                fun field(name: String) = IMEService::class.java.getDeclaredField(name).apply { isAccessible = true }.get(service)
                listOf("isInputViewActive", "gemmaFloatingText", "currentInputType", "currentInputModeForSession").forEach {
                    data.put(it, field(it).toString())
                }
                data.put("fullscreen", service.isFullscreenMode)
                data.put("eligible", IMEService::class.java.getDeclaredMethod("canUseGemmaFloating").apply { isAccessible = true }.invoke(service))
                data.put("loadState", (field("gemmaTranslationManager") as GemmaTranslationManager).loadState.value.toString())
                val coordinator = field("gemmaFloatingCoordinator")
                data.put("request", coordinator?.let { GemmaFloatingCoordinator::class.java.getDeclaredField("current").apply { isAccessible = true }.get(it) }.toString())
                data.put("metrics", service.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds.toShortString())
                val location = IntArray(2).also(candidate::getLocationOnScreen)
                data.put("candidate", "${location.toList()} ${candidate.width}x${candidate.height}")
            }.onFailure { data.put("diagnosticError", it.toString()) }
            File(context.getExternalFilesDir(null), "gemma-floating-window-diagnostics.json").writeText(data.toString())
        }
    }
    private fun tap(rect: Rect) {
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                rect.exactCenterX(), rect.exactCenterY(), 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            check(ui.injectInputEvent(event, false)); event.recycle()
            SystemClock.sleep(30)
        }
    }
    private fun screenshot(name: String) {
        ui.takeScreenshot()?.let { bitmap ->
            File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test(timeout = 600_000)
    fun controlledMultilineScrollAndRealModelRotationAndPasswordSuppression() {
        assumeTrue("Use isolated QA application ID", context.packageName == "com.kazumaproject.gemmafloatingqa")
        val model = File(context.getExternalFilesDir(null), "gemma/gemma-4-E2B-it.litertlm")
        assumeTrue("Install real Gemma 4 E2B model", model.isFile)
        val oldIme = shell("settings get secure default_input_method")
        val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit().putBoolean("gemma_translation_enable_preference", true)
            .putString("gemma_translation_model_path_preference", model.absolutePath)
            .putString("gemma_translation_backend_preference", "cpu")
            .putBoolean(GemmaFloatingSettings.ENABLED, true)
            .putBoolean("save_last_used_keyboard", false)
            .putString("keyboard_order_preference", "[\"QWERTY\"]")
            .putBoolean("qwerty_english_direct_input_preference", false)
            .putBoolean("allow_fullscreen_mode_preference", false)
            .putBoolean("landscape_force_qwerty_preference", false).commit()
        val settings = GemmaFloatingSettings(preferences)
        val expected = (1..12).joinToString("\n") { "LINE $it" }
        settings.save(null, "Multiline QA", "Return exactly the following text, preserving every line break. Do not modify it:\n$expected")
        settings.select(settings.rules.last().id)
        ui.serviceInfo = ui.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        try {
            ui.setRotation(UiAutomation.ROTATION_FREEZE_0)
            shell("ime enable $ime"); shell("ime set $ime")
            ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { scenario ->
                scenario.onActivity { it.editText.setText("PREFIX "); it.restartEditorInput(false) }
                await("Keyboard did not appear") { node("key_a") != null }
                tap(bounds(requireNotNull(node("key_a"))))
                await("Multiline result did not appear") { node("gemma_floating_result") != null }
                val nativeResult = requireNotNull(node("gemma_floating_result")).text.toString()
                // Keep window coverage independent of stochastic model formatting. Verify the
                // real request used our selected rule, and record native compliance separately.
                instrumentation.runOnMainSync {
                    val label = WindowInspector.getGlobalWindowViews().asSequence()
                        .mapNotNull { it.findViewById<TextView>(R.id.gemma_floating_result) }.first()
                    val service = label.context as IMEService
                    fun field(name: String) = IMEService::class.java.getDeclaredField(name).apply { isAccessible = true }.get(service)
                    val coordinator = field("gemmaFloatingCoordinator") as GemmaFloatingCoordinator
                    val input = GemmaFloatingCoordinator::class.java.getDeclaredField("current").apply { isAccessible = true }
                        .get(coordinator) as GemmaFloatingInput
                    assertEquals(settings.selectedRule, input.rule)
                    File(context.getExternalFilesDir(null), "gemma-floating-window-model.json").writeText(
                        JSONObject().put("rule", input.rule.name).put("instruction", input.rule.instruction)
                            .put("source", input.text).put("result", nativeResult)
                            .put("modelMultilinePassed", nativeResult == expected).toString(),
                    )
                    (field("gemmaFloatingWindow") as GemmaFloatingWindow).show(input, expected)
                }
                await("Controlled multiline payload was not rendered") { node("gemma_floating_result")?.text?.toString() == expected }
                SystemClock.sleep(300) // WindowManager applies the new height and position asynchronously.
                val resultNode = requireNotNull(node("gemma_floating_result"))
                val scroll = requireNotNull(resultNode.parent)
                assertTrue("Long result did not provide scrolling", scroll.isScrollable)
                val viewport = viewBounds(R.id.gemma_floating_result, parent = true)
                val candidate = viewBounds(R.id.suggestion_recycler_view)
                File(context.getExternalFilesDir(null), "gemma-floating-geometry.json").writeText(
                    JSONObject().put("viewport", viewport.toShortString()).put("candidate", candidate.toShortString())
                        .put("accessibilityViewport", bounds(scroll).toShortString()).toString(),
                )
                screenshot("gemma-floating-multiline-placement.png")
                assertTrue("Floating result covered candidate strip: result=$viewport candidate=$candidate", viewport.bottom <= candidate.top)
                assertTrue(viewport.top >= 0)
                assertTrue(scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
                SystemClock.sleep(500)
                screenshot("gemma-floating-multiline.png")
                tap(viewport)
                await("Full multiline result was not committed") {
                    var complete = false
                    scenario.onActivity {
                        complete = it.editText.text.toString() == "PREFIX $expected" &&
                            BaseInputConnection.getComposingSpanStart(it.editText.text) < 0
                    }
                    complete
                }

                settings.save(null, "Short QA", "Return only the word TRANSFORMED.")
                settings.select(settings.rules.last().id)
                ui.setRotation(UiAutomation.ROTATION_FREEZE_90)
                await("Landscape rotation did not apply") {
                    var landscape = false
                    scenario.onActivity { landscape = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
                    landscape
                }
                scenario.onActivity { it.editText.setText("PREFIX "); it.restartEditorInput(false) }
                await("Landscape keyboard missing") { node("key_a") != null }
                SystemClock.sleep(300)
                tap(bounds(requireNotNull(node("key_a"))))
                await("Landscape result missing", 20_000) { node("gemma_floating_result") != null }
                val landscapeViewport = viewBounds(R.id.gemma_floating_result, parent = true)
                val landscapeCandidate = viewBounds(R.id.suggestion_recycler_view)
                assertTrue(landscapeViewport.top >= 0 && landscapeViewport.bottom <= landscapeCandidate.top)
                screenshot("gemma-floating-landscape.png")

                // Even a click already queued on an old view must not commit after rule changes.
                lateinit var staleLabel: TextView
                instrumentation.runOnMainSync {
                    staleLabel = WindowInspector.getGlobalWindowViews().asSequence()
                        .mapNotNull { it.findViewById<TextView>(R.id.gemma_floating_result) }.first()
                }
                val selectedId = settings.selectedId
                settings.select("")
                await("Old result remained after rule change") { node("gemma_floating_result") == null }
                instrumentation.runOnMainSync { staleLabel.performClick() }
                scenario.onActivity {
                    assertEquals("PREFIX a", it.editText.text.toString())
                    assertTrue(BaseInputConnection.getComposingSpanStart(it.editText.text) >= 0)
                }
                settings.select(selectedId)
                await("Restored rule did not generate a result") { node("gemma_floating_result") != null }
                tap(bounds(requireNotNull(node("key_delete"))))
                await("Deleted composing text retained a result") { node("gemma_floating_result") == null }
                SystemClock.sleep(2_000)
                assertNull("Cancelled result reappeared after deletion", node("gemma_floating_result"))
                scenario.onActivity { assertEquals("PREFIX ", it.editText.text.toString()) }
                tap(bounds(requireNotNull(node("key_a"))))
                await("New input did not resume suggestions") { node("gemma_floating_result") != null }

                scenario.onActivity {
                    it.editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    it.restartEditorInput(true)
                }
                await("Old result remained in password editor") { node("gemma_floating_result") == null }
                await("Password keyboard missing") { node("key_a") != null }
                tap(bounds(requireNotNull(node("key_a"))))
                SystemClock.sleep(2_000)
                assertNull("Password input triggered floating inference", node("gemma_floating_result"))
            }
        } finally {
            ui.setRotation(UiAutomation.ROTATION_UNFREEZE)
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
        }
    }
}
