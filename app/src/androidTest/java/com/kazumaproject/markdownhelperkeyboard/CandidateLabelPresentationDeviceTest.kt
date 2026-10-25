package com.kazumaproject.markdownhelperkeyboard

import android.content.ContextWrapper
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.inspector.WindowInspector
import android.widget.TextView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.getActionButton
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.*
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.CandidateIndicatorPreferenceFragment
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
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

    private fun mixedRow(): View = WindowInspector.getGlobalWindowViews().firstNotNullOf { root ->
        root.findViewById<androidx.recyclerview.widget.RecyclerView>(id("suggestion_recycler_view"))?.let { list ->
            (0 until list.childCount).map(list::getChildAt).firstOrNull {
                list.isShown && it.findViewById<TextView>(id("suggestion_item_text_view"))?.text?.toString()?.trim() == "東京でカフェ１２３ABC"
            }
        }
    }

    private fun bodyHasColor(color: Int): Boolean {
        val view = mixedRow().findViewById<TextView>(id("suggestion_item_text_view"))
        if (view.width <= 0 || view.height <= 0) return false
        val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val pixels = IntArray(view.width * view.height)
        bitmap.getPixels(pixels, 0, view.width, 0, 0, view.width, view.height)
        bitmap.recycle()
        return color in pixels
    }

    private fun selectColor(fragment: CandidateIndicatorPreferenceFragment, dark: Boolean, index: Int) {
        main {
            val preference = requireNotNull(fragment.findPreference<androidx.preference.Preference>(
                CandidateIndicatorSettings.colorKey(CandidateIndicatorGroup.KATAKANA, dark)))
            check(requireNotNull(preference.onPreferenceClickListener).onPreferenceClick(preference))
        }
        var dialog: MaterialDialog? = null
        await("Color chooser did not appear") {
            dialog = CandidateIndicatorPreferenceFragment::class.java.getDeclaredField("colorDialog")
                .apply { isAccessible = true }.get(fragment) as? MaterialDialog
            dialog?.findViewById<androidx.recyclerview.widget.RecyclerView>(id("colorPresetGrid"))
                ?.findViewHolderForAdapterPosition(index) != null
        }
        main {
            val grid = requireNotNull(dialog).findViewById<androidx.recyclerview.widget.RecyclerView>(id("colorPresetGrid"))
            check(requireNotNull(grid.findViewHolderForAdapterPosition(index)).itemView.performClick())
        }
        if (!dark) capture("style-color-picker")
        main { check(requireNotNull(dialog).getActionButton(WhichButton.POSITIVE).performClick()) }
        await("Color chooser did not save selection") { dialog?.isShowing == false }
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
        var sourceLabel: String? = null
        var rowWidth: Int? = null
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
                val target = mixedRow()
                val label = target.findViewById<TextView>(id("suggestion_item_type_text_view")).text.toString()
                assertFalse("Character names must not extend the row", label.contains("・"))
                if (sourceLabel == null) { sourceLabel = label; rowWidth = target.width }
                assertEquals(sourceLabel, label)
                assertEquals("Indicators changed the candidate width", rowWidth, target.width)
            }
            if (enabled && !baseline) {
                val group = CandidateIndicatorGroup.KATAKANA
                val color = if (dark) 0xffff80ab.toInt() else 0xffc2185b.toInt()
                prefs.edit().putString(CandidateIndicatorSettings.lineKey(group), "dashed")
                    .putInt(CandidateIndicatorSettings.colorKey(group, dark), color).commit()
                await("Custom style was not rebound on the existing candidate") { bodyHasColor(color) }
                assertEquals("Composition changed while changing styles", "てすと", main { host.editor.text.toString() })
                capture("mixed-custom")
                CandidateIndicatorSettings.reset(prefs)
                await("Default style did not return") { !bodyHasColor(color) }
            }
            main { check(mixedRow().performClick()) }
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
                it.findViewById<androidx.recyclerview.widget.RecyclerView>(id("candidates_row_view"))?.takeIf { list ->
                    list.isShown && (0 until list.childCount).any { index ->
                        list.getChildAt(index).findViewById<TextView>(id("suggestion_item_text_view"))
                            ?.text?.toString()?.trim() == "12月31日"
                    }
                }
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
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity>()
                    .firstOrNull { it.hasWindowFocus() }
                navHost = activity?.supportFragmentManager?.findFragmentById(id("nav_host_fragment_activity_main")) as? NavHostFragment
                navHost != null
            }
            try {
                for ((destination, name, fragmentName) in listOf(
                    Triple(id("candidateConversionPreferenceFragment"), "settings-new", "CandidateConversionPreferenceFragment"),
                    Triple(id("legacyCommonPreferenceFragment"), "settings-legacy", "LegacyCommonPreferenceFragment"),
                )) {
                    main { requireNotNull(navHost).navController.navigate(destination) }
                    await("Settings switch missing: $name") {
                        val fragment = navHost?.childFragmentManager?.primaryNavigationFragment as? PreferenceFragmentCompat
                        fragment?.javaClass?.simpleName == fragmentName && fragment.view?.isShown == true &&
                            fragment.findPreference<androidx.preference.Preference>("candidate_character_type_indicators_preference") != null
                    }
                    main { (navHost?.childFragmentManager?.primaryNavigationFragment as PreferenceFragmentCompat)
                        .scrollToPreference("candidate_indicator_style_settings") }
                    val title = main { requireNotNull(navHost).requireContext().getString(
                        context.resources.getIdentifier("candidate_indicator_settings", "string", context.packageName)
                    ) }
                    var stableRect: android.graphics.Rect? = null
                    var stableSince = 0L
                    await("Settings row was not drawn: $name") {
                        val fragment = navHost?.childFragmentManager?.primaryNavigationFragment as? PreferenceFragmentCompat
                        // Async settings can restore their scroll after publishing the hierarchy.
                        // Keep the requested entry in view until its actual position is stable.
                        fragment?.scrollToPreference("candidate_indicator_style_settings")
                        fun findTitle(view: View): TextView? = if (view is TextView && view.text.toString() == title && view.isShown) view
                            else (view as? android.view.ViewGroup)?.let { group ->
                                (0 until group.childCount).firstNotNullOfOrNull { findTitle(group.getChildAt(it)) }
                            }
                        val rowTitle = fragment?.listView?.let(::findTitle)
                        val rect = android.graphics.Rect()
                        if (rowTitle == null || !rowTitle.getGlobalVisibleRect(rect) || rect.height() < rowTitle.height) {
                            stableRect = null
                            false
                        } else if (rect != stableRect) {
                            stableRect = rect
                            stableSince = SystemClock.uptimeMillis()
                            false
                        } else SystemClock.uptimeMillis() - stableSince >= 750
                    }
                    capture(name)
                    main {
                        val fragment = navHost?.childFragmentManager?.primaryNavigationFragment as PreferenceFragmentCompat
                        val route = requireNotNull(fragment.findPreference<androidx.preference.Preference>("candidate_indicator_style_settings"))
                        check(requireNotNull(route.onPreferenceClickListener).onPreferenceClick(route))
                    }
                    var stylesFragment: CandidateIndicatorPreferenceFragment? = null
                    await("Style settings did not open from $name") {
                        stylesFragment = navHost?.childFragmentManager?.primaryNavigationFragment as? CandidateIndicatorPreferenceFragment
                        stylesFragment?.findPreference<androidx.preference.Preference>("candidate_indicator_preview") != null &&
                            WindowInspector.getGlobalWindowViews().any { it.findViewById<View>(id("candidate_indicator_preview_light"))?.isShown == true }
                    }
                    capture(if (name == "settings-new") "style-settings-default" else "style-settings-legacy")
                    if (name == "settings-new") {
                        val fragment = requireNotNull(stylesFragment)
                        main { fragment.onDisplayPreferenceDialog(requireNotNull(fragment.findPreference<androidx.preference.ListPreference>(
                            CandidateIndicatorSettings.lineKey(CandidateIndicatorGroup.KATAKANA)))) }
                        var choices: android.widget.ListView? = null
                        await("Line style choices did not open") {
                            fun find(view: View): android.widget.ListView? = if (view is android.widget.ListView && view.adapter?.count == 5) view
                                else (view as? android.view.ViewGroup)?.let { group -> (0 until group.childCount).firstNotNullOfOrNull { find(group.getChildAt(it)) } }
                            choices = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::find)
                            choices?.isShown == true
                        }
                        main { requireNotNull(choices).let { list -> check(list.performItemClick(list.getChildAt(3), 3, list.adapter.getItemId(3))) } }
                        await("Line style was not saved") { CandidateIndicatorSettings.read(prefs).katakana.line == CandidateIndicatorLineStyle.DASHED }
                        selectColor(fragment, dark = false, index = 7)
                        selectColor(fragment, dark = true, index = 8)
                        assertEquals(0xff2196f3.toInt(), CandidateIndicatorSettings.read(prefs).katakana.lightColor)
                        assertEquals(0xffe91e63.toInt(), CandidateIndicatorSettings.read(prefs).katakana.darkColor)
                        capture("style-settings-custom")
                        main {
                            val reset = requireNotNull(fragment.findPreference<androidx.preference.Preference>("candidate_indicator_reset"))
                            check(requireNotNull(reset.onPreferenceClickListener).onPreferenceClick(reset))
                        }
                        await("Reset did not restore defaults") { CandidateIndicatorSettings.read(prefs) == CandidateIndicatorStyles() }
                        assertTrue(prefs.getBoolean("candidate_character_type_indicators_preference", false))
                    }
                }
            } finally {
                main { navHost?.activity?.finish() }
            }
        }
    }
}
