package com.kazumaproject.markdownhelperkeyboard.zenz

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.BaseInputConnection
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazumaproject.markdownhelperkeyboard.FastInputHostActivity
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.ComposingGuideSettings
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs real generation and touch dispatch on an isolated emulator. */
@RunWith(AndroidJUnit4::class)
class ZenzFloatingCandidateInstrumentedTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ui get() = ins.uiAutomation
    private val ctx get() = ins.targetContext
    private val labelId get() = "${ctx.packageName}:id/zenz_floating_candidate_text"
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command))
        .bufferedReader().use { it.readText().trim() }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            if (!node.isVisibleToUser) return
            result.add(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        ui.windows.mapNotNull { it.root }.filter { it.packageName == ctx.packageName }.forEach(::visit)
        return result.filter { it.isVisibleToUser }
    }
    private fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        android.util.Log.i("ZenzFloatingQA", "Awaiting UI node")
        val until = SystemClock.uptimeMillis() + 60_000
        do {
            nodes().firstOrNull(predicate)?.let {
                android.util.Log.i("ZenzFloatingQA", "Found ${it.viewIdResourceName}: ${it.text}")
                return it
            }
            SystemClock.sleep(150)
        } while (SystemClock.uptimeMillis() < until)
        capture("failure")
        error("Missing node: " + nodes().joinToString { "${it.viewIdResourceName}/${it.text}/${it.contentDescription}" })
    }
    private fun awaitUi(message: String, predicate: (List<AccessibilityNodeInfo>) -> Boolean) {
        val until = SystemClock.uptimeMillis() + 60_000
        do {
            if (predicate(nodes())) return
            SystemClock.sleep(150)
        } while (SystemClock.uptimeMillis() < until)
        error(message)
    }
    private fun bounds(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)
    private fun id(value: String) = awaitNode { it.viewIdResourceName == "${ctx.packageName}:id/$value" }
    private fun label() = awaitNode { it.viewIdResourceName == labelId && it.isEnabled && !it.text.isNullOrBlank() }
    private fun touch(rect: Rect, dx: Float = 0f, dy: Float = 0f, settleMillis: Long = 400, holdMillis: Long = 60) {
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            check(ui.injectInputEvent(event, true)); event.recycle()
        }
        val x = rect.exactCenterX(); val y = rect.exactCenterY()
        send(MotionEvent.ACTION_DOWN, x, y); SystemClock.sleep(holdMillis)
        if (dx != 0f || dy != 0f) { send(MotionEvent.ACTION_MOVE, x + dx, y + dy); SystemClock.sleep(100) }
        send(MotionEvent.ACTION_UP, x + dx, y + dy); SystemClock.sleep(settleMillis)
    }
    private fun withEditor(action: (FastInputHostActivity) -> Unit) {
        val until = SystemClock.uptimeMillis() + 15_000
        do {
            var found = false
            ins.runOnMainSync {
                val activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .filterIsInstance<FastInputHostActivity>().singleOrNull()
                if (activity != null) { found = true; action(activity) }
            }
            if (found) return
            SystemClock.sleep(100)
        } while (SystemClock.uptimeMillis() < until)
        error("Input host did not resume")
    }
    private fun awaitOrientation(expected: Int) {
        val until = SystemClock.uptimeMillis() + 30_000
        do {
            var matches = false
            withEditor { matches = it.resources.configuration.orientation == expected }
            val screenshot = ui.takeScreenshot()
            val displayMatches = screenshot?.let {
                val portrait = it.height > it.width
                it.recycle()
                portrait == (expected == android.content.res.Configuration.ORIENTATION_PORTRAIT)
            } == true
            if (matches && displayMatches) return
            SystemClock.sleep(150)
        } while (SystemClock.uptimeMillis() < until)
        error("Input host did not reach orientation $expected")
    }
    private fun typeReading() { touch(bounds(id("key_1"))); touch(bounds(id("key_2"))) }
    private fun verifyConfiguredDelay(prefs: SharedPreferences, delayMillis: Int?) {
        if (delayMillis == null) prefs.edit().remove("zenz_debounce_time_preference").commit()
        else prefs.edit().putInt("zenz_debounce_time_preference", delayMillis).commit()
        // Keep the same IME session to exercise a live preference update.
        withEditor { it.editText.text.clear() }
        awaitUi("Old panel survived empty input") { visible -> visible.none { it.viewIdResourceName == labelId } }
        val keys = listOf(bounds(id("key_1")), bounds(id("key_2")), bounds(id("key_3")))
        val expected = delayMillis ?: 300
        var finalInputAt = 0L
        keys.forEach { key ->
            finalInputAt = SystemClock.uptimeMillis()
            fun send(action: Int) {
                val event = MotionEvent.obtain(finalInputAt, SystemClock.uptimeMillis(), action,
                    key.exactCenterX(), key.exactCenterY(), 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                // Queue the short burst without waiting for each frame or accessibility idle.
                check(ui.injectInputEvent(event, false))
                event.recycle()
            }
            send(MotionEvent.ACTION_DOWN)
            SystemClock.sleep(30)
            send(MotionEvent.ACTION_UP)
            SystemClock.sleep(10)
        }
        val mounted = shell("dumpsys window windows").contains("Zenz floating result")
        // Accessibility queries may wait for UI idleness longer than the debounce.
        // Use WindowManager registration and check the sampling time after the query.
        if (SystemClock.uptimeMillis() - finalInputAt < expected) {
            assertFalse("Panel appeared during debounce ($expected ms)", mounted)
        }
        val deadline = SystemClock.uptimeMillis() + 60_000
        while (!shell("dumpsys window windows").contains("Zenz floating result")) {
            check(SystemClock.uptimeMillis() < deadline) { "Floating window never appeared" }
            SystemClock.sleep(20)
        }
        val elapsed = SystemClock.uptimeMillis() - finalInputAt
        val first = awaitNode { it.viewIdResourceName == labelId }
        assertTrue("Panel appeared before configured delay: $elapsed < $expected", elapsed >= expected)
        if (first.text?.toString() == "…") assertFalse(first.isEnabled)
        label()
        android.util.Log.i("ZenzFloatingQA", "Configured delay=$delayMillis, first observed panel after ${elapsed}ms")
        capture("delay-${delayMillis ?: "default"}")
    }

    private fun capture(name: String) {
        val directory = File(ctx.filesDir, "zenz-floating-qa").apply { mkdirs() }
        ui.takeScreenshot()?.let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        File(directory, "$name.txt").writeText(nodes().joinToString("\n") { "${it.viewIdResourceName} ${it.text} ${it.contentDescription} ${bounds(it)}" })
    }

    @Test(timeout = 300_000) fun generationSelectionGeometryAndIndependentCandidatePanel() {
        assumeTrue(AppVariantConfig.hasZenz)
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val original = prefs.all.toMap()
        val oldIme = shell("settings get secure default_input_method")
        val oldHardware = shell("settings get secure show_ime_with_hard_keyboard")
        val oldRotation = shell("settings get system user_rotation")
        val oldAutoRotation = shell("settings get system accelerometer_rotation")
        val ime = "${ctx.packageName}/.ime_service.IMEService"
        ui.serviceInfo = ui.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        try {
            shell("input keyevent WAKEUP"); shell("wm dismiss-keyguard")
            shell("settings put secure show_ime_with_hard_keyboard 1")
            shell("settings put system accelerometer_rotation 0")
            shell("settings put system user_rotation 0")
            check(ui.setRotation(android.app.UiAutomation.ROTATION_FREEZE_0))
            check(prefs.edit().clear()
                .putString("keyboard_order_preference", "[\"TENKEY\"]")
                .putBoolean("save_last_used_keyboard", false)
                .putBoolean("keyboard_floating_preference", false)
                .putBoolean("candidate_tab_visibility_preference", false)
                .putBoolean("clipboard_preview_enable_preference", false)
                .putBoolean("live_conversion_preference", false)
                .putBoolean("learn_dictionary_preference", false)
                .putBoolean("flick_input_only_preference", true)
                .putBoolean("tenkey_kana_english_qwerty_preference", false)
                .putBoolean("landscape_force_qwerty_preference", false)
                .putBoolean("landscape_force_qwerty_romaji_preference", false)
                .putInt("candidate_view_height_dp_preference", 100)
                .putInt("candidate_view_empty_height_dp_preference", 100)
                .putInt("keyboard_height_preference", 220)
                .putInt("keyboard_height_landscape_preference", 140)
                .putInt("zenz_debounce_time_preference", 500)
                .putBoolean("enable_ai_conversion_zenz_preference", true)
                .putBoolean("enable_ai_conversion_zenzai_preference", false)
                .putBoolean("enable_zenz_rerank_preference", false)
                .putBoolean(ComposingGuideSettings.ZENZ_ENABLED, true).commit())
            assertTrue(shell("ime enable $ime").contains("enabled"))
            assertTrue(shell("ime set $ime").contains("selected"))
            // Start asynchronously: IME animation callbacks need not become idle to test input.
            ctx.startActivity(Intent(ctx, FastInputHostActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            run {
                withEditor { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                awaitOrientation(android.content.res.Configuration.ORIENTATION_PORTRAIT)
                withEditor { it.restartEditorInput(true) }
                id("key_1")
                typeReading()
                val loading = id("zenz_floating_candidate_text")
                if (loading.text?.toString() == ctx.getString(R.string.zenz_floating_loading)) {
                    assertFalse("Loading state must not be selectable", loading.isEnabled)
                }
                val candidate = label()
                val generated = candidate.text.toString()
                assertTrue("Floating panel must not include a zenz title",
                    nodes().none { it.text?.toString()?.equals("zenz", ignoreCase = true) == true })
                capture("portrait-generated")
                val panelBounds = bounds(candidate)
                val stripBounds = bounds(id("suggestion_item_text_view"))
                assertTrue("Zenz panel must be above the normal candidate strip", panelBounds.bottom < stripBounds.top)
                assertTrue("Move handle must be absent", nodes().none {
                    it.contentDescription?.toString() == ctx.getString(R.string.composing_guide_move)
                })
                touch(bounds(label()))
                withEditor {
                    assertEquals(generated, it.editText.text.toString())
                    assertEquals(-1, BaseInputConnection.getComposingSpanStart(it.editText.text))
                    assertTrue(it.editText.hasFocus())
                }
                assertFalse("Committed panel remained visible", nodes().any { it.viewIdResourceName == labelId })
                for (delay in listOf(0, 300, 500, null)) verifyConfiguredDelay(prefs, delay)
                withEditor { it.restartEditorInput(true) }
                typeReading(); label()
                prefs.edit().putBoolean(ComposingGuideSettings.ENABLED, true).commit()
                awaitUi("Candidate and zenz panels must coexist") { visible ->
                    visible.any { it.viewIdResourceName == labelId } &&
                        visible.count { it.contentDescription?.toString() == ctx.getString(R.string.composing_guide_move) } == 1
                }
                capture("independent-panels")
                prefs.edit().putBoolean(ComposingGuideSettings.ZENZ_ENABLED, false).commit()
                awaitUi("Disabled zenz panel remained visible") { visible -> visible.none { it.viewIdResourceName == labelId } }
                assertTrue(nodes().any { it.viewIdResourceName == "${ctx.packageName}:id/suggestion_item_text_view" })
                prefs.edit().putBoolean(ComposingGuideSettings.ENABLED, false)
                    .putBoolean(ComposingGuideSettings.ZENZ_ENABLED, true).commit()
                label()
                shell("settings put system user_rotation 1")
                check(ui.setRotation(android.app.UiAutomation.ROTATION_FREEZE_90))
                withEditor { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                awaitOrientation(android.content.res.Configuration.ORIENTATION_LANDSCAPE)
                withEditor { it.restartEditorInput(true) }
                typeReading(); label()
                capture("landscape-generated")
                assertTrue("Landscape panel must be above candidates",
                    bounds(label()).bottom < bounds(id("suggestion_item_text_view")).top)
                prefs.edit().putBoolean("keyboard_floating_preference", true).commit()
                withEditor { it.restartEditorInput(true) }
                typeReading(); label()
                capture("floating-keyboard")
                shell("input keyevent BACK")
                awaitUi("Hidden IME left a floating window") { visible -> visible.none { it.viewIdResourceName == labelId } }
            }
        } catch (failure: Throwable) {
            runCatching { capture("failure") }
            throw failure
        } finally {
            ins.runOnMainSync {
                androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .filterIsInstance<FastInputHostActivity>().forEach { it.finish() }
            }
            restore(prefs, original)
            if (oldIme.isNotEmpty() && oldIme != "null") shell("ime set $oldIme")
            if (oldHardware == "null") shell("settings delete secure show_ime_with_hard_keyboard")
            else shell("settings put secure show_ime_with_hard_keyboard $oldHardware")
            ui.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE)
            shell("settings put system user_rotation $oldRotation")
            shell("settings put system accelerometer_rotation $oldAutoRotation")
        }
    }

    @Test(timeout = 120_000) fun manualFloatingRequestBypassesTheAutomaticWait() {
        assumeTrue(AppVariantConfig.hasZenz)
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val original = prefs.all.toMap()
        val oldIme = shell("settings get secure default_input_method")
        val ime = "${ctx.packageName}/.ime_service.IMEService"
        ui.serviceInfo = ui.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        try {
            check(prefs.edit().clear()
                .putString("keyboard_order_preference", "[\"TENKEY\"]")
                .putBoolean("save_last_used_keyboard", false)
                .putBoolean("keyboard_floating_preference", false)
                .putBoolean("candidate_tab_visibility_preference", false)
                .putBoolean("flick_input_only_preference", true)
                .putBoolean("live_conversion_preference", false)
                .putBoolean("learn_dictionary_preference", false)
                .putInt("keyboard_height_preference", 220)
                .putInt("candidate_view_height_dp_preference", 100)
                .putInt("candidate_view_empty_height_dp_preference", 100)
                .putBoolean("enable_ai_conversion_zenz_preference", true)
                .putBoolean("conversion_key_long_press_ai_conversion_preference", true)
                .putBoolean(ComposingGuideSettings.ZENZ_ENABLED, true)
                // A deliberately long test delay separates manual dispatch from auto dispatch,
                // even on a slow emulator. No timer is added to the display implementation.
                .putInt("zenz_debounce_time_preference", 60_000)
                .putInt("long_press_timeout_preference", 100).commit())
            shell("ime enable $ime")
            shell("ime set $ime")
            ctx.startActivity(Intent(ctx, FastInputHostActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            withEditor { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            awaitOrientation(android.content.res.Configuration.ORIENTATION_PORTRAIT)
            withEditor { it.restartEditorInput(true) }
            val firstKey = bounds(id("key_1"))
            val secondKey = bounds(id("key_2"))
            val conversionKey = bounds(id("key_space"))
            val typedAt = SystemClock.uptimeMillis()
            touch(firstKey, settleMillis = 0)
            touch(secondKey, settleMillis = 0)
            assertFalse(shell("dumpsys window windows").contains("Zenz floating result"))
            touch(conversionKey, settleMillis = 0, holdMillis = 650)
            label()
            assertTrue("Manual generation waited for the automatic debounce",
                SystemClock.uptimeMillis() - typedAt < 60_000)
            capture("manual-generated")
        } finally {
            ins.runOnMainSync {
                androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .filterIsInstance<FastInputHostActivity>().forEach { it.finish() }
            }
            restore(prefs, original)
            if (oldIme.isNotEmpty() && oldIme != "null") shell("ime set $oldIme")
        }
    }

    @Test(timeout = 600_000)
    fun correctionSettingsAutomaticallySelectTheMinimumNBestReading() {
        assumeTrue(AppVariantConfig.hasZenz && android.os.Build.VERSION.SDK_INT >= 29)
        assumeTrue(InstrumentationRegistry.getArguments().getString("zenzCorrectionImeProbe") == "true")
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val original = prefs.all.toMap()
        val oldIme = shell("settings get secure default_input_method")
        val oldFlags = ui.serviceInfo.flags
        ui.serviceInfo = ui.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        val ime = "${ctx.packageName}/.ime_service.IMEService"
        val report = StringBuilder()
        var sawCorrectedMinimum = false
        var sawFlickCorrectedMinimum = false
        var sawDuplicate = false
        var sawFloating = false
        try {
            shell("input keyevent WAKEUP"); shell("wm dismiss-keyguard")
            prefs.edit().clear().putString("keyboard_order_preference", "[\"TENKEY\"]")
                .putBoolean("save_last_used_keyboard", false)
                .putBoolean("candidate_tab_visibility_preference", false)
                .putBoolean("learn_dictionary_preference", false)
                .putBoolean("live_conversion_preference", false)
                .putBoolean("enable_ai_conversion_zenz_preference", true)
                .putInt("zenz_debounce_time_preference", 0)
                .putBoolean(ComposingGuideSettings.ZENZ_ENABLED, true).commit()
            val enableResult = shell("ime enable $ime")
            assertTrue(enableResult, enableResult.contains("enabled"))
            val selectResult = shell("ime set $ime")
            assertTrue(selectResult, selectResult.contains("selected"))
            ctx.startActivity(Intent(ctx, FastInputHostActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            for (oldValue in listOf(false, true)) for (enabled in listOf(false, true)) {
                prefs.edit().putBoolean("enable_ai_conversion_zenzai_preference", oldValue)
                    .putBoolean("omission_search_preference", enabled)
                    .putBoolean("enable_typo_correction_japanese_flick_keyboard_preference", enabled).commit()
                for (reading in listOf("かつこう", "とえけい", "あらがとう")) {
                    // Zero is a supported user setting; retain the default for the other fixtures.
                    val flickOffset = if (reading == "あらがとう") 0 else 3000
                    prefs.edit().putInt("enable_typo_correction_japanese_flick_keyboard_offset_score_preference", flickOffset).commit()
                    withEditor { it.restartEditorInput(true) }
                    id("key_1")
                    SystemClock.sleep(400)
                    val service = currentImeService()
                    ins.runOnMainSync {
                        @Suppress("UNCHECKED_CAST")
                        val input = field(service, "_inputString") as kotlinx.coroutines.flow.MutableStateFlow<String>
                        input.value = reading
                    }
                    // A completed generation can intentionally have no floating window.
                    val generated = awaitCompletedResult(service, reading)
                    val snapshot = (field(service, "zenzReadingSnapshot") as kotlinx.coroutines.flow.StateFlow<*>).value!!
                    val best = field(snapshot, "bestCandidate") as com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
                    val expected = if (enabled && best.correctionMask != 0) best.yomi ?: reading else reading
                    if (enabled && best.correctionMask != 0) sawCorrectedMinimum = true
                    if (enabled && (best.correctionMask and
                            com.kazumaproject.markdownhelperkeyboard.converter.candidate.ConversionCorrection.JAPANESE_FLICK) != 0) {
                        sawFlickCorrectedMinimum = true
                    }
                    assertEquals(expected, generated.requestInput)
                    if (generated.duplicate) sawDuplicate = true else sawFloating = true
                    val candidate = candidateToCommit(generated)
                    report.appendLine("old=$oldValue,enabled=$enabled,flickOffset=$flickOffset,input=$reading,score=${best.score},mask=${best.correctionMask},zenzInput=$expected,result=${generated.text},firstLocal=${generated.firstLocalText},floating=${!generated.duplicate}")
                    File(ctx.filesDir, "zenz-correction-ime.txt").writeText(report.toString())
                    touch(bounds(candidate))
                    withEditor { assertEquals(generated.text, it.editText.text.toString()) }
                }
            }
            assertTrue("The fixture must exercise an actually corrected minimum", sawCorrectedMinimum)
            assertTrue("The fixture must exercise a selected Japanese flick correction", sawFlickCorrectedMinimum)
            assertTrue("The fixtures must exercise duplicate suppression", sawDuplicate)
            assertTrue("The fixtures must exercise floating candidate commits", sawFloating)
        } finally {
            ins.runOnMainSync {
                androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .filterIsInstance<FastInputHostActivity>().forEach { it.finish() }
            }
            restore(prefs, original)
            ui.serviceInfo = ui.serviceInfo.apply { flags = oldFlags }
            if (oldIme.isNotEmpty() && oldIme != "null") shell("ime set $oldIme")
        }
    }

    // Keep these probe helpers with measureFloatingResultLatency when copying to the baseline.
    private fun field(owner: Any, name: String): Any? =
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)

    private fun currentImeService(): com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService {
        lateinit var service: com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService
        ins.runOnMainSync {
            val keyId = ctx.resources.getIdentifier("key_1", "id", ctx.packageName)
            val contexts = android.view.inspector.WindowInspector.getGlobalWindowViews().mapNotNull {
                it.findViewById<android.view.View>(keyId)?.context
            }
            service = contexts.mapNotNull { initial ->
                var current: android.content.Context? = initial
                val seen = mutableSetOf<android.content.Context>()
                while (current != null && seen.add(current)) {
                    if (current is com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService) return@mapNotNull current
                    current = (current as? android.content.ContextWrapper)?.baseContext
                }
                null
            }.first()
        }
        return service
    }

    private data class CompletedResult(val requestInput: String, val text: String, val firstLocalText: String) {
        val duplicate get() = text == firstLocalText
    }

    /** Read on the main thread, after both generation and the local candidate query complete. */
    private fun completedResult(service: Any, reading: String): CompletedResult? {
        val state = (field(service, "_zenzLiveSlotState") as kotlinx.coroutines.flow.StateFlow<*>).value ?: return null
        if (field(state, "displayInput") != reading || field(state, "isLoading") != false ||
            field(service, "zenzLiveSnapshotDisplayInput") != reading) return null
        val candidate = field(state, "candidate") as? com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate ?: return null
        val local = (field(service, "zenzLiveLocalCandidatesSnapshot") as List<*>)
            .firstOrNull() as? com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate ?: return null
        val commit = service.javaClass.getDeclaredMethod("getCandidateCommitString", candidate.javaClass)
            .apply { isAccessible = true }
        val text = commit.invoke(service, candidate) as String
        if (text.isEmpty()) return null
        return CompletedResult(field(state, "requestInput") as String, text, commit.invoke(service, local) as String)
    }

    private fun awaitCompletedResult(service: Any, reading: String): CompletedResult {
        val deadline = SystemClock.uptimeMillis() + 60_000
        do {
            var result: CompletedResult? = null
            ins.runOnMainSync { result = completedResult(service, reading) }
            result?.let { return it }
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        error("Generation did not complete for $reading")
    }

    private fun candidateToCommit(result: CompletedResult): AccessibilityNodeInfo {
        if (result.duplicate) {
            awaitUi("Duplicate result must not show a floating candidate") { visible ->
                visible.none { it.viewIdResourceName == labelId }
            }
            return awaitNode {
                it.viewIdResourceName == "${ctx.packageName}:id/suggestion_item_text_view" &&
                    it.text?.toString()?.trim() == result.text
            }
        }
        val candidate = label()
        assertEquals(result.text, candidate.text.toString())
        assertEquals(1, nodes().count { it.viewIdResourceName == labelId })
        return candidate
    }

    @Test(timeout = 900_000)
    fun measureFloatingResultLatency() {
        assumeTrue(AppVariantConfig.hasZenz && android.os.Build.VERSION.SDK_INT >= 29)
        assumeTrue(InstrumentationRegistry.getArguments().getString("zenzFloatingPerf") == "true")
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val original = prefs.all.toMap()
        val oldIme = shell("settings get secure default_input_method")
        val ime = "${ctx.packageName}/.ime_service.IMEService"
        val report = StringBuilder()
        val oldFlags = ui.serviceInfo.flags
        ui.serviceInfo = ui.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        try {
            shell("input keyevent WAKEUP")
            shell("wm dismiss-keyguard")
            prefs.edit().clear()
                .putString("keyboard_order_preference", "[\"TENKEY\"]")
                .putBoolean("save_last_used_keyboard", false)
                .putBoolean("candidate_tab_visibility_preference", false)
                .putBoolean("learn_dictionary_preference", false)
                .putBoolean("flick_input_only_preference", true)
                .putBoolean("tenkey_kana_english_qwerty_preference", false)
                .putBoolean("omission_search_preference", true)
                .putBoolean("enable_ai_conversion_zenz_preference", true)
                .putBoolean("enable_ai_conversion_zenzai_preference", false)
                .putBoolean("enable_zenz_rerank_preference", false)
                .putBoolean("keyboard_floating_preference", false)
                .putBoolean("live_conversion_preference", false)
                .putBoolean(ComposingGuideSettings.ZENZ_ENABLED, true).commit()
            val enableResult = shell("ime enable $ime")
            assertTrue(enableResult, enableResult.contains("enabled"))
            val selectResult = shell("ime set $ime")
            assertTrue(selectResult, selectResult.contains("selected"))
            ctx.startActivity(Intent(ctx, FastInputHostActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            for (debounce in listOf(0, 300)) {
                prefs.edit().putInt("zenz_debounce_time_preference", debounce).commit()
                repeat(23) { iteration ->
                    withEditor { it.restartEditorInput(true) }
                    touch(bounds(id("key_1")))
                    val service = currentImeService()
                    val done = java.util.concurrent.CountDownLatch(1)
                    val started = java.util.concurrent.atomic.AtomicLong(0)
                    val drawn = java.util.concurrent.atomic.AtomicLong(0)
                    val suppressed = java.util.concurrent.atomic.AtomicReference<CompletedResult?>(null)
                    val observers = mutableMapOf<android.view.View, android.view.ViewTreeObserver.OnDrawListener>()
                    lateinit var frame: android.view.Choreographer.FrameCallback
                    val choreographer = arrayOfNulls<android.view.Choreographer>(1)
                    ins.runOnMainSync {
                        choreographer[0] = android.view.Choreographer.getInstance()
                        frame = android.view.Choreographer.FrameCallback {
                            val result = completedResult(service, "あか")
                            if (started.get() != 0L && result?.duplicate == true) {
                                suppressed.set(result)
                                done.countDown()
                            }
                            for (root in android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                                if (root !in observers) {
                                    val listener = android.view.ViewTreeObserver.OnDrawListener {
                                        val label = root.findViewById<android.widget.TextView>(R.id.zenz_floating_candidate_text)
                                        if (started.get() != 0L && label?.isShown == true && label.isEnabled &&
                                            !label.text.isNullOrBlank() && label.text.toString() != ctx.getString(R.string.zenz_floating_loading)) {
                                            if (drawn.compareAndSet(0, SystemClock.elapsedRealtimeNanos())) done.countDown()
                                        }
                                    }
                                    observers[root] = listener
                                    root.viewTreeObserver.addOnDrawListener(listener)
                                }
                            }
                            if (done.count != 0L) choreographer[0]!!.postFrameCallback(frame)
                        }
                        choreographer[0]!!.postFrameCallback(frame)
                    }
                    try {
                        val key = bounds(id("key_2"))
                        val down = SystemClock.uptimeMillis()
                        fun send(action: Int) {
                            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, key.exactCenterX(), key.exactCenterY(), 0)
                            event.source = InputDevice.SOURCE_TOUCHSCREEN
                            check(ui.injectInputEvent(event, true)); event.recycle()
                        }
                        send(MotionEvent.ACTION_DOWN)
                        SystemClock.sleep(30)
                        started.set(SystemClock.elapsedRealtimeNanos())
                        send(MotionEvent.ACTION_UP)
                        assertTrue("Neither a floating draw nor a completed duplicate result was observed",
                            done.await(90, java.util.concurrent.TimeUnit.SECONDS))
                        val result = suppressed.get() ?: awaitCompletedResult(service, "あか")
                        if (result.duplicate) {
                            // Suppression is not a draw latency sample.
                            report.appendLine("floating-suppressed,debounceMs=$debounce,warmup=${iteration<3},result=${result.text},firstLocal=${result.firstLocalText}")
                        } else {
                            val elapsed = drawn.get()-started.get()
                            assertTrue("Missing floating draw timestamp", drawn.get() != 0L)
                            report.appendLine("floating,debounceMs=$debounce,warmup=${iteration<3},ns=$elapsed")
                        }
                        File(ctx.filesDir, "zenz-floating-perf.txt").writeText(report.toString())
                        if (result.duplicate) {
                            assertFalse(
                                "Duplicate result must not create a floating window",
                                shell("dumpsys window windows").contains("Zenz floating result"),
                            )
                            withEditor { it.editText.text.clear() }
                        } else {
                            val candidate = candidateToCommit(result)
                            touch(bounds(candidate))
                            withEditor { assertEquals(result.text, it.editText.text.toString()) }
                        }
                    } finally {
                        ins.runOnMainSync {
                            choreographer[0]!!.removeFrameCallback(frame)
                            observers.forEach { (root, listener) ->
                                if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnDrawListener(listener)
                            }
                        }
                    }
                }
            }
        } finally {
            ins.runOnMainSync {
                androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .filterIsInstance<FastInputHostActivity>().forEach { it.finish() }
            }
            restore(prefs, original)
            ui.serviceInfo = ui.serviceInfo.apply { flags = oldFlags }
            if (oldIme.isNotEmpty() && oldIme != "null") shell("ime set $oldIme")
        }
    }

    private fun restore(prefs: SharedPreferences, values: Map<String, *>) {
        prefs.edit().clear().apply {
            values.forEach { (key, value) -> when (value) {
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Float -> putFloat(key, value)
                is String -> putString(key, value)
                is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
            } }
        }.commit()
    }
}
