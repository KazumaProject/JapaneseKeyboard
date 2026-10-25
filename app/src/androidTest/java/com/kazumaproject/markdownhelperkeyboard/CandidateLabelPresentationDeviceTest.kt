package com.kazumaproject.markdownhelperkeyboard

import android.content.ContextWrapper
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.inspector.WindowInspector
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceFragmentCompat
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt in only on a disposable emulator with -e candidate_label_device_test true. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class CandidateLabelPresentationDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context = ins.targetContext
    private var previousIme: String? = null
    private fun id(name: String): Int = context.resources.getIdentifier(name, "id", context.packageName)

    private fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        ins.uiAutomation.executeShellCommand(command)
    ).use { it.readBytes().toString(Charsets.UTF_8).trim() }

    @After fun restoreInputMethod() {
        previousIme?.takeIf { it.isNotBlank() && it != "null" }?.let { shell("ime set $it") }
    }

    private fun <T> main(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var result: T? = null
        var error: Throwable? = null
        ins.runOnMainSync { try { result = block() } catch (t: Throwable) { error = t } }
        error?.let { throw it }
        @Suppress("UNCHECKED_CAST") return result as T
    }

    private fun await(message: String, condition: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 45_000
        while (SystemClock.uptimeMillis() < until) {
            if (main(condition)) return
            SystemClock.sleep(100)
        }
        error(message)
    }

    private fun service(): IMEService? = WindowInspector.getGlobalWindowViews().asSequence()
        .flatMap { root -> generateSequence((root.findViewById<View>(id("suggestion_recycler_view")) ?: root).context) {
            (it as? ContextWrapper)?.baseContext
        } }.filterIsInstance<IMEService>().firstOrNull()

    private fun field(name: String): Any? = IMEService::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.get(requireNotNull(service()))

    @Suppress("UNCHECKED_CAST")
    private fun candidates(): List<Candidate> = field("currentCandidateStripCandidates") as List<Candidate>

    private fun tapKey(id: Int, dx: Int = 0, dy: Int = 0) {
        val view = main { WindowInspector.getGlobalWindowViews().asReversed()
            .firstNotNullOf { it.findViewById<View>(id)?.takeIf(View::isShown) } }
        val bounds = main {
            val position = IntArray(2)
            view.getLocationOnScreen(position)
            android.graphics.Rect(position[0], position[1], position[0] + view.width, position[1] + view.height)
        }
        val time = SystemClock.uptimeMillis()
        val distance = context.resources.displayMetrics.density * 45
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action,
                bounds.exactCenterX() + if (action == MotionEvent.ACTION_DOWN) 0f else dx * distance,
                bounds.exactCenterY() + if (action == MotionEvent.ACTION_DOWN) 0f else dy * distance, 0)
            check(ins.uiAutomation.injectInputEvent(event, true))
            event.recycle()
            SystemClock.sleep(50)
        }
        SystemClock.sleep(150)
    }

    private fun capture(name: String) {
        // Each call site awaits its visible state. The IME can keep scheduling work,
        // so waiting for the entire main queue to become idle can block indefinitely.
        SystemClock.sleep(400)
        val args = InstrumentationRegistry.getArguments()
        val prefix = listOf("dark", "floating", "landscape")
            .filter { args.getString("candidate_label_$it") == "true" }.joinToString("") { "$it-" }
        val destination = File(context.getExternalFilesDir("candidate-label-qa"), "$prefix$name.png")
        destination.parentFile!!.mkdirs()
        val bitmap = requireNotNull(ins.uiAutomation.takeScreenshot())
        destination.outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        android.util.Log.i("CandidateLabelDeviceQA", "Captured ${destination.name}")
    }

    @Test fun realImeCandidateFormsAndCommitMatch() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("candidate_label_device_test") == "true")
        val baseline = InstrumentationRegistry.getArguments().getString("candidate_label_baseline") == "true"
        val dark = InstrumentationRegistry.getArguments().getString("candidate_label_dark") == "true"
        val floating = InstrumentationRegistry.getArguments().getString("candidate_label_floating") == "true"
        val landscape = InstrumentationRegistry.getArguments().getString("candidate_label_landscape") == "true"
        shell("cmd uimode night ${if (dark) "yes" else "no"}")
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear()
            .putString("keyboard_order_preference", "[\"TENKEY\"]")
            .putBoolean("save_last_used_keyboard", false)
            .putBoolean("live_conversion_preference", false)
            .putBoolean("keyboard_floating_preference", floating)
            .putBoolean("show_dictionary_candidate_labels_preference", true)
            .putBoolean("user_dictionary_preference", true)
            .putBoolean("learn_dictionary_preference", false)
            .putBoolean("enable_prediction_search_learn_dictionary_preference", false)
            .putBoolean("ng_word_enable_preference", false)
            .putBoolean("allow_fullscreen_mode_preference", false)
            .putBoolean("clipboard_preview_enable_preference", false)
            .putBoolean("inline_suggestion_enabled_preference", false)
            .putBoolean("candidate_character_type_indicators_preference", false).commit()
        previousIme = shell("settings get secure default_input_method")
        val targetIme = "${context.packageName}/${IMEService::class.java.name}"
        shell("ime enable $targetIme")
        shell("ime set $targetIme")
        assertEquals("Test IME was not selected", targetIme, shell("settings get secure default_input_method"))
        context.startActivity(Intent(context, CandidateHeightTestHostActivity::class.java)
            .putExtra("landscape", landscape).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await("Editor/IME did not start") { service() != null && CandidateHeightTestHostActivity.current.get()?.hasWindowFocus() == true }
        val host = main { requireNotNull(CandidateHeightTestHostActivity.current.get()) }
        val ime = main { requireNotNull(service()) }
        ime.userDictionaryRepository.insert(UserWord(word = "東京でカフェ１２３ABC", reading = "てすと", posIndex = 0, posScore = -10_000))
        for (enabled in if (baseline) listOf(false) else listOf(false, true, false)) {
            prefs.edit().putBoolean("candidate_character_type_indicators_preference", enabled).commit()
            main { host.restartEditor() }
            await("Keyboard not ready") { host.editor.text.isEmpty() && WindowInspector.getGlobalWindowViews().any {
                it.findViewById<View>(id("key_4"))?.isShown == true
            } }
            SystemClock.sleep(500)
            tapKey(id("key_4"), dx = 1) // て
            tapKey(id("key_3"), dy = -1) // す
            tapKey(id("key_4"), dy = 1) // と
            await("Mixed dictionary candidate missing") { candidates().any { it.string == "東京でカフェ１２３ABC" } }
            await("Candidate not drawn") { WindowInspector.getGlobalWindowViews().any { root ->
                fun contains(view: View): Boolean = (view as? TextView)?.text?.toString()?.trim() == "東京でカフェ１２３ABC" ||
                    (view as? android.view.ViewGroup)?.let { group -> (0 until group.childCount).any { contains(group.getChildAt(it)) } } == true
                contains(root)
            } }
            capture(if (baseline) "baseline-mixed" else if (enabled) "mixed-on" else "mixed-off")
            main {
                val recycler = WindowInspector.getGlobalWindowViews().firstNotNullOf { it.findViewById<androidx.recyclerview.widget.RecyclerView>(id("suggestion_recycler_view")) }
                val target = (0 until recycler.childCount).map(recycler::getChildAt).first {
                    it.findViewById<TextView>(id("suggestion_item_text_view"))?.text?.toString()?.trim() == "東京でカフェ１２３ABC"
                }
                assertEquals("Mixed-form badges did not follow the switch", enabled,
                    target.findViewById<TextView>(id("suggestion_item_type_text_view")).text.contains("・"))
                check(target.performClick())
            }
            await("Displayed text was not committed") { host.editor.text.toString() == "東京でカフェ１２３ABC" }
        }
        main { host.restartEditor() }
        SystemClock.sleep(500)
        // Exercise the numeric query through the actual IME input flow; no candidate injection.
        main {
            @Suppress("UNCHECKED_CAST")
            val input = field("_inputString") as MutableStateFlow<String>
            input.value = "1231"
        }
        await("Numeric date missing") { candidates().any { it.string == "12月31日" } }
        val date = main { candidates().first { it.string == "12月31日" } }
        assertEquals(if (baseline) 40.toByte() else 55.toByte(), date.type)
        tapKey(id("suggestion_visibility"))
        await("Expanded date candidate not visible") {
            WindowInspector.getGlobalWindowViews().any { root ->
                val rows = root.findViewById<androidx.recyclerview.widget.RecyclerView>(id("candidates_row_view"))
                rows?.isShown == true && (0 until rows.childCount).any {
                    rows.getChildAt(it).findViewById<TextView>(id("suggestion_item_text_view"))?.text?.toString()?.trim() == "12月31日"
                }
            }
        }
        main {
            val rows = WindowInspector.getGlobalWindowViews().firstNotNullOf {
                it.findViewById<androidx.recyclerview.widget.RecyclerView>(id("candidates_row_view"))?.takeIf(View::isShown)
            }
            val row = (0 until rows.childCount).map(rows::getChildAt).first {
                it.findViewById<TextView>(id("suggestion_item_text_view"))?.text?.toString()?.trim() == "12月31日"
            }
            assertEquals("Numeric date must not be labeled as AI", baseline,
                row.findViewById<TextView>(id("suggestion_item_type_text_view")).text.contains("AI"))
        }
        capture(if (baseline) "baseline-date" else "date-fixed")
        tapKey(id("suggestion_visibility"))
        if (!baseline) {
            prefs.edit().putBoolean("candidate_character_type_indicators_preference", true)
                .putBoolean("live_conversion_preference", true)
                .putBoolean("live_conversion_candidate_yomi_preference", true)
                .putString("live_conversion_candidate_yomi_mode_preference", "ruby").commit()
            main { host.restartEditor() }
            SystemClock.sleep(800)
            main {
                @Suppress("UNCHECKED_CAST")
                val input = field("_inputString") as MutableStateFlow<String>
                input.value = "とうきょうでかふぇ"
            }
            await("Converted reading candidate missing") { candidates().firstOrNull()?.let {
                !it.yomi.isNullOrBlank() && it.yomi != it.string
            } == true }
            await("Reading was not drawn alongside indicators") { WindowInspector.getGlobalWindowViews().any {
                it.findViewById<TextView>(id("suggestion_item_yomi_text_view"))?.let { reading ->
                    reading.isShown && reading.text.isNotEmpty()
                } == true
            } }
            capture("ruby-on")
            // ActivityScenario waits for global main-queue idleness before launch.
            // A running IME need not become idle; await the actual settings window instead.
            context.startActivity(Intent(context, com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            var navHost: NavHostFragment? = null
            await("Settings activity did not start") {
                val activity = WindowInspector.getGlobalWindowViews().asSequence().flatMap { root ->
                    generateSequence(root.context) { (it as? ContextWrapper)?.baseContext }
                }.filterIsInstance<com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity>()
                    .firstOrNull { it.hasWindowFocus() }
                navHost = activity?.supportFragmentManager?.findFragmentById(id("nav_host_fragment_activity_main")) as? NavHostFragment
                navHost != null
            }
            try {
                for ((destination, name) in listOf(
                    id("candidateConversionPreferenceFragment") to "settings-new",
                    id("legacyCommonPreferenceFragment") to "settings-legacy",
                )) {
                    main { requireNotNull(navHost).navController.navigate(destination) }
                    await("Settings switch missing: $name") {
                        (navHost?.childFragmentManager?.primaryNavigationFragment as? PreferenceFragmentCompat)
                            ?.findPreference<androidx.preference.Preference>("candidate_character_type_indicators_preference") != null
                    }
                    main { (navHost?.childFragmentManager?.primaryNavigationFragment as PreferenceFragmentCompat)
                        .scrollToPreference("candidate_character_type_indicators_preference") }
                    val title = main { requireNotNull(navHost).requireContext().getString(
                        context.resources.getIdentifier("candidate_character_indicators_title", "string", context.packageName)
                    ) }
                    await("Settings row was not drawn: $name") {
                        val list = (navHost?.childFragmentManager?.primaryNavigationFragment as? PreferenceFragmentCompat)?.listView
                        fun containsTitle(view: View): Boolean = (view as? TextView)?.let {
                            it.text.toString() == title &&
                                it.isShown && it.getGlobalVisibleRect(android.graphics.Rect())
                        } == true || (view as? android.view.ViewGroup)?.let { group ->
                            (0 until group.childCount).any { containsTitle(group.getChildAt(it)) }
                        } == true
                        list?.let(::containsTitle) == true
                    }
                    capture(name)
                }
            } finally {
                main { navHost?.activity?.finish() }
            }
        }
    }
}
