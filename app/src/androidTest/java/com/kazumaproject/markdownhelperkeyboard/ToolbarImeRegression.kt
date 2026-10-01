package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Instrumentation
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.Choreographer
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.BaseInputConnection
import android.view.inspector.WindowInspector
import androidx.preference.PreferenceManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Real IME regression: no ActivityScenario/global idle waits, no product hooks. */
internal class ToolbarImeRegression(private val instrumentation: Instrumentation) {
    private val context = instrumentation.targetContext
    private val automation = instrumentation.uiAutomation
    private val handler = Handler(Looper.getMainLooper())
    private val args = InstrumentationRegistry.getArguments()
    private var phase = "setup"
    private val events = JSONArray()
    private val frames = JSONArray()
    private val phases = JSONArray()
    private lateinit var output: File
    private var main: View? = null
    private var keyboard: View? = null
    private var service: IMEService? = null
    private var monitoring = false
    private var monitorFailure: Throwable? = null
    private var observer: ViewTreeObserver? = null
    private val drawListener = ViewTreeObserver.OnPreDrawListener { sample("preDraw"); true }
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!monitoring) return
            try { sample("vsync") } catch (error: Throwable) { monitorFailure = error }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun <T> onMain(action: () -> T): T {
        val result = AtomicReference<T>()
        val failure = AtomicReference<Throwable>()
        val done = CountDownLatch(1)
        handler.post {
            try { result.set(action()) } catch (error: Throwable) { failure.set(error) }
            finally { done.countDown() }
        }
        check(done.await(10, TimeUnit.SECONDS)) { "Main-thread operation timed out: $phase" }
        failure.get()?.let { throw it }
        return result.get()
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)
    ).bufferedReader().use { it.readText().trim() }

    private fun await(label: String, timeoutMs: Long = 15_000, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            monitorFailure?.let { throw it }
            if (predicate()) return
            SystemClock.sleep(25)
        }
        error("Timed out waiting for $label ($phase)")
    }

    private fun host(): FastInputHostActivity = onMain {
        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            .filterIsInstance<FastInputHostActivity>().single()
    }

    @Suppress("UNCHECKED_CAST")
    private fun roots(): List<View> {
        if (Build.VERSION.SDK_INT >= 29) return WindowInspector.getGlobalWindowViews()
        val type = Class.forName("android.view.WindowManagerGlobal")
        val manager = type.getDeclaredMethod("getInstance").invoke(null)
        return (type.getDeclaredField("mViews").apply { isAccessible = true }
            .get(manager) as List<View>).toList()
    }

    private fun findKeyboard(floating: Boolean): View? = roots().firstNotNullOfOrNull { root ->
        root.findViewById<View>(if (floating) R.id.keyboard_view_floating else R.id.keyboard_view)
            ?.takeIf { it.isShown && it.height > 0 }
    }

    private fun rect(view: View): Rect {
        val position = IntArray(2)
        view.getLocationOnScreen(position)
        return Rect(position[0], position[1], position[0] + view.width, position[1] + view.height)
    }

    private fun jsonRect(rect: Rect) = JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom))

    private fun sample(source: String) {
        val body = requireNotNull(keyboard)
        val container = requireNotNull(main)
        frames.put(JSONObject().put("uptimeMs", SystemClock.uptimeMillis())
            .put("phase", phase).put("source", source)
            .put("mainHeight", container.height).put("requestedHeight", container.layoutParams.height)
            .put("windowHeight", body.rootView.height).put("mainBounds", jsonRect(rect(container)))
            .put("keyBounds", jsonRect(rect(body)))
            .put("shown", body.isShown && body.windowVisibility == View.VISIBLE)
            .put("alpha", body.alpha).put("windowAlpha", body.rootView.alpha))
    }

    private fun mark(nextPhase: String) = onMain {
        phase = nextPhase
        events.put(JSONObject().put("uptimeMs", SystemClock.uptimeMillis()).put("phase", phase))
    }

    private fun recordPhase(activity: FastInputHostActivity) = onMain {
        val text = activity.editText.text
        phases.put(JSONObject().put("phase", phase).put("uptimeMs", SystemClock.uptimeMillis())
            .put("text", text.toString()).put("composingStart", BaseInputConnection.getComposingSpanStart(text))
            .put("mainHeight", requireNotNull(main).height)
            .put("editorImeInset", ViewCompat.getRootWindowInsets(activity.window.decorView)
                ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom)
            .put("candidateHeight", main?.findViewById<View>(R.id.suggestionView_parent)?.height)
            .put("keyboardBounds", jsonRect(rect(requireNotNull(keyboard)))))
    }

    private fun conversionActive(): Boolean = onMain {
        (IMEService::class.java.getDeclaredField("isHenkan").apply { isAccessible = true }
            .get(requireNotNull(service)) as AtomicBoolean).get()
    }

    private fun tap(id: Int) {
        val bounds = onMain {
            val key = requireNotNull(keyboard).findViewById<View>(id)
            check(key != null && key.isShown) { "Missing key $id" }
            rect(key)
        }
        val downTime = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                bounds.exactCenterX(), bounds.exactCenterY(), 0).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
            }
            try { check(automation.injectInputEvent(event, true)) { "Touch injection failed" } }
            finally { event.recycle() }
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(25)
        }
    }

    private fun capture(name: String) {
        automation.takeScreenshot()?.let { bitmap ->
            File(output, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        File(output, "$name-window.txt").writeText(shell("dumpsys window windows"))
        File(output, "$name-input-method.txt").writeText(shell("dumpsys input_method"))
    }

    fun run() {
        check(context.packageName == "com.kazumaproject.toolbarqa") {
            "Build with investigation/toolbar-flicker.init.gradle; do not use a user's app"
        }
        val mode = args.getString("toolbarMode") ?: "on"
        check(mode in listOf("on", "off", "integrated"))
        val tab = (args.getString("candidateTab") ?: "false").toBooleanStrict()
        val floating = (args.getString("floating") ?: "false").toBooleanStrict()
        val landscape = (args.getString("rotation") ?: "portrait") == "landscape"
        val toolbarHeight = (args.getString("toolbarHeight") ?: "36").toInt()
        val candidateHeight = (args.getString("candidateHeight") ?: "60").toInt()
        val cycles = (args.getString("cycles") ?: "20").toInt()
        val expectHeightChange = (args.getString("expectHeightChange") ?: "false").toBooleanStrict()
        check(toolbarHeight in 32..72 && candidateHeight in 60..80 && cycles in 1..100)
        val label = args.getString("label") ?: "${mode}-${tab}-${toolbarHeight}-${floating}-${landscape}-${candidateHeight}"
        check(label.matches(Regex("[A-Za-z0-9_-]+")))
        output = File(context.filesDir, "toolbar-regression/$label").apply { mkdirs() }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val saved = prefs.all.toMap()
        val oldIme = shell("settings get secure default_input_method")
        val oldEnabled = shell("settings get secure enabled_input_methods")
        val oldRotation = shell("settings get system user_rotation")
        val oldAuto = shell("settings get system accelerometer_rotation")
        val target = "${context.packageName}/${IMEService::class.java.name}"
        val result = JSONObject().put("device", Build.MODEL).put("api", Build.VERSION.SDK_INT)
            .put("mode", mode).put("tab", tab).put("toolbarHeightDp", toolbarHeight)
            .put("floating", floating).put("landscape", landscape)
            .put("candidateHeightDp", candidateHeight).put("emptyHeightDp", 60).put("cycles", cycles)
            .put("expectHeightChange", expectHeightChange)
        var failure: Throwable? = null
        try {
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            }
            shell("input keyevent WAKEUP")
            shell("wm dismiss-keyguard")
            shell("settings put system accelerometer_rotation 0")
            shell("settings put system user_rotation ${if (landscape) 1 else 0}")
            check(prefs.edit().clear()
                .putString("keyboard_order_preference", "[\"TENKEY\"]")
                .putBoolean("save_last_used_keyboard", false)
                .putBoolean("keyboard_floating_preference", floating)
                .putString("keyboard_skin_preference", "default")
                .putBoolean("live_conversion_preference", false)
                .putBoolean("learn_dictionary_preference", false)
                .putBoolean("flick_input_only_preference", true)
                .putBoolean("landscape_force_qwerty_preference", false)
                .putBoolean("landscape_force_qwerty_romaji_preference", false)
                .putBoolean("clipboard_preview_enable_preference", false)
                .putBoolean("clipboard_history_preference", false)
                .putString("candidate_column_preference", "1")
                .putString("candidate_column_landscape_preference", "1")
                .putInt("candidate_view_height_dp_preference", candidateHeight)
                .putInt("candidate_view_height_dp_landscape_preference", candidateHeight)
                .putInt("candidate_view_height_portrait_column_1_dp_preference", candidateHeight)
                .putInt("candidate_view_height_landscape_column_1_dp_preference", candidateHeight)
                .putInt("candidate_view_empty_height_dp_preference", 60)
                .putInt("candidate_view_empty_height_dp_landscape_preference", 60)
                .putBoolean("candidate_tab_visibility_preference", tab)
                .putBoolean("shortcut_toolbar_visibility_preference", mode != "off")
                .putBoolean("shortcut_toolbar_integrated_in_suggestion_preference", mode == "integrated")
                .putInt("shortcut_toolbar_height_dp_preference", toolbarHeight)
                .putString("keyboard_theme_mode_preference", "custom")
                .putBoolean("theme_custom", true).putBoolean("theme_default", false)
                .putInt("custom_theme_key_color_preference", 0xff000000.toInt())
                .putInt("custom_theme_special_key_color_preference", 0xff333333.toInt())
                .putInt("custom_theme_key_text_color_preference", 0xffffffff.toInt())
                .putInt("custom_theme_special_key_text_color_preference", 0xffffffff.toInt())
                .putInt("custom_theme_bg_color_preference", 0xff1c1d1e.toInt())
                .commit())
            shell("ime enable $target")
            shell("ime set $target")
            onMain { context.startActivity(Intent(context, FastInputHostActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) }
            await("host and IME", 60_000) {
                onMain {
                    val activity = ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED).filterIsInstance<FastInputHostActivity>()
                        .singleOrNull()
                    keyboard = findKeyboard(floating)
                    activity?.editText?.hasWindowFocus() == true && keyboard != null
                }
            }
            val activity = host()
            onMain {
                main = requireNotNull(keyboard).parent as View
                var ctx = requireNotNull(keyboard).context
                while (ctx is ContextWrapper && ctx !is IMEService) ctx = ctx.baseContext
                service = ctx as IMEService
            }
            SystemClock.sleep(1000)
            capture("initial")
            result.put("initialKeyboardBounds", onMain { jsonRect(rect(requireNotNull(keyboard))) })
            val initialHeight = onMain { requireNotNull(main).height }
            result.put("initialMainHeight", initialHeight)
            onMain {
                monitoring = true
                observer = requireNotNull(main).viewTreeObserver.also { it.addOnPreDrawListener(drawListener) }
                Choreographer.getInstance().postFrameCallback(frameCallback)
            }
            mark("ready")
            SystemClock.sleep(250)
            repeat(cycles) { cycle ->
                mark("cycle${cycle + 1}-input")
                tap(com.kazumaproject.tenkey.R.id.key_2)
                await("composing か") { onMain {
                    val text = activity.editText.text
                    text.toString() == "か".repeat(cycle + 1) &&
                        BaseInputConnection.getComposingSpanStart(text) >= 0
                } }
                // Wait for asynchronous candidate refresh before entering conversion.
                await("candidate refresh") { onMain {
                    IMEService::class.java.getDeclaredField("shortcutToolbarHiddenForCandidates")
                        .apply { isAccessible = true }.getBoolean(requireNotNull(service))
                } }
                SystemClock.sleep(150)
                recordPhase(activity)
                mark("cycle${cycle + 1}-conversion")
                tap(com.kazumaproject.tenkey.R.id.key_space)
                await("conversion actually active") { conversionActive() }
                SystemClock.sleep(150)
                recordPhase(activity)
                mark("cycle${cycle + 1}-commit")
                tap(com.kazumaproject.tenkey.R.id.key_enter)
                await("committed text and cleared composing span") { onMain {
                    val text = activity.editText.text
                    text.toString() == "か".repeat(cycle + 1) &&
                        BaseInputConnection.getComposingSpanStart(text) == -1
                } }
                await("conversion cleared") { !conversionActive() }
                SystemClock.sleep(150)
                recordPhase(activity)
            }
            mark("finished")
            SystemClock.sleep(250)
            onMain {
                monitoring = false
                observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(drawListener)
                Choreographer.getInstance().removeFrameCallback(frameCallback)
            }
            result.put("finalText", onMain { activity.editText.text.toString() })
            capture("final")
            check(frames.length() >= cycles * 3) { "Insufficient continuous samples" }
            val heights = (0 until frames.length()).map { frames.getJSONObject(it).getInt("mainHeight") }.toSet()
            result.put("observedMainHeights", JSONArray(heights.sorted()))
            val surfaceHeights = (0 until frames.length()).map { frames.getJSONObject(it).getInt("windowHeight") }.toSet()
            result.put("observedSurfaceHeights", JSONArray(surfaceHeights.sorted()))
            val missing = (0 until frames.length()).count {
                val frame = frames.getJSONObject(it)
                !frame.getBoolean("shown") || frame.getDouble("alpha") == 0.0 || frame.getDouble("windowAlpha") == 0.0
            }
            result.put("missingKeyboardSamples", missing)
            check(missing == 0) { "Keyboard disappeared in $missing samples" }
            if (!floating && !expectHeightChange) {
                check(surfaceHeights.size == 1) { "Backing IME surface resized during input: $surfaceHeights" }
                val keyBounds = (0 until frames.length()).map {
                    frames.getJSONObject(it).getJSONArray("keyBounds").toString()
                }.toSet()
                check(keyBounds.size == 1) { "Key body moved during candidate changes: $keyBounds" }
                if (Build.VERSION.SDK_INT >= 30) {
                    check((0 until phases.length()).all { index ->
                        val snapshot = phases.getJSONObject(index)
                        kotlin.math.abs(snapshot.getInt("editorImeInset") - snapshot.getInt("mainHeight")) <= 1
                    }) { "Editor IME Insets did not follow the actual input-view height" }
                }
            }
            if (expectHeightChange) {
                check(heights.size > 1) { "Baseline control did not detect the expected height change" }
            } else if (!floating && candidateHeight == 60 && (mode == "on" || !tab)) {
                check(heights == setOf(initialHeight)) { "Transient IME height changed: $heights; initial=$initialHeight" }
            } else if (!floating) {
                val density = context.resources.displayMetrics.density
                val candidateDelta = (candidateHeight * density).toInt() - (60 * density).toInt()
                // XML dimensions use resource rounding; 36dp at 2.625 density is 95px,
                // whereas the candidate preferences deliberately truncate their dp values.
                val tabDelta = if (tab && mode != "on") onMain {
                    requireNotNull(main).findViewById<View>(R.id.candidate_tab_layout).layoutParams.height
                } else 0
                val activeHeight = initialHeight + candidateDelta + tabDelta
                check(heights.all { it == initialHeight || it == activeHeight }) {
                    "Unexpected height during configured resize: $heights; expected=$initialHeight/$activeHeight"
                }
                check((0 until phases.length()).all { index ->
                    val snapshot = phases.getJSONObject(index)
                    snapshot.getInt("mainHeight") == if (snapshot.getString("phase").endsWith("commit")) initialHeight else activeHeight
                }) { "Configured candidate/tab resize was not applied" }
            }
            monitorFailure?.let { throw it }
            result.put("passed", true)
        } catch (error: Throwable) {
            failure = error
            result.put("passed", false).put("failure", error.stackTraceToString())
            runCatching { capture("failure") }
        } finally {
            runCatching { onMain {
                monitoring = false
                observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(drawListener)
                Choreographer.getInstance().removeFrameCallback(frameCallback)
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<FastInputHostActivity>().forEach { it.finish() }
            } }.onFailure { if (failure == null) failure = it }
            val restore = prefs.edit().clear()
            saved.forEach { (key, value) -> when (value) {
                is Boolean -> restore.putBoolean(key, value)
                is Int -> restore.putInt(key, value)
                is Long -> restore.putLong(key, value)
                is Float -> restore.putFloat(key, value)
                is String -> restore.putString(key, value)
                is Set<*> -> restore.putStringSet(key, value.filterIsInstance<String>().toSet())
            } }
            check(restore.commit())
            if (oldIme != "null" && oldIme.isNotBlank()) shell("ime set $oldIme")
            // UiAutomation tokenizes the command directly; quotes would become stored data.
            if (oldEnabled != "null") shell("settings put secure enabled_input_methods $oldEnabled")
            for ((key, value) in listOf("user_rotation" to oldRotation, "accelerometer_rotation" to oldAuto)) {
                shell(if (value == "null") "settings delete system $key" else "settings put system $key $value")
            }
            result.put("restoredIme", shell("settings get secure default_input_method"))
            result.put("events", events).put("frames", frames).put("phases", phases)
            if (failure != null) result.put("passed", false).put("failure", failure!!.stackTraceToString())
            File(output, "result.json").writeText(result.toString(2))
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "TOOLBAR_RESULT $label passed=${failure == null} samples=${frames.length()} output=$output\n") })
        }
        failure?.let { throw it }
    }
}
