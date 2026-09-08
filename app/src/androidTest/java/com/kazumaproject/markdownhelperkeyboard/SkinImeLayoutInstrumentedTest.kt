package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Uses the actual InputMethodService, candidate adapters and window insets. */
@RunWith(AndroidJUnit4::class)
@androidx.test.filters.SdkSuppress(minSdkVersion = 30)
class SkinImeLayoutInstrumentedTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val automation get() = ins.uiAutomation
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            result.add(node)
            for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
        }
        automation.windows.forEach { it.root?.let(::visit) }
        return result
    }

    private fun awaitKey(): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 60000
        while (SystemClock.uptimeMillis() < deadline) {
            nodes().firstOrNull { it.isVisibleToUser && it.text?.toString() == "あ" &&
                it.viewIdResourceName?.startsWith(ins.targetContext.packageName + ":id/") == true }?.let { return it }
            SystemClock.sleep(100)
        }
        val out = File(ins.targetContext.getExternalFilesDir(null), "ime-layout").apply { mkdirs() }
        automation.takeScreenshot()?.let { bitmap ->
            File(out, "missing-key.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        File(out, "missing-key.txt").writeText(nodes().joinToString("\n") { "${it.viewIdResourceName}: ${it.text} visible=${it.isVisibleToUser}" })
        error("Actual IME kana key did not appear")
    }

    private fun awaitStableLayout() {
        var previous: List<Rect>? = null
        var unchanged = 0
        val deadline = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < deadline) {
            val geometry = nodes().filter {
                it.viewIdResourceName == "android:id/inputArea" ||
                    it.viewIdResourceName == "${ins.targetContext.packageName}:id/keyboard_view"
            }.map { node ->
                node.refresh()
                Rect().also(node::getBoundsInScreen)
            }
            unchanged = if (geometry.size == 2 && geometry == previous) unchanged + 1 else 0
            if (unchanged >= 3) return
            previous = geometry
            SystemClock.sleep(200)
        }
        error("IME layout did not settle")
    }

    @Test fun captureActualImeWithEmptyAndComposingCandidates() {
        val context = ins.targetContext
        check(context.packageName.startsWith("com.kazumaproject.skinfidelity")) {
            "Run with the isolated fidelity application ID"
        }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val saved = prefs.all.toMap()
        val oldIme = shell("settings get secure default_input_method").trim()
        val target = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        check(oldIme != target) {
            "Select another IME before starting instrumentation, which restarts the target process"
        }
        val wasEnabled = shell("ime list -s").lineSequence().any { it.trim() == target }
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        val out = File(context.getExternalFilesDir(null), "ime-layout").apply { mkdirs() }
        val measurements = JSONArray()
        try {
            when (InstrumentationRegistry.getArguments().getString("rotation")) {
                "landscape" -> check(automation.setRotation(android.app.UiAutomation.ROTATION_FREEZE_90))
                "portrait" -> check(automation.setRotation(android.app.UiAutomation.ROTATION_FREEZE_0))
            }
            shell("ime enable $target")
            shell("ime set $target")
            for ((index,skin) in listOf(KeyboardSkinId.DEFAULT, KeyboardSkinId.CUPERTINO_LIGHT,
                KeyboardSkinId.CUPERTINO_DARK, KeyboardSkinId.DEFAULT).withIndex()) {
                check(prefs.edit().putString(KeyboardSkinId.PREFERENCE_KEY, skin.preferenceValue)
                    .putString("keyboard_order_preference", "[\"TENKEY\"]")
                    .putBoolean("save_last_used_keyboard", false).putBoolean("keyboard_floating_preference", false)
                    .putString("candidate_column_preference", "2")
                    .putString("candidate_column_landscape_preference", "2")
                    .putInt("candidate_view_height_dp_landscape_preference",110)
                    .putInt("candidate_view_empty_height_dp_landscape_preference",60)
                    .putInt("candidate_view_height_dp_preference",110).putInt("candidate_view_empty_height_dp_preference",60)
                    .putBoolean("shortcut_toolbar_visibility_preference",true)
                    .putBoolean("shortcut_toolbar_integrated_in_suggestion_preference",true)
                    .putBoolean("clipboard_preview_enable_preference",false).putBoolean("clipboard_history_preference",false)
                    .putBoolean("live_conversion_preference",false).putBoolean("enable_ai_conversion_zenz_preference",false)
                    .commit())
                ActivityScenario.launch<FastInputHostActivity>(Intent(context,FastInputHostActivity::class.java)).use { scenario ->
                    awaitKey(); SystemClock.sleep(1000)
                    for (phase in listOf("empty", "composing", "committed")) {
                        val composing = phase == "composing"
                        if(composing) {
                            val bounds=Rect();awaitKey().getBoundsInScreen(bounds)
                            val down=SystemClock.uptimeMillis()
                            for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                                val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,bounds.exactCenterX(),bounds.exactCenterY(),0)
                                event.source=InputDevice.SOURCE_TOUCHSCREEN
                                check(automation.injectInputEvent(event,true));event.recycle()
                                if(action==MotionEvent.ACTION_DOWN)SystemClock.sleep(30)
                            }
                            SystemClock.sleep(1200)
                        }
                        if (phase == "committed") {
                            val candidate = nodes().first { it.viewIdResourceName == "${context.packageName}:id/suggestion_item_text_view" && it.isVisibleToUser }
                            val bounds = Rect().also(candidate::getBoundsInScreen)
                            val down = SystemClock.uptimeMillis()
                            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
                                event.source = InputDevice.SOURCE_TOUCHSCREEN
                                check(automation.injectInputEvent(event, true))
                                event.recycle()
                            }
                            SystemClock.sleep(800)
                        }
                        awaitStableLayout()
                        val name="$index-${skin.preferenceValue}-$phase"
                        val snapshot=JSONArray()
                        val currentNodes = nodes()
                        fun boundsOf(id: String): Rect {
                            val node = currentNodes.first { it.viewIdResourceName == id && it.isVisibleToUser }
                            return Rect().also(node::getBoundsInScreen)
                        }
                        currentNodes.forEach { node ->
                            val bounds=Rect();node.getBoundsInScreen(bounds)
                            snapshot.put(JSONObject().put("id",node.viewIdResourceName).put("text",node.text?.toString())
                                .put("description",node.contentDescription?.toString()).put("visible",node.isVisibleToUser)
                                .put("bounds",JSONArray(listOf(bounds.left,bounds.top,bounds.right,bounds.bottom))))
                        }
                        File(out,"$name-nodes.json").writeText(snapshot.toString(2))
                        val screenshot=requireNotNull(automation.takeScreenshot())
                        File(out,"$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) }
                        val keyboardBounds = boundsOf("${context.packageName}:id/keyboard_view")
                        val inputBounds = boundsOf("android:id/inputArea")
                        val candidateBounds = currentNodes.first { it.viewIdResourceName == "${context.packageName}:id/suggestionView_parent" }
                            .let { Rect().also(it::getBoundsInScreen) }
                        var navigationInsets = android.graphics.Insets.NONE
                        scenario.onActivity { activity ->
                            navigationInsets = activity.window.decorView.rootWindowInsets
                                .getInsets(android.view.WindowInsets.Type.navigationBars())
                        }
                        val navigationInset = navigationInsets.bottom
                        val navigationBounds = Rect(0, screenshot.height - navigationInset, screenshot.width, screenshot.height)
                        check(keyboardBounds.left >= navigationInsets.left && keyboardBounds.right <= screenshot.width - navigationInsets.right) { "Keyboard overlaps side navigation: $keyboardBounds / $navigationInsets" }
                        check(keyboardBounds.bottom <= navigationBounds.top) { "Keyboard overlaps navigation: $keyboardBounds / $navigationBounds" }
                        check(candidateBounds.bottom <= keyboardBounds.top) { "Candidates overlap keys" }
                        val expectedCandidateHeight = ((if (composing) 110 else 60) * context.resources.displayMetrics.density).toInt()
                        check(kotlin.math.abs(keyboardBounds.top - inputBounds.top - expectedCandidateHeight) <= 1) {
                            "Candidate reserved space clipped: input=$inputBounds, keyboard=$keyboardBounds, expected height $expectedCandidateHeight"
                        }
                        com.kazumaproject.core.ui.skin.KeyboardSkinRegistry.find(skin)?.let { appearance ->
                            val id = if (composing) "suggestion_item_text_view" else "item_image"
                            val ink = currentNodes.first { it.viewIdResourceName == "${context.packageName}:id/$id" && it.isVisibleToUser }
                                .let { Rect().also(it::getBoundsInScreen) }
                            val expected = appearance.palette.text
                            var matchingPixels = 0
                            for (y in ink.top.coerceAtLeast(0) until ink.bottom.coerceAtMost(screenshot.height)) {
                                for (x in ink.left.coerceAtLeast(0) until ink.right.coerceAtMost(screenshot.width)) {
                                    val pixel = screenshot.getPixel(x,y)
                                    if (listOf(0,8,16).all { shift -> kotlin.math.abs(((pixel ushr shift) and 255) - ((expected ushr shift) and 255)) <= 3 }) matchingPixels++
                                }
                            }
                            check(matchingPixels > 25) { "Candidate/shortcut did not adopt ${skin.preferenceValue}: $matchingPixels matching pixels" }
                        }
                        if (composing) {
                            val candidates = currentNodes.filter { it.viewIdResourceName == "${context.packageName}:id/suggestion_item_text_view" && it.isVisibleToUser }
                                .map { Rect().also(it::getBoundsInScreen) }
                            val firstColumn = candidates.minOf { it.left }
                            check(candidates.count { kotlin.math.abs(it.left - firstColumn) <= 2 } == 2) { "Candidate column count changed" }
                        }
                        screenshot.recycle()
                        var editorText=""
                        scenario.onActivity { editorText=it.editText.text.toString() }
                        check(editorText.isNotEmpty() == (phase != "empty")){"Unexpected editor state: $editorText"}
                        measurements.put(JSONObject().put("name",name).put("editorText",editorText)
                            .put("columns",prefs.getString("candidate_column_preference",null))
                            .put("reservedCandidateHeightPx", keyboardBounds.top - inputBounds.top)
                            .put("navigationInsetPx",navigationInset).put("keyboardBottomPx",keyboardBounds.bottom)
                            .put("navigationRightInsetPx",navigationInsets.right).put("keyboardRightPx",keyboardBounds.right))
                        check(prefs.getString("candidate_column_preference",null)=="2")
                    }
                }
            }
        } finally {
            val editor=prefs.edit().clear()
            saved.forEach { (key,value) -> when(value) {
                is String -> editor.putString(key,value);is Boolean -> editor.putBoolean(key,value)
                is Int -> editor.putInt(key,value);is Long -> editor.putLong(key,value);is Float -> editor.putFloat(key,value)
                is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key,value as Set<String>)
            } };check(editor.commit())
            if(oldIme.isNotBlank() && oldIme != "null") {
                shell("ime set $oldIme")
                check(shell("settings get secure default_input_method").trim() == oldIme) { "Could not restore original IME" }
            }
            if(!wasEnabled)shell("ime disable $target")
            File(out,"measurements.json").writeText(measurements.toString(2))
        }
    }
}
