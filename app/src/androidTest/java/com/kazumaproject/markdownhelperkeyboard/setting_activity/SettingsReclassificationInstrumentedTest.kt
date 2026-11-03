package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme.KeyboardThemeFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_geometry.CandidateDisplayPreferenceFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.CollapsiblePreferenceCategory
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.CommonPreferenceFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.DictionaryPreferenceFragment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsReclassificationInstrumentedTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun shell(command: String) = ins.uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim()
    }
    private fun await(message: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 90_000
        while (SystemClock.uptimeMillis() < end) {
            var ready = false
            ins.runOnMainSync { ready = condition() }
            if (ready) return
            SystemClock.sleep(50)
        }
        error(message)
    }

    @Test fun searchExpandsThemeAndCrossScreenDependenciesRefreshOnReturn() {
        val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val enabledBefore = shell("ime list -s").lines().contains(ime)
        shell("ime enable $ime")
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val original = prefs.all.toMap()
        val expansion = context.getSharedPreferences(CollapsiblePreferenceCategory.STATE_PREFERENCES, Context.MODE_PRIVATE)
        val originalExpansion = expansion.all.toMap()
        try {
            prefs.edit().putBoolean("live_conversion_preference", false)
                .putBoolean("live_conversion_candidate_yomi_preference", true)
                .putBoolean("composing_guide_text_enabled", false)
                .putBoolean("learn_dictionary_preference", false)
                .putBoolean("user_dictionary_preference", false)
                .putBoolean("enable_prediction_search_learn_dictionary_preference", true).commit()
            expansion.edit().putBoolean("settings_theme_text", false).commit()
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { scenario ->
                lateinit var activity: MainActivity
                scenario.onActivity { activity = it }
                await("Settings home not ready") { activity.isSettingsContentReady }
                fun host() = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                fun theme() = host().childFragmentManager.primaryNavigationFragment as? KeyboardThemeFragment
                fun candidate() = host().childFragmentManager.primaryNavigationFragment as? CandidateDisplayPreferenceFragment
                fun highlight(key: String) = Bundle().apply { putString(CommonPreferenceFragment.ARG_HIGHLIGHT_PREFERENCE_KEY, key) }
                ins.runOnMainSync { host().navController.navigate(R.id.candidateDisplayPreferenceFragment, highlight("live_conversion_candidate_yomi_preference")) }
                await("Candidate settings did not load") { candidate()?.findPreference<Preference>("live_conversion_candidate_yomi_preference") != null && candidate()?.listView?.adapter != null }
                ins.runOnMainSync {
                    val current = candidate()!!
                    assertFalse(current.findPreference<Preference>("live_conversion_candidate_yomi_preference")!!.isEnabled)
                    assertTrue(current.findPreference<Preference>("settings_theme_enable_live_conversion_link")!!.isVisible)
                    assertFalse(expansion.getBoolean("settings_theme_text", false))
                    host().navController.navigate(R.id.conversionEnginePreferenceFragment, highlight("live_conversion_preference"))
                }
                await("Conversion screen did not load") {
                    (host().childFragmentManager.primaryNavigationFragment as? CommonPreferenceFragment)
                        ?.findPreference<SwitchPreferenceCompat>("live_conversion_preference") != null
                }
                ins.runOnMainSync {
                    val engine = host().childFragmentManager.primaryNavigationFragment as CommonPreferenceFragment
                    engine.findPreference<SwitchPreferenceCompat>("live_conversion_preference")!!.isChecked = true
                    assertTrue(host().navController.popBackStack())
                }
                await("Candidate dependency did not refresh") { candidate()?.findPreference<Preference>("live_conversion_candidate_yomi_preference")?.isEnabled == true }
                ins.runOnMainSync {
                    assertTrue(candidate()!!.findPreference<SwitchPreferenceCompat>("live_conversion_candidate_yomi_preference")!!.isChecked)
                    assertFalse(candidate()!!.findPreference<Preference>("settings_theme_enable_live_conversion_link")!!.isVisible)
                    host().navController.navigate(R.id.keyboardThemeFragment, highlight("composing_guide_text_size_setting"))
                }
                await("Theme did not load") { theme()?.findPreference<Preference>("composing_guide_text_size_setting") != null && theme()?.listView?.adapter != null }
                ins.runOnMainSync {
                    assertTrue(theme()!!.findPreference<CollapsiblePreferenceCategory>("settings_theme_text")!!.isExpanded)
                    assertFalse(expansion.getBoolean("settings_theme_text", false))
                    host().navController.navigate(R.id.legacyCommonPreferenceFragment, highlight("composing_guide_text_enabled"))
                }
                await("Common screen did not load") {
                    (host().childFragmentManager.primaryNavigationFragment as? CommonPreferenceFragment)
                        ?.findPreference<SwitchPreferenceCompat>("composing_guide_text_enabled") != null
                }
                ins.runOnMainSync {
                    val common = host().childFragmentManager.primaryNavigationFragment as CommonPreferenceFragment
                    common.findPreference<SwitchPreferenceCompat>("composing_guide_text_enabled")!!.isChecked = true
                    assertTrue(host().navController.popBackStack())
                }
                await("Composing dependency did not refresh") { theme()?.findPreference<Preference>("composing_guide_text_size_setting")?.isEnabled == true }
                ins.runOnMainSync {
                    host().navController.navigate(R.id.conversionEnginePreferenceFragment, highlight("learn_prediction_preference"))
                }
                fun engine() = host().childFragmentManager.primaryNavigationFragment as? CommonPreferenceFragment
                await("Prediction dictionaries did not load") {
                    engine()?.findPreference<Preference>("learn_prediction_preference") != null
                }
                ins.runOnMainSync {
                    assertFalse(engine()!!.findPreference<Preference>("learn_prediction_preference")!!.isEnabled)
                    assertFalse(engine()!!.findPreference<Preference>("user_dictionary_prefix_match_number")!!.isEnabled)
                    assertTrue(engine()!!.findPreference<Preference>("settings_engine_enable_learning_dictionary_link")!!.isVisible)
                    host().navController.navigate(R.id.dictionaryPreferenceFragment, highlight("learn_dictionary_preference"))
                }
                fun dictionary() = host().childFragmentManager.primaryNavigationFragment as? DictionaryPreferenceFragment
                await("Dictionary settings did not load") {
                    dictionary()?.findPreference<SwitchPreferenceCompat>("learn_dictionary_preference") != null
                }
                ins.runOnMainSync {
                    dictionary()!!.findPreference<SwitchPreferenceCompat>("learn_dictionary_preference")!!.isChecked = true
                    dictionary()!!.findPreference<SwitchPreferenceCompat>("user_dictionary_preference")!!.isChecked = true
                    assertTrue(host().navController.popBackStack())
                }
                await("Dictionary dependencies did not refresh") {
                    engine()?.findPreference<Preference>("learn_prediction_preference")?.isEnabled == true &&
                        engine()?.findPreference<Preference>("user_dictionary_prefix_match_number")?.isEnabled == true
                }
            }
        } finally {
            fun restore(target: android.content.SharedPreferences, values: Map<String, *>) {
                val edit = target.edit().clear()
                values.forEach { (k, v) -> when (v) {
                    is Boolean -> edit.putBoolean(k, v)
                    is Int -> edit.putInt(k, v)
                    is Long -> edit.putLong(k, v)
                    is Float -> edit.putFloat(k, v)
                    is String -> edit.putString(k, v)
                    is Set<*> -> edit.putStringSet(k, v.filterIsInstance<String>().toSet())
                } }
                edit.commit()
            }
            restore(prefs, original)
            restore(expansion, originalExpansion)
            if (!enabledBefore) shell("ime disable $ime")
        }
    }
}
