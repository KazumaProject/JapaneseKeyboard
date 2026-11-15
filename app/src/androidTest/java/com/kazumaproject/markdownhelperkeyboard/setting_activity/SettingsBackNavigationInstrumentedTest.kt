package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.tabs.TabLayout
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.DictionaryPreferenceFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingTabRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsBackNavigationInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun host(activity: MainActivity) = activity.supportFragmentManager
        .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment

    private fun fragments(fragment: Fragment): Sequence<Fragment> = sequence {
        yield(fragment)
        fragment.childFragmentManager.fragments.forEach { yieldAll(fragments(it)) }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }

    private fun ready(fragment: PreferenceFragmentCompat?): Boolean = fragment?.view?.let { view ->
        fragment.isResumed && fragment.preferenceScreen != null && fragment.listView.adapter != null &&
            descendants(view).none { it.id == R.id.settings_loading_overlay && it.isShown }
    } == true

    private fun await(scenario: ActivityScenario<MainActivity>, message: String, condition: (MainActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            var satisfied = false
            scenario.onActivity { satisfied = condition(it) }
            if (satisfied) return
            SystemClock.sleep(40)
        }
        fail(message)
    }

    private fun keys(group: PreferenceGroup): List<String?> = (0 until group.preferenceCount).flatMap { index ->
        val child = group.getPreference(index)
        listOf(child.key) + if (child is PreferenceGroup) keys(child) else emptyList()
    }

    private fun withHomeMode(newHome: Boolean, block: (ActivityScenario<MainActivity>) -> Unit) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val previous = prefs.getBoolean("setting_use_new_home_screen_preference", true)
        try {
            prefs.edit().putBoolean("setting_use_new_home_screen_preference", newHome).commit()
            AppPreference.init(context)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.awaitSettingsContentReady()
                block(scenario)
            }
        } finally {
            prefs.edit().putBoolean("setting_use_new_home_screen_preference", previous).commit()
            AppPreference.init(context)
        }
    }

    private fun dictionary(activity: MainActivity) = fragments(host(activity))
        .filterIsInstance<DictionaryPreferenceFragment>().firstOrNull { it.isResumed }

    private fun checkDictionaryReturns(newHome: Boolean) = withHomeMode(newHome) { scenario ->
        if (newHome) {
            scenario.onActivity { host(it).navController.navigate(R.id.dictionaryPreferenceFragment) }
        } else {
            scenario.onActivity { host(it).navController.navigate(R.id.settingMainFragment) }
            await(scenario, "Legacy tabs did not load") { it.findViewById<TabLayout>(R.id.setting_tab_layout) != null }
            scenario.onActivity { activity ->
                val index = SettingTabRegistry.createTabs().indexOfFirst { it.key == SettingTabRegistry.TAB_DICTIONARY }
                activity.findViewById<TabLayout>(R.id.setting_tab_layout).getTabAt(index)!!.select()
            }
        }
        await(scenario, "Dictionary settings did not load") { ready(dictionary(it)) }
        var original = emptyList<String?>()
        scenario.onActivity {
            original = keys(dictionary(it)!!.preferenceScreen)
            assertEquals(1, original.count { key -> key == "classified_dictionary_manager" })
        }
        repeat(5) { pass ->
            scenario.onActivity { dictionary(it)!!.findPreference<androidx.preference.Preference>("classified_dictionary_manager")!!.performClick() }
            await(scenario, "Classified dictionaries did not open") {
                host(it).navController.currentDestination?.id == R.id.classifiedDictionarySettingsFragment &&
                    (it.findViewById<RecyclerView>(R.id.classified_list)?.adapter?.itemCount ?: 0) > 0
            }
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            await(scenario, "Dictionary settings did not return") { ready(dictionary(it)) }
            scenario.onActivity {
                assertEquals(1, keys(dictionary(it)!!.preferenceScreen).count { key -> key == "classified_dictionary_manager" })
                assertEquals("Dictionary settings changed after return ${pass + 1}", original, keys(dictionary(it)!!.preferenceScreen))
            }
        }
        if (!newHome) repeat(3) {
            scenario.onActivity { it.findViewById<TabLayout>(R.id.setting_tab_layout).getTabAt(0)!!.select() }
            await(scenario, "Dictionary tab did not become inactive") { dictionary(it) == null }
            scenario.onActivity { activity ->
                val index = SettingTabRegistry.createTabs().indexOfFirst { it.key == SettingTabRegistry.TAB_DICTIONARY }
                activity.findViewById<TabLayout>(R.id.setting_tab_layout).getTabAt(index)!!.select()
            }
            await(scenario, "Dictionary tab did not return") { ready(dictionary(it)) }
            scenario.onActivity { assertEquals(original, keys(dictionary(it)!!.preferenceScreen)) }
        }
        scenario.recreate()
        scenario.awaitSettingsContentReady()
        await(scenario, "Dictionary settings were not restored") { ready(dictionary(it)) }
        scenario.onActivity { assertEquals(original, keys(dictionary(it)!!.preferenceScreen)) }
    }

    @Test fun legacyDictionaryTabDoesNotAccumulateRowsAfterBackAndRecreation() = checkDictionaryReturns(false)

    @Test fun newDictionarySettingsDoNotAccumulateRowsAfterBackAndRecreation() = checkDictionaryReturns(true)

    private fun checkOtherPreferenceScreens(newHome: Boolean) = withHomeMode(newHome) { scenario ->
        val destinations = SettingTabRegistry.createTabs().filter { it.xmlRes != null && it.key != SettingTabRegistry.TAB_DICTIONARY }
            .map { it.key to it.destinationId } + listOf(
            "external_dictionary" to R.id.externalDictionarySettingsFragment,
            "input_flick" to R.id.inputFlickPreferenceFragment,
            "utility_candidates" to R.id.utilityCandidatePreferenceFragment,
            "common" to R.id.commonPreferenceFragment,
            "keyboard_display" to R.id.keyboardDisplayPreferenceFragment,
            "input_method" to R.id.inputMethodPreferenceFragment,
            "candidate_conversion" to R.id.candidateConversionPreferenceFragment,
            "candidate_display" to R.id.candidateDisplayPreferenceFragment,
            "candidate_indicator" to R.id.candidateIndicatorPreferenceFragment,
            "ai_conversion" to R.id.aiConversionPreferenceFragment,
            "ai_voice" to R.id.aiVoicePreferenceFragment,
            "external_keyboard" to R.id.externalKeyboardPreferenceFragment,
            "operation_feedback" to R.id.operationFeedbackPreferenceFragment,
            "clipboard_shortcut" to R.id.clipboardShortcutPreferenceFragment,
            "general_info" to R.id.generalInfoPreferenceFragment,
            "advanced" to R.id.advancedPreferenceFragment,
            "free_flick" to R.id.freeFlickSettingsFragment,
            "fever" to R.id.feverSettingsFragment,
        )
        for ((name, destination) in destinations) {
            scenario.onActivity { host(it).navController.navigate(destination) }
            fun current(activity: MainActivity) = host(activity).childFragmentManager.primaryNavigationFragment as? PreferenceFragmentCompat
            await(scenario, "$name did not load") { ready(current(it)) }
            var original = emptyList<String?>()
            scenario.onActivity { original = keys(current(it)!!.preferenceScreen) }
            repeat(3) { pass ->
                scenario.onActivity { host(it).navController.navigate(R.id.classifiedDictionarySettingsFragment) }
                await(scenario, "Child screen did not open for $name") {
                    host(it).navController.currentDestination?.id == R.id.classifiedDictionarySettingsFragment &&
                        (it.findViewById<RecyclerView>(R.id.classified_list)?.adapter?.itemCount ?: 0) > 0
                }
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                await(scenario, "$name did not return") { ready(current(it)) }
                scenario.onActivity { assertEquals("$name changed after return ${pass + 1}", original, keys(current(it)!!.preferenceScreen)) }
            }
            scenario.recreate()
            scenario.awaitSettingsContentReady()
            await(scenario, "$name was not restored after recreation") { ready(current(it)) }
            scenario.onActivity { assertEquals("$name changed after recreation", original, keys(current(it)!!.preferenceScreen)) }
            android.util.Log.i("SettingsReentryAudit", "PASS mode=$newHome screen=$name rows=${original.size} returns=3 recreation=1")
            scenario.onActivity { assertTrue(host(it).navController.popBackStack()) }
        }
    }

    @Test fun legacyPreferenceScreensKeepTheirRowsAcrossBackAndRecreation() = checkOtherPreferenceScreens(false)

    @Test fun newPreferenceScreensKeepTheirRowsAcrossBackAndRecreation() = checkOtherPreferenceScreens(true)

    @Test fun newHomeKeepsOnePreviewAndStableCardCountsAfterBackAndRecreation() = withHomeMode(true) { scenario ->
        fun counts(activity: MainActivity) = listOf(
            R.id.setting_home_preview, R.id.setting_home_frequent_grid,
            R.id.setting_home_category_list, R.id.setting_home_management_list,
        ).map { activity.findViewById<ViewGroup>(it).childCount }
        fun homeReady(activity: MainActivity): Boolean = activity.findViewById<View>(R.id.setting_home_content)?.let { root ->
            descendants(root).none { it.id == R.id.settings_loading_overlay && it.isShown } &&
                activity.findViewById<ViewGroup>(R.id.setting_home_frequent_grid).childCount > 0
        } == true
        await(scenario, "New home did not load") { homeReady(it) }
        var original = emptyList<Int>()
        scenario.onActivity { original = counts(it); assertEquals(1, original.first()) }
        repeat(5) {
            scenario.onActivity { it.findViewById<View>(R.id.setting_home_keyboard_list).performClick() }
            await(scenario, "Keyboard list did not open") { host(it).navController.currentDestination?.id == R.id.keyboardSelectionFragment }
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            await(scenario, "New home did not return") { homeReady(it) }
            scenario.onActivity { assertEquals(original, counts(it)) }
        }
        scenario.recreate()
        scenario.awaitSettingsContentReady()
        await(scenario, "New home was not restored") { homeReady(it) }
        scenario.onActivity { assertEquals(original, counts(it)) }
    }
}
