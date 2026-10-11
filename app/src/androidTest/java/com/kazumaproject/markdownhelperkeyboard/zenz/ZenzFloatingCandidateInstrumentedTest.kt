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
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.GuideProfile
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
    private fun description(value: Int) = awaitNode { it.contentDescription?.toString() == ctx.getString(value) }
    private fun label() = awaitNode { it.viewIdResourceName == labelId && it.isEnabled && !it.text.isNullOrBlank() }
    private fun touch(rect: Rect, dx: Float = 0f, dy: Float = 0f) {
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            check(ui.injectInputEvent(event, true)); event.recycle()
        }
        val x = rect.exactCenterX(); val y = rect.exactCenterY()
        send(MotionEvent.ACTION_DOWN, x, y); SystemClock.sleep(60)
        if (dx != 0f || dy != 0f) { send(MotionEvent.ACTION_MOVE, x + dx, y + dy); SystemClock.sleep(100) }
        send(MotionEvent.ACTION_UP, x + dx, y + dy); SystemClock.sleep(400)
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
            if (matches) return
            SystemClock.sleep(150)
        } while (SystemClock.uptimeMillis() < until)
        error("Input host did not reach orientation $expected")
    }
    private fun typeReading() { touch(bounds(id("key_1"))); touch(bounds(id("key_2"))) }
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
                val grip = bounds(description(R.string.composing_guide_move))
                assertTrue("Default zenz panel overlaps the normal candidate strip",
                    grip.bottom <= bounds(id("suggestion_item_text_view")).top)
                touch(grip, dy = if (grip.top > 200) -60f else 60f)
                awaitUi("Move did not move the panel") { visible ->
                    visible.firstOrNull { it.contentDescription?.toString() == ctx.getString(R.string.composing_guide_move) }
                        ?.let { kotlin.math.abs(bounds(it).top - grip.top) > 10 } == true
                }
                touch(bounds(description(R.string.composing_guide_edit)))
                val beforeResize = bounds(description(R.string.composing_guide_move))
                touch(bounds(description(R.string.composing_guide_resize_right)), dx = -80f)
                val afterResize = bounds(description(R.string.composing_guide_move))
                assertTrue("Resize did not change the width", afterResize.width() < beforeResize.width())
                touch(bounds(description(R.string.composing_guide_done)))
                val saved = ComposingGuideSettings(prefs).load(false, GuideProfile.ZENZ)
                val savedGrip = bounds(description(R.string.composing_guide_move))
                capture("portrait-moved-resized")
                touch(bounds(label()))
                withEditor {
                    assertEquals(generated, it.editText.text.toString())
                    assertEquals(-1, BaseInputConnection.getComposingSpanStart(it.editText.text))
                    assertTrue(it.editText.hasFocus())
                }
                assertFalse("Committed panel remained visible", nodes().any { it.viewIdResourceName == labelId })
                withEditor { it.restartEditorInput(true) }
                typeReading(); label()
                assertEquals(saved, ComposingGuideSettings(prefs).load(false, GuideProfile.ZENZ))
                assertEquals(savedGrip, bounds(description(R.string.composing_guide_move)))

                prefs.edit().putBoolean(ComposingGuideSettings.ENABLED, true).commit()
                awaitUi("Candidate and zenz panels must coexist") { visible ->
                    visible.count { it.contentDescription?.toString() == ctx.getString(R.string.composing_guide_move) } == 2
                }
                capture("independent-panels")
                prefs.edit().putBoolean(ComposingGuideSettings.ZENZ_ENABLED, false).commit()
                awaitUi("Disabled zenz panel remained visible") { visible -> visible.none { it.viewIdResourceName == labelId } }
                assertTrue(nodes().any { it.viewIdResourceName == "${ctx.packageName}:id/suggestion_item_text_view" })
                prefs.edit().putBoolean(ComposingGuideSettings.ENABLED, false)
                    .putBoolean(ComposingGuideSettings.ZENZ_ENABLED, true).commit()
                label()
                ComposingGuideSettings.reset(prefs, GuideProfile.ZENZ)
                SystemClock.sleep(700)
                assertEquals(280f, ComposingGuideSettings(prefs).load(false, GuideProfile.ZENZ).widthDp, .01f)

                shell("settings put system user_rotation 1")
                check(ui.setRotation(android.app.UiAutomation.ROTATION_FREEZE_90))
                awaitOrientation(android.content.res.Configuration.ORIENTATION_LANDSCAPE)
                withEditor { it.restartEditorInput(true) }
                typeReading(); label()
                capture("landscape-generated")
                assertEquals(280f, ComposingGuideSettings(prefs).load(true, GuideProfile.ZENZ).widthDp, .01f)
                touch(bounds(description(R.string.composing_guide_edit)))
                val landscapeWidth = bounds(description(R.string.composing_guide_move)).width()
                touch(bounds(description(R.string.composing_guide_resize_right)), dx = -80f)
                assertTrue(bounds(description(R.string.composing_guide_move)).width() < landscapeWidth)
                touch(bounds(description(R.string.composing_guide_done)))
                assertEquals(280f, ComposingGuideSettings(prefs).load(false, GuideProfile.ZENZ).widthDp, .01f)
                capture("landscape-resized")
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
