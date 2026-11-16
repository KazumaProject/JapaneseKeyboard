package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.materialswitch.MaterialSwitch
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.keyboard_presentation.KeyboardPreviewSelection
import com.kazumaproject.markdownhelperkeyboard.keyboard_presentation.KeyboardPreviewTarget
import com.kazumaproject.markdownhelperkeyboard.keyboard_presentation.KeyboardPreviewView
import com.kazumaproject.markdownhelperkeyboard.keyboard_presentation.keyboardSettingsDestination
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.InputFlickPreferenceFragment
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NewSettingsHomeRedesignInstrumentedTest {
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) repeat(view.childCount) { yieldAll(descendants(view.getChildAt(it))) }
    }
    private fun host(activity: MainActivity) = activity.supportFragmentManager
        .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
    private fun await(scenario: ActivityScenario<MainActivity>, message: String, condition: (MainActivity) -> Boolean) {
        val until = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < until) {
            var done = false
            scenario.onActivity { done = condition(it) }
            if (done) return
            SystemClock.sleep(75)
        }
        fail(message)
    }

    @Test fun selectedPreviewFavoritesLibraryAndBasicControlsRemainReachable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue(context.packageName.endsWith(".settingsuxreview"))
        AppPreference.init(context)
        val backup = AppPreference.exportAllToJson()
        try {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean("setting_use_new_home_screen_preference", true).commit()
            AppPreference.keyboard_order = listOf(KeyboardType.TENKEY, KeyboardType.SUMIRE, KeyboardType.QWERTY)
            AppPreference.save_last_used_keyboard_enable_preference = false
            AppPreference.setting_home_frequent_keys = listOf("keyboard_screen_preference", "key_sound_preference")
            val selected = KeyboardPreviewTarget(KeyboardType.SUMIRE, inputMode = InputMode.ModeJapanese)
            KeyboardPreviewSelection.publish(selected)
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.awaitSettingsContentReady()
                await(scenario, "Selected Sumire preview missing from Home") { activity ->
                    val root = activity.findViewById<View>(R.id.setting_home_preview)
                    root != null && descendants(root).filterIsInstance<KeyboardPreviewView>().any { it.ready && it.target == selected }
                }
                scenario.onActivity { activity ->
                    assertEquals(activity.getString(R.string.settings_keyboard_selected), activity.findViewById<TextView>(R.id.setting_home_preview_summary).text.toString())
                    assertEquals(2, activity.findViewById<ViewGroup>(R.id.setting_home_frequent_grid).childCount)
                    val basics = descendants(activity.findViewById(R.id.setting_home_category_list)).filterIsInstance<TextView>().map { it.text.toString() }.toList()
                    for (name in listOf(R.string.settings_keyboard_list, R.string.settings_appearance_title, R.string.settings_input_flick_title, R.string.settings_sound_title, R.string.settings_dictionary_title))
                        assertTrue("Missing basic destination: ${activity.getString(name)}", activity.getString(name) in basics)
                    activity.findViewById<View>(R.id.setting_home_keyboard_list).performClick()
                }
                await(scenario, "Keyboard list did not show all eight types") { it.findViewById<RecyclerView>(R.id.keyboard_selection_view)?.adapter?.itemCount == KeyboardType.entries.size }
                scenario.onActivity { activity ->
                    val list = activity.findViewById<RecyclerView>(R.id.keyboard_selection_view)
                    val second = list.findViewHolderForAdapterPosition(1)!!.itemView
                    assertEquals(activity.getString(R.string.settings_keyboard_selected), second.findViewById<TextView>(R.id.keyboard_selected_badge).text.toString())
                    list.findViewHolderForAdapterPosition(2)!!.itemView.findViewById<MaterialSwitch>(R.id.keyboard_enabled).performClick()
                }
                await(scenario, "Keyboard enable switch did not persist") { KeyboardType.QWERTY !in AppPreference.keyboard_order }
                assertEquals(listOf("keyboard_screen_preference", "key_sound_preference"), AppPreference.setting_home_frequent_keys)
                // Each keyboard has an explicit settings/management destination, including disabled types.
                for (type in KeyboardType.entries) {
                    var position = 0
                    scenario.onActivity { activity ->
                        val order = AppPreference.keyboard_order
                        position = (order + KeyboardType.entries.filter { it !in order }).indexOf(type)
                        activity.findViewById<RecyclerView>(R.id.keyboard_selection_view).scrollToPosition(position)
                    }
                    await(scenario, "Keyboard row was not bound: $type") { it.findViewById<RecyclerView>(R.id.keyboard_selection_view)?.findViewHolderForAdapterPosition(position) != null }
                    scenario.onActivity { activity ->
                        activity.findViewById<RecyclerView>(R.id.keyboard_selection_view).findViewHolderForAdapterPosition(position)!!.itemView.findViewById<View>(R.id.keyboard_settings).performClick()
                    }
                    await(scenario, "Wrong settings destination for $type") { host(it).navController.currentDestination?.id == (if (type == KeyboardType.CUSTOM) R.id.keyboardListFragment else keyboardSettingsDestination(type)) }
                    scenario.awaitSettingsContentReady()
                    scenario.onActivity { assertTrue(host(it).navController.popBackStack()) }
                    await(scenario, "Keyboard list did not return") { it.findViewById<RecyclerView>(R.id.keyboard_selection_view)?.adapter?.itemCount == 8 }
                }
                scenario.onActivity { host(it).navController.navigate(R.id.inputFlickPreferenceFragment) }
                scenario.awaitSettingsContentReady()
                await(scenario, "Flick settings did not load") { (host(it).childFragmentManager.primaryNavigationFragment as? InputFlickPreferenceFragment)?.findPreference<SeekBarPreference>("flick_sensitivity_preference") != null }
                scenario.onActivity { activity ->
                    val page = host(activity).childFragmentManager.primaryNavigationFragment as InputFlickPreferenceFragment
                    page.findPreference<SeekBarPreference>("flick_sensitivity_preference")!!.value = 125
                    page.findPreference<SeekBarPreference>("long_press_timeout_preference")!!.value = 425
                }
                assertEquals(125, PreferenceManager.getDefaultSharedPreferences(context).getInt("flick_sensitivity_preference", 0))
                assertEquals(425, AppPreference.long_press_timeout_preference)
                scenario.recreate(); scenario.awaitSettingsContentReady()
                await(scenario, "Flick controls did not survive recreation") { activity ->
                    val page = host(activity).childFragmentManager.primaryNavigationFragment as? InputFlickPreferenceFragment
                    page?.findPreference<SeekBarPreference>("flick_sensitivity_preference")?.value == 125 &&
                        page.findPreference<SeekBarPreference>("long_press_timeout_preference")?.value == 425
                }
            }
        } finally {
            KeyboardPreviewSelection.publish(null)
            AppPreference.importAllFromJson(backup)
        }
    }

    @Test fun realSumireSelectionSurvivesOrderChangesAndLiveStateResetWithRememberOff() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue(context.packageName.endsWith(".settingsuxreview"))
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
        fun shot(name: String) {
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            java.io.File(context.getExternalFilesDir(null), "redesign-$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        AppPreference.init(context)
        val backup = AppPreference.exportAllToJson()
        val snapshot = context.getSharedPreferences("last_real_keyboard_preview", Context.MODE_PRIVATE)
        val previousSnapshot = snapshot.all.toMap()
        val oldIme = shell("settings get secure default_input_method")
        val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.edit().putBoolean("setting_use_new_home_screen_preference", true)
                .putString("sumire_keyboard_style_preference", "free-popup")
                .putString("sumire_input_method_preference", "flick")
                .putBoolean("landscape_force_qwerty_preference", false).commit()
            AppPreference.keyboard_order = listOf(KeyboardType.SUMIRE, KeyboardType.TENKEY)
            AppPreference.save_last_used_keyboard_enable_preference = false
            snapshot.edit().clear().commit()
            KeyboardPreviewSelection.publish(null)
            shell("ime enable $ime"); shell("ime set $ime")
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.awaitSettingsContentReady()
                scenario.onActivity { it.findViewById<View>(R.id.setting_home_search_button).performClick() }
                await(scenario, "Search did not focus and show the real Sumire IME") { activity ->
                    activity.findViewById<View>(R.id.setting_search_input)?.hasFocus() == true &&
                        KeyboardPreviewSelection.current.value?.type == KeyboardType.SUMIRE
                }
                assertEquals(KeyboardType.SUMIRE, KeyboardPreviewSelection.lastUsed(context)?.type)
                assertFalse(com.kazumaproject.markdownhelperkeyboard.keyboard_presentation.KeyboardTrialSession.active.value)
                instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                scenario.onActivity { host(it).navController.popBackStack() }
                scenario.awaitSettingsContentReady()
                await(scenario, "Live selected keyboard not shown on Home") { activity ->
                    activity.findViewById<TextView>(R.id.setting_home_preview_summary)?.text.toString() == activity.getString(R.string.settings_keyboard_selected) &&
                        activity.findViewById<View>(R.id.setting_home_preview)?.let { root ->
                            descendants(root).filterIsInstance<KeyboardPreviewView>().any { it.ready && it.target.type == KeyboardType.SUMIRE }
                        } == true
                }
                shot("home-selected")
                scenario.onActivity { AppPreference.keyboard_order = listOf(KeyboardType.TENKEY, KeyboardType.SUMIRE) }
                shell("ime set $oldIme")
                // Android may keep an unbound IME service cached. Explicitly clear volatile state
                // to exercise disk restoration independently of that lifecycle timing.
                scenario.onActivity { KeyboardPreviewSelection.publish(null) }
                scenario.recreate(); scenario.awaitSettingsContentReady()
                await(scenario, "Last-used Sumire was incorrectly replaced by the first keyboard") { activity ->
                    activity.findViewById<TextView>(R.id.setting_home_preview_summary)?.text.toString() == activity.getString(R.string.settings_keyboard_last_used) &&
                        activity.findViewById<View>(R.id.setting_home_preview)?.let { root ->
                            descendants(root).filterIsInstance<KeyboardPreviewView>().any { it.ready && it.target.type == KeyboardType.SUMIRE }
                        } == true
                }
                shot("home-last-used")
                scenario.onActivity { it.findViewById<View>(R.id.setting_home_keyboard_list).performClick() }
                await(scenario, "Keyboard library did not load") { it.findViewById<RecyclerView>(R.id.keyboard_selection_view)?.adapter?.itemCount == 8 }
                shot("keyboard-list")
                scenario.onActivity { host(it).navController.navigate(R.id.inputFlickPreferenceFragment) }
                scenario.awaitSettingsContentReady()
                await(scenario, "Input controls did not finish loading") {
                    (host(it).childFragmentManager.primaryNavigationFragment as? InputFlickPreferenceFragment)
                        ?.findPreference<SeekBarPreference>("flick_sensitivity_preference") != null
                }
                shot("input-flick")
            }
        } finally {
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
            KeyboardPreviewSelection.publish(null)
            snapshot.edit().clear().apply {
                previousSnapshot.forEach { (key, value) -> when (value) {
                    is String -> putString(key, value)
                    is Long -> putLong(key, value)
                    is Boolean -> putBoolean(key, value)
                } }
            }.commit()
            AppPreference.importAllFromJson(backup)
        }
    }

}
