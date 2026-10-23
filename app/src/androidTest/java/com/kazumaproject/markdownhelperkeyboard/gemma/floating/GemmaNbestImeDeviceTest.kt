package com.kazumaproject.markdownhelperkeyboard.gemma.floating

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.BaseInputConnection
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.FastInputHostActivity
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Run after the opt-in comparison; uses a real differing winner from that same app/model. */
@RunWith(AndroidJUnit4::class)
class GemmaNbestImeDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val ui get() = instrumentation.uiAutomation
    private val context get() = instrumentation.targetContext

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command))
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
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                bounds.exactCenterX(), bounds.exactCenterY(), 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            try { check(ui.injectInputEvent(event, true)) } finally { event.recycle() }
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(20)
        }
    }

    private fun bounds(id: String) = Rect().also { requireNotNull(node(id)).getBoundsInScreen(it) }

    private fun stripTexts(): List<String> {
        fun texts(root: AccessibilityNodeInfo): List<String> = buildList {
            root.text?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
            for (index in 0 until root.childCount) root.getChild(index)?.let { addAll(texts(it)) }
        }
        return node("suggestion_recycler_view")?.let(::texts).orEmpty()
    }

    @Test(timeout = 600_000)
    fun realNbestSuggestionKeepsTheStripAndCommitsOnlyOnTap() {
        assumeTrue("Explicitly opt into real IME verification",
            InstrumentationRegistry.getArguments().getString("gemmaNbestUi") == "true")
        val report = File(context.getExternalFilesDir(null), "gemma-nbest-comparison.jsonl")
        assertTrue("Run the comparison test before this UI test", report.isFile)
        val trial = report.readLines().asSequence().map(::JSONObject).firstOrNull {
            it.optString("model") == "gemma-4-E2B-it.litertlm" &&
                it.optString("method") == GemmaNbestScoring.defaultMethod.name &&
                it.optBoolean("correct") && it.optString("normalFirst") != it.optString("selected")
        }
        assertNotNull("The chosen method did not correctly promote any alternative in the comparison", trial)
        val record = requireNotNull(trial)
        val reading = record.getString("reading")
        val prefix = record.getString("context")
        val expected = record.getString("selected")
        val romaji = mapOf("はし" to "hashi", "あつい" to "atsui", "かみ" to "kami", "きる" to "kiru",
            "あめ" to "ame", "はな" to "hana", "いし" to "ishi", "かえる" to "kaeru", "こうえん" to "kouen",
            "せいかく" to "seikaku", "しゅうかん" to "shuukan", "かいとう" to "kaitou",
            "はしをわたる" to "hashiwowataru", "はしをつかう" to "hashiwotsukau",
            "かみをきる" to "kamiwokiru", "あめがふる" to "amegafuru", "こうえんにいく" to "kouenniiku",
            "せいかくなこたえ" to "seikakunakotae",
            "いしにはなす" to "ishinihanasu", "いえにかえる" to "ienikaeru").getValue(reading)
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val backup = AppPreference.exportAllToJson()
        val previousIme = shell("settings get secure default_input_method")
        val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val model = File(context.getExternalFilesDir(null), "gemma/gemma-4-E2B-it.litertlm")
        preferences.edit().putBoolean("gemma_translation_enable_preference", true)
            .putString("gemma_translation_model_path_preference", model.absolutePath)
            .putString("gemma_translation_backend_preference", "cpu")
            .putString(GemmaFloatingSettings.MODE, GemmaFloatingMode.NBEST.value)
            .putBoolean("save_last_used_keyboard", false)
            .putBoolean("allow_fullscreen_mode_preference", false)
            .putBoolean("learn_dictionary_preference", false)
            .putBoolean("live_conversion_preference", false)
            .putBoolean("enable_ai_conversion_zenz_preference", false)
            .putBoolean("enable_zenz_rerank_preference", false)
            .putBoolean("qwerty_english_direct_input_preference", false)
            .putBoolean("landscape_force_qwerty_preference", false)
            .putString("keyboard_order_preference", "[\"ROMAJI\"]").commit()
        AppPreference.init(context)
        ui.serviceInfo = ui.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        try {
            shell("input keyevent WAKEUP")
            shell("wm dismiss-keyguard")
            shell("ime enable $ime")
            shell("ime set $ime")
            ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { scenario ->
                scenario.onActivity { it.editText.setText(prefix); it.restartEditorInput(false) }
                await("Romaji keyboard did not appear") { node("key_a") != null }
                SystemClock.sleep(350)
                for (letter in romaji) { tap(bounds("key_$letter")); SystemClock.sleep(35) }
                val stopped = SystemClock.elapsedRealtime()
                await("The reading was not entered as composing text") {
                    var valid = false
                    scenario.onActivity { valid = it.editText.text.toString() == prefix + reading }
                    valid
                }
                await("Normal conversion candidates did not appear") {
                    val texts = stripTexts()
                    record.getString("normalFirst") in texts && expected in texts
                }
                val before = stripTexts()
                await("No differing N-best suggestion was displayed") { node("gemma_floating_result")?.isClickable == true }
                val displayed = SystemClock.elapsedRealtime()
                assertEquals(expected, node("gemma_floating_result")?.text?.toString())
                assertEquals(before, stripTexts())
                scenario.onActivity {
                    assertEquals(prefix + reading, it.editText.text.toString())
                    assertTrue(BaseInputConnection.getComposingSpanStart(it.editText.text) >= 0)
                }
                tap(bounds("gemma_floating_result"))
                await("Tapping the proposal did not replace and commit the composing region") {
                    var committed = false
                    scenario.onActivity { committed = it.editText.text.toString() == prefix + expected &&
                        BaseInputConnection.getComposingSpanStart(it.editText.text) < 0 }
                    committed
                }
                assertNull(node("gemma_floating_result"))
                File(context.getExternalFilesDir(null), "gemma-nbest-ime-verification.json").writeText(JSONObject()
                    .put("reading", reading).put("context", prefix).put("result", expected)
                    .put("method", GemmaNbestScoring.defaultMethod.name).put("displayLatencyMs", displayed - stopped)
                    .put("stripUnchanged", true).put("tapCommitPassed", true).toString())
            }
        } finally {
            AppPreference.importAllFromJson(backup)
            if (previousIme.isNotBlank() && previousIme != "null") shell("ime set $previousIme")
        }
    }
}
