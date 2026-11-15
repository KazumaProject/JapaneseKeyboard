package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.LegacyCommonPreferenceFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingSearchIndex
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingSearchScope
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PurposeSettingsPagesInstrumentedTest {
    private fun host(activity: MainActivity) = activity.supportFragmentManager
        .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
    private fun page(activity: MainActivity) = host(activity).childFragmentManager
        .primaryNavigationFragment as? LegacyCommonPreferenceFragment

    private fun await(scenario: ActivityScenario<MainActivity>, message: String, condition: (MainActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { ready = condition(it) }
            if (ready) return
            SystemClock.sleep(50)
        }
        fail(message)
    }

    @Test fun commonIndexOpensAllTenSectionsAndRestoresItsPositionInBothModes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue(context.packageName.endsWith(".settingsuxreview"))
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val mode = prefs.getBoolean("setting_use_new_home_screen_preference", true)
        try {
            for (newHome in listOf(false, true)) {
                prefs.edit().putBoolean("setting_use_new_home_screen_preference", newHome).commit()
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                    scenario.awaitSettingsContentReady()
                    scenario.onActivity { host(it).navController.navigate(R.id.legacyCommonPreferenceFragment) }
                    await(scenario, "Common index did not load") { page(it)?.preferenceScreen?.preferenceCount == 10 && page(it)?.listView?.adapter != null }
                    val keys = mutableListOf<String>()
                    scenario.onActivity { activity ->
                        val screen = page(activity)!!.preferenceScreen
                        repeat(screen.preferenceCount) { index ->
                            val item = screen.getPreference(index)
                            assertFalse("Index entries must be destinations", item is PreferenceGroup)
                            keys += item.key
                        }
                    }
                    var total = 0
                    for (key in keys) {
                        scenario.onActivity { page(it)!!.findPreference<Preference>(key)!!.performClick() }
                        await(scenario, "Section $key did not open") { host(it).navController.currentDestination?.id == R.id.legacyCommonSectionFragment && page(it)?.preferenceScreen?.preferenceCount == 1 && page(it)?.listView?.adapter != null }
                        scenario.onActivity { activity ->
                            val group = page(activity)!!.preferenceScreen.getPreference(0) as PreferenceGroup
                            assertEquals(key, group.key)
                            assertTrue(group.preferenceCount in 2..12)
                            assertFalse(group.isSelectable)
                            total += group.preferenceCount
                            assertTrue(host(activity).navController.popBackStack())
                        }
                        await(scenario, "Common index did not return") { page(it)?.preferenceScreen?.preferenceCount == 10 && page(it)?.listView?.adapter != null }
                    }
                    assertEquals("Every original setting must remain reachable", 64, total)
                    var position = 0
                    var offset = 0
                    scenario.onActivity { (page(it)!!.listView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(7, 0) }
                    SystemClock.sleep(250)
                    scenario.onActivity {
                        val manager = page(it)!!.listView.layoutManager as LinearLayoutManager
                        position = manager.findFirstVisibleItemPosition()
                        offset = manager.findViewByPosition(position)!!.top
                        page(it)!!.findPreference<Preference>(keys.last())!!.performClick()
                    }
                    await(scenario, "Last section did not load") { page(it)?.preferenceScreen?.preferenceCount == 1 && page(it)?.listView?.adapter != null }
                    scenario.recreate(); scenario.awaitSettingsContentReady()
                    await(scenario, "Section was lost during recreation") { page(it)?.preferenceScreen?.getPreference(0)?.key == keys.last() }
                    scenario.onActivity { assertTrue(host(it).navController.popBackStack()) }
                    await(scenario, "Index scroll position was lost") {
                        val manager = page(it)?.listView?.layoutManager as? LinearLayoutManager
                        page(it)?.preferenceScreen?.preferenceCount == 10 && manager?.findFirstVisibleItemPosition() == position && manager.findViewByPosition(position)?.top == offset
                    }
                }
            }
        } finally {
            prefs.edit().putBoolean("setting_use_new_home_screen_preference", mode).commit()
        }
    }

    @Test fun searchTargetsTheOwningSectionAndKeepsTheSavedPreferenceValue() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue(context.packageName.endsWith(".settingsuxreview"))
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val key = "flick_threshold_shape_preference"
        val original = prefs.getString(key, null)
        try {
            prefs.edit().putString(key, "rectangular").commit()
            for (scope in SettingSearchScope.entries) {
                val result = SettingSearchIndex.searchable(context, scope).first { it.key == key }
                val destination = result.legacyTarget?.destinationId
                    ?: com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingDestinations.destinationId(result.destination)
                assertEquals(R.id.legacyCommonSectionFragment, destination)
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                    scenario.awaitSettingsContentReady()
                    scenario.onActivity {
                        host(it).navController.navigate(destination!!, android.os.Bundle().apply { putString(com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.CommonPreferenceFragment.ARG_HIGHLIGHT_PREFERENCE_KEY, key) })
                    }
                    await(scenario, "Search did not open the owning section") { page(it)?.findPreference<Preference>(key) != null }
                    scenario.onActivity {
                        assertEquals("settings_common_touch", page(it)!!.preferenceScreen.getPreference(0).key)
                        assertEquals("rectangular", prefs.getString(key, null))
                    }
                }
            }
        } finally {
            prefs.edit().apply { if (original == null) remove(key) else putString(key, original) }.commit()
        }
    }
}
