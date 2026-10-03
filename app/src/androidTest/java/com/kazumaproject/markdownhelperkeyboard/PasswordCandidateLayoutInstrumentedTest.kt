package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.text.InputType
import android.view.InputDevice
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.accessibility.AccessibilityNodeInfo
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/** Exercises the real IME layout; run only with the isolated skin-fidelity application ID. */
@RunWith(AndroidJUnit4::class)
class PasswordCandidateLayoutInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private val context get() = instrumentation.targetContext

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)
    ).bufferedReader().use { it.readText().trim() }

    private fun nodes(): List<AccessibilityNodeInfo> = buildList {
        fun visit(node: AccessibilityNodeInfo) {
            add(node)
            for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
        }
        automation.windows.forEach { it.root?.let(::visit) }
    }

    private fun awaitNode(id: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            nodes().firstOrNull { it.isVisibleToUser && it.viewIdResourceName == "${context.packageName}:id/$id" }
                ?.let { return it }
            SystemClock.sleep(100)
        }
        error("Missing IME node: $id")
    }

    private fun tap(id: String) {
        val rect = Rect().also(awaitNode(id)::getBoundsInScreen)
        tapAt(rect.exactCenterX(), rect.exactCenterY())
    }

    private fun tapNumberKey(delete: Boolean) {
        val rect = Rect().also(awaitNode("custom_layout_default")::getBoundsInScreen)
        // The built-in number layout has four columns and four rows.
        tapAt(rect.left + rect.width() * (if (delete) 7f else 1f) / 8f,
            rect.top + rect.height() * (if (delete) 5f else 1f) / 8f)
    }

    private fun tapAt(x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            check(automation.injectInputEvent(event, true))
            event.recycle()
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(30)
        }
        SystemClock.sleep(800)
    }

    @Test
    fun passwordAndOrdinaryFieldsReserveTheDisplayedCandidateGeometry() = verifyGeometry(floating = false)

    @Test
    fun floatingPasswordCandidatesStayInsideTheirPanel() = verifyGeometry(floating = true)

    private fun verifyGeometry(floating: Boolean) {
        check(context.packageName.startsWith("com.kazumaproject.skinfidelity"))
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val saved = preferences.all.toMap()
        val oldIme = shell("settings get secure default_input_method")
        val target = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        check(oldIme != target) { "Select another IME before starting instrumentation" }
        val wasEnabled = shell("ime list -s").lineSequence().any { it == target }
        val oldFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        val output = File(context.getExternalFilesDir(null),
            if (floating) "password-candidate-layout/floating" else "password-candidate-layout").apply { mkdirs() }
        val failures = mutableListOf<String>()
        val measurements = mutableListOf<String>()
        val fields = listOf(
            Triple("text", InputType.TYPE_CLASS_TEXT, "Text"),
            Triple("password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, "Secret"),
            Triple("web-password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, "Secret"),
            Triple("visible-password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, "Secret"),
            Triple("metadata-password", InputType.TYPE_CLASS_TEXT, "Password"),
            Triple("private-options-password", InputType.TYPE_CLASS_TEXT, "Memo"),
            Triple("email", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, "Email"),
            Triple("uri", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, "URL"),
            Triple("search", InputType.TYPE_CLASS_TEXT, "Search"),
            Triple("number-password", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD, "Secret")
        ).filter { !floating || it.first in listOf("text", "password") }
        try {
            shell("ime enable $target")
            shell("ime set $target")
            for (hidePasswordCandidates in listOf(false, true)) for (showTab in listOf(true, false)) {
                check(preferences.edit()
                    .putString("keyboard_order_preference", "[\"QWERTY\"]")
                    .putBoolean("save_last_used_keyboard", false)
                    .putBoolean("keyboard_floating_preference", floating)
                    .putBoolean("qwerty_english_direct_input_preference", false)
                    // Key-preview popups are independent of candidate geometry. API 24
                    // rejects their window token on the floating test surface.
                    .putBoolean("qwerty_show_popup_window_preference", false)
                    .putBoolean("hide_candidate_password_preference", hidePasswordCandidates)
                    .putBoolean("candidate_tab_visibility_preference", showTab)
                    .putString("candidate_column_preference", "1")
                    .putInt("candidate_view_height_dp_preference", 80)
                    .putInt("candidate_view_height_portrait_column_1_dp_preference", 80)
                    .putInt("candidate_view_empty_height_dp_preference", 60)
                    .putBoolean("shortcut_toolbar_visibility_preference", false)
                    .putBoolean("clipboard_preview_enable_preference", false)
                    .putBoolean("live_conversion_preference", false).commit())
                ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { scenario ->
                    for ((name, inputType, hint) in fields) {
                        val number = name == "number-password"
                        scenario.onActivity {
                            it.editText.inputType = inputType
                            it.editText.hint = hint
                            it.editText.privateImeOptions = if (name == "private-options-password") {
                                "router.field=password"
                            } else {
                                null
                            }
                            it.editText.imeOptions = if (name == "search") EditorInfo.IME_ACTION_SEARCH else EditorInfo.IME_ACTION_DONE
                            it.restartEditorInput(true)
                        }
                        awaitNode(if (number) "custom_layout_default" else "key_a")
                        SystemClock.sleep(800)
                        val suppressed = hidePasswordCandidates && name.contains("password")
                        for (phase in listOf("empty", "first", "additional", "deleted")) {
                            when (phase) {
                                "first", "additional" -> if (number) tapNumberKey(false) else tap("key_a")
                                "deleted" -> repeat(2) { if (number) tapNumberKey(true) else tap("key_delete") }
                            }
                            val active = phase in listOf("first", "additional") && !suppressed
                            if (active) awaitNode("suggestion_item_text_view")
                            val current = nodes().filter { it.isVisibleToUser }
                            fun bounds(id: String) = Rect().also { rect ->
                                current.first { it.viewIdResourceName == id }.getBoundsInScreen(rect)
                            }
                            val keyboardId = when {
                                number -> "custom_layout_default"
                                floating -> "qwerty_view_floating"
                                else -> "qwerty_view"
                            }
                            val keyboard = bounds("${context.packageName}:id/$keyboardId")
                            val expectedDp = (if (active) 80 else 60) + if (active && showTab) 36 else 0
                            val expectedPx = (expectedDp * context.resources.displayMetrics.density).toInt()
                            val label = "$name-hide=$hidePasswordCandidates-tab=$showTab-$phase"
                            val panel = if (floating) bounds("${context.packageName}:id/suggestion_recycler_view") else null
                            val measuredPx = panel?.height() ?: (keyboard.top - bounds("android:id/inputArea").top)
                            measurements.add("$label: reserved=$measuredPx expected=${if (floating) "floating-panel" else expectedPx}")
                            File(output, "measurements.txt").writeText(measurements.joinToString("\n"))
                            if (!floating && abs(measuredPx - expectedPx) > 2) failures.add(measurements.last())
                            val candidates = current.filter { it.viewIdResourceName == "${context.packageName}:id/suggestion_item_text_view" }
                            if (floating) {
                                val floatingPanel = checkNotNull(panel)
                                if (candidates.isNotEmpty() != active) failures.add("$label: unexpected candidate visibility")
                                if (floatingPanel.bottom > keyboard.top) failures.add("$label: panel overlaps keyboard")
                                candidates.forEach { node ->
                                    val rect = Rect().also(node::getBoundsInScreen)
                                    if (rect.top < floatingPanel.top || rect.bottom > floatingPanel.bottom) failures.add("$label: candidate clipped by panel")
                                }
                            }
                            val screenshot = automation.takeScreenshot()
                            screenshot?.let { bitmap ->
                                File(output, "$label.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }
            File(output, "measurements.txt").writeText(measurements.joinToString("\n"))
            assertTrue(failures.joinToString("\n"), failures.isEmpty())
        } finally {
            shell("ime set $oldIme")
            if (!wasEnabled) shell("ime disable $target")
            val editor = preferences.edit().clear()
            saved.forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            check(editor.commit())
            automation.serviceInfo = automation.serviceInfo.apply { flags = oldFlags }
        }
    }
}
