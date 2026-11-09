package com.kazumaproject.markdownhelperkeyboard

import android.content.Intent
import android.graphics.PointF
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.inspector.WindowInspector
import androidx.core.widget.doAfterTextChanged
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap

/** Run on a physical device with -e english_latency true and an isolated debug application ID. */
@RunWith(AndroidJUnit4::class)
class EnglishInputLatencyDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val ui get() = instrumentation.uiAutomation
    private val args get() = InstrumentationRegistry.getArguments()

    @Test
    fun measureEnglishTyping() {
        assumeTrue(args.getString("english_latency") == "true")
        check(context.packageName.endsWith(".englishlatencyqa")) { "Use the isolated QA application ID" }
        val words = args.getString("english_latency_words")?.split(',') ?: listOf(
            "h", "he", "the", "and", "in", "open", "there", "testing", "hecantake",
            "keyboard", "twofactorauthentication", "thequickbrownfoxjumpsoverthelazydog",
        )
        require(words.isNotEmpty() && words.all { it.matches(Regex("[a-z]{1,255}")) })
        val rounds = args.getString("english_latency_rounds")?.toInt() ?: 4
        val interval = args.getString("english_latency_interval_ms")?.toLong() ?: 85L
        require(rounds in 1..100 && interval in 30..1_000)
        val waitForEachCandidate = args.getString("english_latency_serial") == "true"
        val liveConversion = args.getString("english_latency_live") == "true"
        val target = "${context.packageName}/${IMEService::class.java.name}"
        val previousIme = shell("settings get secure default_input_method")
        val wasEnabled = shell("ime list -s").lines().any { it.startsWith(context.packageName + "/") }
        check(PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString("keyboard_order_preference", "[\"QWERTY\"]")
            .putBoolean("save_last_used_keyboard", false)
            .putBoolean("keyboard_floating_preference", false)
            .putBoolean("qwerty_english_direct_input_preference", false)
            .putBoolean("qwerty_glide_input_preference", liveConversion)
            .putBoolean("qwerty_show_popup_window_preference", liveConversion)
            .putBoolean("enable_typo_correction_qwerty_english_keyboard_preference", false)
            .putBoolean("live_conversion_preference", liveConversion)
            .putInt("live_conversion_start_length_preference", 1)
            .putBoolean("flick_input_only_preference", liveConversion)
            .putBoolean("incremental_conversion_session_preference", liveConversion)
            .putBoolean("learn_dictionary_preference", false)
            .putBoolean("zero_query_suggestion_preference", false)
            .putBoolean("candidate_tab_visibility_preference", liveConversion)
            .putBoolean("english_prediction_enable_preference", args.getString("english_latency_prediction") != "false")
            .commit())
        try {
            shell("input keyevent WAKEUP")
            shell("wm dismiss-keyguard")
            shell("ime enable $target")
            shell("ime set $target")
            check(shell("settings get secure default_input_method").startsWith(context.packageName + "/"))
            ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { scenario ->
                repeat(rounds) { round ->
                    words.forEach { word -> measureWord(scenario, word, round, interval, waitForEachCandidate) }
                }
            }
        } finally {
            if (previousIme.isNotBlank() && previousIme != "null") shell("ime set $previousIme")
            if (!wasEnabled) shell("ime disable $target")
        }
    }

    private fun measureWord(
        scenario: ActivityScenario<FastInputHostActivity>,
        word: String,
        round: Int,
        interval: Long,
        serial: Boolean,
    ) {
        val textTimes = ConcurrentHashMap<String, Long>()
        val candidateTimes = ConcurrentHashMap<String, Long>()
        val captureOrder = args.getString("english_latency_capture_order") == "true"
        val candidateOrders = ConcurrentHashMap<String, List<String>>()
        scenario.onActivity { it.restartEditorInput(clearText = true) }
        val keys = mutableMapOf<Char, PointF>()
        lateinit var recycler: RecyclerView
        lateinit var adapter: SuggestionAdapter
        await("QWERTY keys and empty candidate list") {
            instrumentation.runOnMainSync {
                val roots = WindowInspector.getGlobalWindowViews()
                val list = roots.firstNotNullOfOrNull { it.findViewById<RecyclerView>(R.id.suggestion_recycler_view) }
                val suggestions = list?.adapter as? SuggestionAdapter
                if (list == null || suggestions == null || suggestions.suggestions.isNotEmpty()) return@runOnMainSync
                recycler = list
                adapter = suggestions
                for (char in word.toSet()) {
                    val id = context.resources.getIdentifier("key_$char", "id", context.packageName)
                    val key = roots.firstNotNullOfOrNull { it.findViewById<View>(id)?.takeIf(View::isShown) } ?: continue
                    val position = IntArray(2)
                    key.getLocationOnScreen(position)
                    keys[char] = PointF(position[0] + key.width / 2f, position[1] + key.height / 2f)
                }
            }
            keys.size == word.toSet().size
        }
        var typed = ""
        var previousSuggestions = adapter.suggestions
        val draw = ViewTreeObserver.OnPreDrawListener {
            val suggestions = adapter.suggestions
            if (suggestions !== previousSuggestions) {
                previousSuggestions = suggestions
                if (suggestions.any { it.string.equals(typed, ignoreCase = true) }) {
                    if (captureOrder) candidateOrders.putIfAbsent(typed, suggestions.map { it.string })
                    candidateTimes.putIfAbsent(typed, SystemClock.uptimeMillis())
                }
            }
            true
        }
        val watcher = checkNotNull(scenario.withEditor { editor ->
            recycler.viewTreeObserver.addOnPreDrawListener(draw)
            editor.doAfterTextChanged { text ->
                typed = text.toString()
                textTimes.putIfAbsent(typed, SystemClock.uptimeMillis())
            }
        })
        val releases = LongArray(word.length)
        try {
            var nextTap = SystemClock.uptimeMillis()
            word.forEachIndexed { index, char ->
                SystemClock.sleep((nextTap - SystemClock.uptimeMillis()).coerceAtLeast(0))
                val down = SystemClock.uptimeMillis()
                val point = keys.getValue(char)
                inject(MotionEvent.ACTION_DOWN, down, down, point)
                releases[index] = SystemClock.uptimeMillis()
                inject(MotionEvent.ACTION_UP, down, releases[index], point)
                if (serial) {
                    val prefix = word.take(index + 1)
                    await("candidates for $prefix") { candidateTimes[prefix]?.let { it >= releases[index] } == true }
                }
                nextTap = down + interval
            }
            await("text and candidates for $word (observed editor prefixes: $textTimes)") { word in textTimes.keys && word in candidateTimes.keys }
            assertEquals(word, scenario.withEditor { it.text.toString() })
            for (index in releases.indices) {
                val prefix = word.take(index + 1)
                val textMs = textTimes[prefix]?.minus(releases[index])
                val candidateMs = candidateTimes[prefix]?.minus(releases[index])?.takeIf { it >= 0 }
                instrumentation.sendStatus(2, Bundle().apply {
                    val order = candidateOrders[prefix]?.let { "\nENGLISH_ORDER round=$round prefix=$prefix candidates=${JSONArray(it)}" }.orEmpty()
                    putString("stream", "\nENGLISH_LATENCY round=$round word=$word key=${index + 1} text_ms=$textMs candidate_ms=$candidateMs$order\n")
                })
            }
            if (args.getString("english_latency_allow_skipped_text") != "true") {
                assertEquals("Every prefix must reach the editor", word.length, releases.indices.count {
                    textTimes[word.take(it + 1)]?.let { time -> time >= releases[it] } == true
                })
            }
        } finally {
            scenario.onActivity {
                it.editText.removeTextChangedListener(watcher)
                recycler.viewTreeObserver.removeOnPreDrawListener(draw)
            }
        }
    }

    private fun <T> ActivityScenario<FastInputHostActivity>.withEditor(action: (android.widget.EditText) -> T): T? {
        var result: T? = null
        onActivity { result = action(it.editText) }
        return result
    }

    private fun await(label: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < deadline) { "Timed out waiting for $label" }
            SystemClock.sleep(5)
        }
    }

    private fun inject(action: Int, down: Long, time: Long, point: PointF) {
        val event = MotionEvent.obtain(down, time, action, point.x, point.y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try { check(ui.injectInputEvent(event, false)) } finally { event.recycle() }
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
}
