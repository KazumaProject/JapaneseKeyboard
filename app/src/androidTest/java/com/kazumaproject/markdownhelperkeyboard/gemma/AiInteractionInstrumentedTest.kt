package com.kazumaproject.markdownhelperkeyboard.gemma

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inspector.WindowInspector
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.FastInputHostActivity
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaPromptTemplate
import com.kazumaproject.markdownhelperkeyboard.gemma.floating.GemmaFloatingSettings
import com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService
import com.kazumaproject.markdownhelperkeyboard.repository.GemmaPromptTemplateRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real IME gestures and editor selection, with externally supplied model weights. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class AiInteractionInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val ui get() = instrumentation.uiAutomation
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command))
        .bufferedReader().use { it.readText().trim() }
    private fun roots(): List<AccessibilityNodeInfo> {
        if (android.os.Build.VERSION.SDK_INT >= 33) ui.clearCache()
        return ui.windows.mapNotNull { it.root }
    }
    private fun node(id: String): AccessibilityNodeInfo? = roots().asSequence()
        .flatMap { it.findAccessibilityNodeInfosByViewId("${context.packageName}:id/$id").asSequence() }
        .firstOrNull { it.isVisibleToUser }
    private fun text(value: String): AccessibilityNodeInfo? = roots().asSequence()
        .flatMap { it.findAccessibilityNodeInfosByText(value).asSequence() }
        .firstOrNull { it.isVisibleToUser && it.text?.toString() == value }
    private fun candidate(value: String): AccessibilityNodeInfo? = roots().asSequence()
        .flatMap { it.findAccessibilityNodeInfosByViewId("${context.packageName}:id/suggestion_item_text_view").asSequence() }
        .firstOrNull { it.isVisibleToUser && it.text?.toString()?.trim() == value }
    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 120_000
        while (!condition()) {
            if (SystemClock.uptimeMillis() >= deadline) {
                capture("failure")
                error(message)
            }
            SystemClock.sleep(40)
        }
    }
    private fun touch(node: AccessibilityNodeInfo, hold: Boolean = false) {
        val bounds = Rect().also(node::getBoundsInScreen)
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                bounds.exactCenterX(), bounds.exactCenterY(), 0).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
                check(ui.injectInputEvent(this, true))
                recycle()
            }
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(if (hold) 850 else 25)
        }
    }
    private fun type(value: String) {
        for (letter in value) {
            touch(requireNotNull(node("key_$letter")))
            SystemClock.sleep(60)
        }
    }
    private fun service(): IMEService {
        var result: IMEService? = null
        instrumentation.runOnMainSync {
            val view = WindowInspector.getGlobalWindowViews().asSequence()
                .mapNotNull { it.findViewById<View>(R.id.suggestion_recycler_view) }.first { it.isShown }
            var owner = view.context
            while (owner is ContextWrapper && owner !is IMEService) owner = owner.baseContext
            result = owner as IMEService
        }
        return requireNotNull(result)
    }
    private fun capture(name: String) {
        val args = InstrumentationRegistry.getArguments()
        if (args.getString("aiScreenshots") != "true") return
        SystemClock.sleep(500)
        val prefix = File(args.getString("aiModel")!!).extension + "-" + (args.getString("aiBackend") ?: "cpu")
        val directory = File(context.getExternalFilesDir(null), "interaction-screenshots/$prefix").apply { mkdirs() }
        val bitmap = requireNotNull(ui.takeScreenshot())
        File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    @Test(timeout = 480_000)
    fun floatingSelectionActionsAndCandidateReplacementRoundTrip() {
        assumeTrue("Use the isolated QA application ID", context.packageName == "com.kazumaproject.universalaiqa")
        val args = InstrumentationRegistry.getArguments()
        val filename = args.getString("aiModel")
        assumeTrue("Supply aiModel", filename != null)
        val model = File(context.getExternalFilesDir(null), "gemma/$filename")
        assertTrue(model.isFile)
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val saved = preferences.all.toMap()
        val oldIme = shell("settings get secure default_input_method")
        var repository: GemmaPromptTemplateRepository? = null
        var inserted: GemmaPromptTemplate? = null
        try {
            preferences.edit()
                .putBoolean("gemma_translation_enable_preference", true)
                .putString("gemma_translation_model_path_preference", model.absolutePath)
                .putString("gemma_translation_backend_preference", args.getString("aiBackend") ?: "cpu")
                .putBoolean(GemmaFloatingSettings.ENABLED, true)
                .putBoolean("save_last_used_keyboard", false)
                .putBoolean("allow_fullscreen_mode_preference", false)
                .putBoolean("learn_dictionary_preference", false)
                .putBoolean("live_conversion_preference", false)
                .putBoolean("qwerty_english_direct_input_preference", false)
                .putBoolean("landscape_force_qwerty_preference", false)
                .putString("keyboard_order_preference", "[\"QWERTY\"]").commit()
            val settings = GemmaFloatingSettings(preferences)
            settings.save(null, "Uppercase QA", "Convert every Latin letter to uppercase. Return only the converted text.")
            settings.select(settings.rules.last().id)
            AppPreference.init(context)
            ui.serviceInfo = ui.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            }
            ui.setRotation(UiAutomation.ROTATION_FREEZE_0)
            shell("input keyevent WAKEUP")
            shell("wm dismiss-keyguard")
            val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
            shell("ime enable $ime")
            shell("ime set $ime")
            ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { scene ->
                fun editorText(): String { var value = ""; scene.onActivity { value = it.editText.text.toString() }; return value }
                fun reset(value: String) { scene.onActivity { it.editText.setText(value); it.restartEditorInput(false) }; await("Keyboard missing") { node("key_h") != null } }
                reset("PREFIX ")
                type("hello")
                await("Floating result missing") { node("gemma_floating_result")?.text?.toString() == "HELLO" }
                assertEquals("PREFIX hello", editorText())
                capture("06-floating-preview")
                touch(requireNotNull(node("gemma_floating_result")))
                await("Floating tap did not replace composition") { editorText() == "PREFIX HELLO" }
                await("Stale floating result remained after apply") { node("gemma_floating_result") == null }
                capture("07-floating-applied")
                val imeService = service()
                args.getString("aiExpectedBackend")?.let { expected ->
                    assertEquals(expected, (imeService.gemmaTranslationManager.loadState.value as GemmaLoadState.Ready).backend)
                }
                repository = imeService.gemmaPromptTemplateRepository
                val template = GemmaPromptTemplate(title = "大文字QA", prompt = "Convert every Latin letter to uppercase.",
                    isEnabled = true, sortOrder = -100, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
                inserted = template.copy(id = runBlocking { repository!!.insert(template) })
                reset("PREFIX hello SUFFIX")
                scene.onActivity { it.editText.setSelection(7, 12) }
                await("Selected text did not switch the candidate strip to AI actions") { text("大文字QA") != null }
                assertNotNull("Translation action missing", text(context.getString(R.string.candidate_action_translate)))
                capture("08-selection-actions")
                touch(requireNotNull(text("大文字QA")))
                await("Selection action did not replace only selected text") { editorText() == "PREFIX HELLO SUFFIX" }
                await("Selection actions remained after replacement") { text("大文字QA") == null }
                capture("09-selection-replaced")
                reset("PREFIX ")
                type("world")
                await("Ordinary candidates did not return") { candidate("world") != null }
                // Keep floating enabled: the foreground candidate action must take priority.
                touch(requireNotNull(candidate("world")), hold = true)
                await("Candidate long-press menu missing") { text("大文字QA") != null }
                capture("10-candidate-menu")
                touch(requireNotNull(text("大文字QA")))
                await("Candidate row did not show the generated replacement") { candidate("WORLD") != null }
                capture("11-candidate-replaced")
                touch(requireNotNull(candidate("WORLD")))
                await("Generated candidate did not commit exactly once") { editorText() == "PREFIX WORLD" }
                capture("12-candidate-committed")
                reset("PREFIX ")
                type("next")
                await("Ordinary composition failed after AI candidate commit") { editorText() == "PREFIX next" && candidate("next") != null }
                assertNull("Old selection menu leaked into normal composition", text("大文字QA"))
                await("Floating preview did not resume after foreground candidate action") {
                    node("gemma_floating_result")?.let {
                        it.isClickable && it.text?.toString() == "NEXT"
                    } == true
                }
                val resumedResult = requireNotNull(node("gemma_floating_result")).text.toString()
                capture("13-normal-candidates-restored")
                preferences.edit().putBoolean(GemmaFloatingSettings.ENABLED, false).commit()
                await("Disabling floating did not hide its window") { node("gemma_floating_result") == null }
                assertEquals("PREFIX next", editorText())
                preferences.edit().putBoolean(GemmaFloatingSettings.ENABLED, true).commit()
                await("Re-enabling floating did not restore the current result") {
                    node("gemma_floating_result")?.let { it.isClickable && it.text?.toString() == resumedResult } == true
                }
                assertEquals("PREFIX next", editorText())

                if (args.getString("aiFormattedText") == "true") {
                    val formattedAction = requireNotNull(inserted).copy(
                        title = "日本語・改行QA",
                        prompt = "Replace the word next with NEXT. Keep all other characters unchanged. Return only the result.",
                    )
                    runBlocking { repository!!.update(formattedAction) }
                    val source = "「こんにちは next」\n\"日本語 next\" 😀"
                    val expected = "「こんにちは NEXT」\n\"日本語 NEXT\" 😀"
                    reset("PREFIX $source SUFFIX")
                    scene.onActivity { it.editText.setSelection(7, 7 + source.length) }
                    await("Formatted text selection action missing") { text(formattedAction.title) != null }
                    capture("14-formatted-selection")
                    touch(requireNotNull(text(formattedAction.title)))
                    await("AI text lost Japanese characters, quotes, emoji, line breaks or surrounding text") {
                        editorText() == "PREFIX $expected SUFFIX"
                    }
                    capture("15-formatted-selection-replaced")
                }
            }
        } finally {
            inserted?.let { row -> runBlocking { repository?.delete(row) } }
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
            preferences.edit().clear().apply {
                saved.forEach { (key, value) -> when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is Set<*> -> @Suppress("UNCHECKED_CAST") putStringSet(key, value as Set<String>)
                } }
            }.commit()
            AppPreference.init(context)
            ui.setRotation(UiAutomation.ROTATION_UNFREEZE)
        }
    }
}
