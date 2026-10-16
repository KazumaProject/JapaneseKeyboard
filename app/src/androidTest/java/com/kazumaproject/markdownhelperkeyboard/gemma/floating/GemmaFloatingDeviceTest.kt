package com.kazumaproject.markdownhelperkeyboard.gemma.floating

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Intent
import android.graphics.Rect
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.inspector.WindowInspector
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.BaseInputConnection
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.FastInputHostActivity
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaTranslationManager
import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaRuntimeClient
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real model, keyboard touch events, IME-owned result window and composing replacement. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class GemmaFloatingDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val ui get() = instrumentation.uiAutomation
    private val context get() = instrumentation.targetContext
    private val preferences get() = PreferenceManager.getDefaultSharedPreferences(context)
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command))
        .bufferedReader().use { it.readText().trim() }

    private fun node(id: String): AccessibilityNodeInfo? {
        if (android.os.Build.VERSION.SDK_INT >= 33) ui.clearCache()
        return ui.windows.asSequence().mapNotNull { it.root }
            .flatMap { it.findAccessibilityNodeInfosByViewId("${context.packageName}:id/$id").asSequence() }
            .firstOrNull { it.isVisibleToUser }
    }

    private fun await(message: String, timeout: Long = 120_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (!condition()) {
            check(SystemClock.uptimeMillis() < deadline) { message }
            SystemClock.sleep(40)
        }
    }

    private fun tap(bounds: Rect) {
        val down = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                bounds.exactCenterX(), bounds.exactCenterY(), 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            check(ui.injectInputEvent(event, false))
            event.recycle()
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(20)
        }
    }

    private fun resultBounds(): Rect {
        var result = Rect()
        instrumentation.runOnMainSync {
            val label = WindowInspector.getGlobalWindowViews().asSequence()
                .mapNotNull { it.findViewById<View>(R.id.gemma_floating_result) }.first { it.isShown }
            fun rect(view: View): Rect {
                val location = IntArray(2).also(view::getLocationOnScreen)
                return Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
            }
            result = rect(label).apply { intersect(rect(label.parent as View)) }
        }
        return result
    }

    @Test(timeout = 1_800_000)
    fun realModelImeBenchmarkAndReplacement() {
        assumeTrue("Use the isolated QA application ID", context.packageName == "com.kazumaproject.gemmafloatingqa")
        val model = File(requireNotNull(context.getExternalFilesDir(null)), "gemma/gemma-4-E2B-it.litertlm")
        assumeTrue("Install the pinned Gemma 4 E2B model before testing", model.isFile && model.length() == 2_588_147_712L)
        val oldIme = shell("settings get secure default_input_method")
        val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        preferences.edit().clear()
            .putBoolean("gemma_translation_enable_preference", true)
            .putString("gemma_translation_model_path_preference", model.absolutePath)
            .putString("gemma_translation_backend_preference", "cpu")
            .putBoolean(GemmaFloatingSettings.ENABLED, true)
            .putBoolean("save_last_used_keyboard", false)
            .putBoolean("allow_fullscreen_mode_preference", false)
            .putBoolean("learn_dictionary_preference", false)
            .putBoolean("live_conversion_preference", false)
            .putBoolean("qwerty_english_direct_input_preference", false)
            .putBoolean("landscape_force_qwerty_preference", false)
            .putString("keyboard_order_preference", "[\"ROMAJI\"]").commit()
        val settings = GemmaFloatingSettings(preferences)
        settings.ensureDefaults()
        settings.save(null, "Uppercase", "Convert every Latin letter to uppercase. Preserve all other characters. Return only the converted text.")
        val uppercase = settings.rules.last()
        val report = File(context.getExternalFilesDir(null), "gemma-floating-benchmark.jsonl")
        report.writeText("")
        AppPreference.init(context)
        val loadProbe = GemmaTranslationManager(context, AppPreference, GemmaRuntimeClient(context))
        runBlocking {
            val started = SystemClock.elapsedRealtime()
            assertTrue("Real Gemma CPU initialization failed", loadProbe.initializeIfEnabled(forceReload = true))
            report.appendText(JSONObject().put("initializationMs", SystemClock.elapsedRealtime() - started)
                .put("backend", "CPU").toString() + "\n")
            for (source in listOf("こんにちは", "ありがとう", "おやすみ")) {
                val inferenceStarted = SystemClock.elapsedRealtime()
                val result = loadProbe.generateFloatingText(source, settings.rules.first().instruction)
                report.appendText(JSONObject().put("managerProbe", true).put("source", source).put("result", result)
                    .put("inferenceMs", SystemClock.elapsedRealtime() - inferenceStarted).toString() + "\n")
            }
            val preview = async {
                runCatching { loadProbe.generateFloatingText("nihongonyuuryoku", settings.rules[1].instruction) }
            }
            delay(100)
            val foreground = loadProbe.translate("こんにちは")
            assertTrue("Foreground translation was cancelled by a preview", foreground.contains("hello", true))
            preview.await()
            assertFalse(loadProbe.generateFloatingText("ありがとう", settings.rules.first().instruction).isNullOrBlank())
            report.appendText(JSONObject().put("foregroundPriorityPassed", true).toString() + "\n")
            // The IME can already be bound when instrumentation starts. Do not close its
            // shared runtime through this separate measurement client before the UI trials.
        }
        ui.serviceInfo = ui.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        val suites = listOf(
            Triple("english", "ROMAJI", listOf("konnichiha", "arigatou", "oyasumi", "ashita", "nihongo", "tenki", "kaigi", "denwa", "shigoto", "yasumi")),
            Triple("modeless", "QWERTY", listOf("konnichiwa", "arigatou", "nihongo", "kyouhagoogledekensaku", "ashitanomeetingwotsuika", "hello", "keyboard", "chatgptwotsukau", "markdownhenkan", "Tokyo")),
            Triple(uppercase.id, "QWERTY", listOf("hello", "keyboard", "markdown", "android", "gemma", "floating", "candidate", "project", "settings", "typing")),
        )
        val japaneseSources = listOf("こんいちは", "ありがとう", "おやすみ", "あした", "にほんご", "てんき", "かいぎ", "でんわ", "しごと", "やすみ")
        try {
            ui.setRotation(UiAutomation.ROTATION_FREEZE_0)
            shell("input keyevent WAKEUP"); shell("wm dismiss-keyguard")
            shell("ime enable $ime"); shell("ime set $ime")
            ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { scenario ->
                scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                await("Portrait configuration did not apply") {
                    var portrait = false
                    scenario.onActivity { portrait = it.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
                    portrait
                }
                var host: FastInputHostActivity? = null
                scenario.onActivity { host = it }
                fun editor(action: (FastInputHostActivity) -> Unit) = instrumentation.runOnMainSync { action(requireNotNull(host)) }
                editor { assertEquals(android.content.res.Configuration.ORIENTATION_PORTRAIT, it.resources.configuration.orientation) }
                val coldStart = SystemClock.elapsedRealtime()
                for ((ruleId, keyboard, examples) in suites) {
                    preferences.edit().putString("keyboard_order_preference", "[\"$keyboard\"]").commit()
                    settings.select(ruleId)
                    val args = InstrumentationRegistry.getArguments()
                    repeat(args.getString("repeats")?.toIntOrNull() ?: 3) { repeatIndex ->
                        for (typed in examples.take(args.getString("examples")?.toIntOrNull() ?: 10)) {
                            editor { it.editText.setText("PREFIX "); it.restartEditorInput(false) }
                            await("QWERTY keyboard did not appear") { node("key_a") != null }
                            SystemClock.sleep(350)
                            val keyBounds = ('a'..'z').associateWith { letter ->
                                Rect().also { requireNotNull(node("key_$letter")).getBoundsInScreen(it) }
                            }
                            val typingInterval = if (repeatIndex == 0 && typed == examples.first()) 400L else 40L
                            var inputStopped = 0L
                            for ((index, letter) in typed.lowercase().withIndex()) {
                                tap(requireNotNull(keyBounds[letter]))
                                inputStopped = SystemClock.elapsedRealtime()
                                if (index < typed.lastIndex) SystemClock.sleep(typingInterval)
                            }
                            val expectedSource = if (ruleId == "english") japaneseSources[examples.indexOf(typed)] else typed.lowercase()
                            await("Last key was not applied to the composing editor") {
                                var applied = false
                                editor { applied = it.editText.text.toString() == "PREFIX $expectedSource" }
                                applied
                            }
                            await("Gemma floating result did not appear for $ruleId / $typed") { node("gemma_floating_result")?.text?.isNotBlank() == true }
                            val shownAt = SystemClock.elapsedRealtime()
                            val resultNode = requireNotNull(node("gemma_floating_result"))
                            val result = resultNode.text.toString()
                            if (repeatIndex == 0 && typed == examples.first()) ui.takeScreenshot()?.let { bitmap ->
                                File(context.getExternalFilesDir(null), "gemma-floating-$ruleId.png").outputStream().use {
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                                }
                                bitmap.recycle()
                            }
                            var source = ""
                            editor { source = it.editText.text.toString().removePrefix("PREFIX ") }
                            tap(resultBounds())
                            await("Result tap did not replace composing text") {
                                var valid = false
                                editor {
                                    valid = it.editText.text.toString() == "PREFIX $result" &&
                                        BaseInputConnection.getComposingSpanStart(it.editText.text) < 0
                                }
                                valid
                            }
                            assertNull("Result remained after commit", node("gemma_floating_result"))
                            val record = JSONObject().put("rule", ruleId).put("repeat", repeatIndex)
                                .put("typed", typed).put("source", source).put("result", result)
                                .put("typingIntervalMs", typingInterval)
                                .put("displayLatencyMs", shownAt - inputStopped)
                                .put("orientation", "portrait")
                                .put("elapsedSinceColdStartMs", shownAt - coldStart)
                                .put("replacementPassed", true)
                            report.appendText(record.toString() + "\n")
                        }
                    }
                    report.appendText(JSONObject().put("rule", ruleId)
                        .put("memory", shell("dumpsys meminfo ${context.packageName}:gemma")).toString() + "\n")
                }
                // A previously visible result must disappear when the feature is disabled.
                editor { it.restartEditorInput(true) }
                await("Keyboard missing") { node("key_a") != null }
                tap(Rect().also { requireNotNull(node("key_a")).getBoundsInScreen(it) })
                await("Result missing before disabling") { node("gemma_floating_result") != null }
                preferences.edit().putBoolean(GemmaFloatingSettings.ENABLED, false).commit()
                await("Disabled feature kept a result", 5_000) { node("gemma_floating_result") == null }
            }
        } finally {
            ui.setRotation(UiAutomation.ROTATION_UNFREEZE)
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
        }
    }
}
