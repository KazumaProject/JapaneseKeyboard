package com.kazumaproject.markdownhelperkeyboard

import android.app.UiAutomation
import android.graphics.PointF
import android.inputmethodservice.InputMethodService
import android.os.*
import android.view.*
import android.view.inspector.WindowInspector
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.ime_service.fever.FeverInputFrameLayout
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiQueryResult
import com.kazumaproject.markdownhelperkeyboard.converter.session.CandidateQueryMode
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Collections

/**
 * Opt-in real-IME benchmark; refuses to change the user's installed application.
 * candidateReadyMs ends at the IME's candidate-refresh notification, before asynchronous diff
 * completion/presentation. frameMs comes from the IME window, not the editor activity.
 * feverCoreProbe measures the complete core entry point after timed typing, in a separate trial.
 */
@RunWith(AndroidJUnit4::class)
@androidx.test.filters.SdkSuppress(minSdkVersion = 29)
class FeverPerformanceInstrumentedTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ui get() = ins.uiAutomation
    private val context get() = ins.targetContext
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command))
        .bufferedReader().use { it.readText().trim() }
    private fun await(what: String, test: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 60_000
        while (SystemClock.uptimeMillis() < until) { if (test()) return; SystemClock.sleep(10) }
        error("Timed out: $what")
    }
    private inline fun <reified T> field(instance: Any, name: String): T =
        instance.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(instance) as T
    private fun root(): FeverInputFrameLayout? {
        fun find(v: View): FeverInputFrameLayout? {
            if (v is FeverInputFrameLayout && v.isShown) return v
            if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }
            return null
        }
        return WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::find)
    }
    private fun memory(): JSONObject {
        val info = Debug.MemoryInfo().also(Debug::getMemoryInfo)
        val runtime = Runtime.getRuntime()
        return JSONObject().put("pssKiB", info.totalPss).put("javaPssKiB", info.dalvikPss)
            .put("nativePssKiB", info.nativePss).put("graphicsKiB", info.getMemoryStat("summary.graphics"))
            .put("usedJavaBytes", runtime.totalMemory() - runtime.freeMemory())
    }
    @Test fun longTypingConversionAndRendering() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("feverPerf") == "true")
        check(context.packageName.startsWith("com.kazumaproject.skinfidelity."))
        val label = args.getString("feverPerfLabel") ?: "local"
        require(label.matches(Regex("[a-zA-Z0-9_-]+")))
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val saved = prefs.all.toMap()
        val originalIme = shell("settings get secure default_input_method")
        val target = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val enabled = target in shell("ime list -s").lines()
        var activity: FastInputHostActivity? = null
        val thread = HandlerThread("fever-frame-metrics").apply { start() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var window: Window? = null
        val frames = Collections.synchronizedList(mutableListOf<Double>())
        val measuring = java.util.concurrent.atomic.AtomicBoolean(false)
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            if (measuring.get()) frames.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1e6)
        }
        val full = args.getString("feverPerfFullScreen") != "false"
        val probeCore = args.getString("feverCoreProbe") == "true"
        val report = JSONObject().put("label", label).put("device", Build.MODEL).put("sdk", Build.VERSION.SDK_INT)
            .put("fullScreen", full).put("intensity", 2).put("liveConversion", true).put("intervalMs", 120)
            .put("coreProbe", probeCore).put("animatorScale", shell("settings get global animator_duration_scale"))
        val results = JSONArray()
        val file = File(context.getExternalFilesDir(null), "fever-perf/$label.json")
        file.parentFile!!.mkdirs()
        try {
            check(ui.setRotation(UiAutomation.ROTATION_FREEZE_0))
            prefs.edit().putString("keyboard_skin_preference", "fever")
                .putString("keyboard_order_preference", "[\"TENKEY\"]").putBoolean("save_last_used_keyboard", false)
                .putBoolean("keyboard_floating_preference", false).putInt("keyboard_width_preference", 100)
                .putBoolean("live_conversion_preference", true).putInt("n_best_preference", 4)
                .putBoolean("fever_full_screen", full).putInt("fever_intensity", 2)
                .putInt("fever_hit_rate", 5).putInt("fever_combo_seconds", 3)
                .putBoolean("key_sound_preference", false).putBoolean("fever_sound", false)
                .putBoolean("fever_vibration", false).commit()
            shell("ime enable $target"); shell("ime set $target")
            shell("am start -n ${context.packageName}/com.kazumaproject.markdownhelperkeyboard.FastInputHostActivity")
            var keyboard: FeverInputFrameLayout? = null
            await("real editor and FEVER IME") {
                ins.runOnMainSync {
                    activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<FastInputHostActivity>().firstOrNull()
                    keyboard = root()
                }
                activity != null && keyboard != null && keyboard!!.width > 0
            }
            SystemClock.sleep(1800)
            val service = keyboard!!.context as InputMethodService
            val input: StateFlow<String> = field(service, "_inputString")
            val candidates: StateFlow<Pair<String, List<Candidate>>?> = field(service, "customScreenCandidateResult")
            val sent = mutableMapOf<String, Long>()
            val ready = mutableMapOf<String, Double>()
            val fingerprints = mutableMapOf<String, String>()
            ins.runOnMainSync {
                report.put("refreshHz", keyboard!!.display.refreshRate)
                window = service.window!!.window!!
                window!!.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper))
                scope.launch {
                    candidates.collect { result ->
                        if (result != null && result.second.isNotEmpty()) sent[result.first]?.let { start ->
                            ready.putIfAbsent(result.first, (SystemClock.elapsedRealtimeNanos() - start) / 1e6)
                            fingerprints[result.first] = result.second.joinToString("|") { "${it.string}:${it.type}:${it.score}" }

                        }
                    }
                }
            }
            val sentence = "わたしはきのうあたらしいみせにいきましたあしたはともにこうえんをあるいてからいえにかえります"
            val reading = (sentence + sentence + sentence).take(120)
            // Dispatch actual TenKey gestures on main. Accessibility's idle wait would serialize effects.
            val rows = listOf("あいうえお", "かきくけこ", "さしすせそ", "たちつてと", "なにぬねの",
                "はひふへほ", "まみむめも", "や(ゆ)よ", "らりるれろ", "わをんー〜")
            fun type(ch: Char) {
                val row = rows.indexOfFirst { ch in it }; check(row >= 0)
                val index = rows[row].indexOf(ch)
                ins.runOnMainSync {
                    keyboard = requireNotNull(root())
                    val keyId = context.resources.getIdentifier("key_${if (row == 9) 11 else row + 1}", "id", context.packageName)
                    val cap = keyboard!!.findViewById<View>(keyId)
                    check(cap != null && cap.isShown)
                    val rootLocation = IntArray(2).also(keyboard!!::getLocationOnScreen)
                    val capLocation = IntArray(2).also(cap::getLocationOnScreen)
                    val x = capLocation[0] - rootLocation[0] + cap.width / 2f
                    val y = capLocation[1] - rootLocation[1] + cap.height / 2f
                    val distance = 48 * context.resources.displayMetrics.density
                    val offset = when (index) { 1 -> PointF(-distance, 0f); 2 -> PointF(0f, -distance)
                        3 -> PointF(distance, 0f); 4 -> PointF(0f, distance); else -> PointF() }
                    val down = SystemClock.uptimeMillis()
                    fun dispatch(action: Int, dx: Float, dy: Float) {
                        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x + dx + rootLocation[0], y + dy + rootLocation[1], 0)
                        event.offsetLocation(-rootLocation[0].toFloat(), -rootLocation[1].toFloat())
                        event.source = InputDevice.SOURCE_TOUCHSCREEN
                        try { check(keyboard!!.dispatchTouchEvent(event)) } finally { event.recycle() }
                    }
                    dispatch(MotionEvent.ACTION_DOWN, 0f, 0f)
                    if (index != 0) dispatch(MotionEvent.ACTION_MOVE, offset.x, offset.y)
                    dispatch(MotionEvent.ACTION_UP, offset.x, offset.y)
                }
            }
            reading.take(20).forEach { type(it); SystemClock.sleep(120) }
            repeat(args.getString("feverPerfTrials")?.toIntOrNull() ?: 3) { trial ->
                ins.runOnMainSync { activity!!.restartEditorInput(true) }
                await("composition cleared") { input.value.isEmpty() }
                SystemClock.sleep(2500)
                System.gc(); SystemClock.sleep(200)
                ins.runOnMainSync { sent.clear(); ready.clear(); fingerprints.clear() }
                frames.clear()
                val before = memory()
                val statsBefore = Debug.getRuntimeStats()
                val dispatchMs = mutableListOf<Double>()
                val activeMemory = JSONArray()
                val serial = JSONArray()
                measuring.set(true)
                val begin = SystemClock.elapsedRealtimeNanos()
                reading.forEachIndexed { index, ch ->
                    val start = SystemClock.elapsedRealtimeNanos()
                    ins.runOnMainSync { sent[reading.take(index + 1)] = SystemClock.elapsedRealtimeNanos() }
                    val dispatch = SystemClock.elapsedRealtimeNanos()
                    type(ch)
                    dispatchMs.add((SystemClock.elapsedRealtimeNanos() - dispatch) / 1e6)
                    assertEquals("Reading at index $index", reading.take(index + 1), input.value)
                    if (index + 1 in listOf(24, 64, 120)) {
                        await("candidates length ${index + 1}") {
                            var done = false; ins.runOnMainSync { done = ready.containsKey(reading.take(index + 1)) }; done
                        }
                        ins.runOnMainSync { serial.put(JSONObject().put("length", index + 1).put("readyMs", ready.getValue(reading.take(index + 1)))) }
                        activeMemory.put(memory())
                    }
                    val remaining = 120 - (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
                    if (remaining > 0) SystemClock.sleep(remaining)
                }
                measuring.set(false)
                val statsAfter = Debug.getRuntimeStats()
                val samples = synchronized(frames) { frames.toList() }
                val out = JSONObject().put("trial", trial).put("characters", reading.length)
                    .put("elapsedMs", (SystemClock.elapsedRealtimeNanos() - begin) / 1e6)
                    .put("beforeMemory", before).put("activeMemory", activeMemory).put("endMemory", memory())
                    .put("dispatchMs", JSONArray(dispatchMs)).put("frameMs", JSONArray(samples)).put("settledConversion", serial)
                    .put("allocatedBytes", statsAfter.getValue("art.gc.bytes-allocated").toLong() - statsBefore.getValue("art.gc.bytes-allocated").toLong())
                    .put("gcCount", statsAfter.getValue("art.gc.gc-count").toLong() - statsBefore.getValue("art.gc.gc-count").toLong())
                ins.runOnMainSync {
                    out.put("candidateReadyMs", JSONArray(ready.values.toList())).put("candidateUpdates", ready.size)
                        .put("finalCandidates", fingerprints[reading])
                    assertTrue(candidates.value?.second?.isNotEmpty() == true)
                    assertEquals(120, keyboard!!.controller.combo)
                }
                if (probeCore) {
                    // A separate diagnostic measures the complete real core entry point. Stage
                    // counters alone omit work performed by some original-candidate search paths.
                    SystemClock.sleep(1000)
                    val sessionField = service.javaClass.getDeclaredField("kanaKanjiConversionSession").apply { isAccessible = true }
                    val savedSession = sessionField.get(service)
                    val method = service.javaClass.declaredMethods.single { it.name == "queryKanaKanjiCore" }
                        .apply { isAccessible = true }
                    val dispatcher: CoroutineDispatcher = field(service, "kanaKanjiConversionDispatcher")
                    val learnRepository = service.javaClass.getDeclaredMethod("learnedRepositoryForSuggestion")
                        .apply { isAccessible = true }.invoke(service)
                    val wholeCore = JSONArray()
                    suspend fun query(text: String): KanaKanjiQueryResult = suspendCoroutineUninterceptedOrReturn { continuation ->
                        method.invoke(service, text, CandidateQueryMode.NO_TAB_DEFAULT, learnRepository, continuation)
                    }
                    try {
                        runBlocking { withContext(dispatcher) {
                            for (length in listOf(24, 64, 120)) repeat(10) { iteration ->
                                // Warm dictionaries, a fresh composition session, the service's
                                // actual backend/preferences, and no synthetic candidate rendering.
                                sessionField.set(service, null)
                                val start = SystemClock.elapsedRealtimeNanos()
                                val result = query(reading.take(length))
                                val elapsed = (SystemClock.elapsedRealtimeNanos() - start) / 1e6
                                check(result.candidates.isNotEmpty())
                                wholeCore.put(JSONObject().put("length", length).put("iteration", iteration)
                                    .put("ms", elapsed).put("candidateCount", result.candidates.size)
                                    .put("fingerprint", result.candidates.joinToString("|") { "${it.string}:${it.type}:${it.score}" }))
                            }
                        } }
                    } finally { sessionField.set(service, savedSession) }
                    out.put("freshSessionCore", wholeCore)
                }
                results.put(out)
                report.put("trials", results)
                file.writeText(report.toString(2))
                println("FEVER_PERF $label trial=$trial frames=${samples.size} elapsedMs=${out.getDouble("elapsedMs")} alloc=${out.getLong("allocatedBytes")}")
            }
            report.put("trials", results)
            file.writeText(report.toString(2))
            println("FEVER_PERF report=$file")
        } finally {
            measuring.set(false)
            ins.runOnMainSync { scope.cancel(); window?.removeOnFrameMetricsAvailableListener(listener); activity?.finish() }
            val edit = prefs.edit().clear()
            saved.forEach { (key, value) -> when(value) {
                is String -> edit.putString(key, value); is Boolean -> edit.putBoolean(key, value)
                is Int -> edit.putInt(key, value); is Long -> edit.putLong(key, value); is Float -> edit.putFloat(key, value)
                is Set<*> -> @Suppress("UNCHECKED_CAST") edit.putStringSet(key, value as Set<String>)
            } }
            check(edit.commit())
            shell("ime set $originalIme"); if (!enabled) shell("ime disable $target")
            ui.setRotation(UiAutomation.ROTATION_UNFREEZE)
            thread.quitSafely()
        }
    }
}
