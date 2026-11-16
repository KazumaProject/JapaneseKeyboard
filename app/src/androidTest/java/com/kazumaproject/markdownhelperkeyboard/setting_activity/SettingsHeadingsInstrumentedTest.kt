package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.CollapsiblePreferenceCategory
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsHeadingsInstrumentedTest {
    private fun headings(group: PreferenceGroup): List<CollapsiblePreferenceCategory> = buildList {
        for (index in 0 until group.preferenceCount) {
            val child = group.getPreference(index)
            if (child is CollapsiblePreferenceCategory) add(child)
            if (child is PreferenceGroup) addAll(headings(child))
        }
    }
    @Test fun bothModesUseNonInteractiveAlwaysOpenHeadingsAcrossSettingsAndRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue(context.packageName.endsWith(".settingsuxreview"))
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val mode = prefs.getBoolean("setting_use_new_home_screen_preference", true)
        val expansion = context.getSharedPreferences(CollapsiblePreferenceCategory.STATE_PREFERENCES, Context.MODE_PRIVATE)
        val hadState = expansion.contains("settings_common_selection")
        val savedState = expansion.getBoolean("settings_common_selection", false)
        try {
            expansion.edit().putBoolean("settings_common_selection", false).commit()
            for (newHome in listOf(false, true)) {
                prefs.edit().putBoolean("setting_use_new_home_screen_preference", newHome).commit()
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                    scenario.awaitSettingsContentReady()
                    for (destination in listOf(R.id.legacyCommonSectionFragment, R.id.keyboardThemeFragment,
                        R.id.conversionEnginePreferenceFragment, R.id.dictionaryPreferenceFragment)) {
                        scenario.onActivity { activity -> (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment).navController.navigate(destination, if (destination == R.id.legacyCommonSectionFragment) android.os.Bundle().apply { putString("legacy_common_section_key", "settings_common_selection") } else null) }
                        fun verify(): Boolean {
                            var ready = false
                            scenario.onActivity { activity ->
                                val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                                val owner = host.childFragmentManager.primaryNavigationFragment as? PreferenceFragmentCompat
                                val screen = owner?.preferenceScreen
                                if (host.navController.currentDestination?.id == destination && screen != null && owner.listView.adapter != null) {
                                    val groups = headings(screen)
                                    ready = groups.isNotEmpty()
                                    groups.forEach { group ->
                                        assertTrue(group.isExpanded); assertFalse(group.isSelectable); assertEquals(0, group.widgetLayoutResource)
                                        val before = (0 until group.preferenceCount).associate { group.getPreference(it) to group.getPreference(it).isVisible }
                                        group.performClick(); group.setExpanded(false)
                                        before.forEach { (child, visible) -> assertEquals(child.key, visible, child.isVisible) }
                                    }
                                }
                            }
                            return ready
                        }
                        val deadline = SystemClock.uptimeMillis() + 20_000
                        while (!verify() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
                        assertTrue("Missing headings: mode=$newHome destination=$destination", verify())
                    }
                    scenario.recreate(); scenario.awaitSettingsContentReady()
                    scenario.onActivity { assertFalse("Heading rendering must not rewrite old expansion preferences", expansion.getBoolean("settings_common_selection", false)) }
                }
            }
        } finally {
            prefs.edit().putBoolean("setting_use_new_home_screen_preference", mode).commit()
            expansion.edit().apply { if (hadState) putBoolean("settings_common_selection", savedState) else remove("settings_common_selection") }.commit()
        }
    }
}
