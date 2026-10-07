package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.os.SystemClock
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.number.*
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NumberCandidateSettingsInstrumentedTest {
    @Test fun bothSettingsPresentationsSaveReorderResetAndRestore() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue(context.packageName.endsWith(".freezeprobe"))
        AppPreference.awaitInitialization()
        val backup = AppPreference.exportAllToJson()
        try {
            for (newHome in listOf(false, true)) {
                AppPreference.setting_use_new_home_screen_preference = newHome
                AppPreference.number_candidate_config = NumberCandidateConfig()
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    scenario.awaitSettingsContentReady()
                    scenario.onActivity { activity ->
                        if (newHome) {
                            navHost(activity).navController.navigate(R.id.conversionEnginePreferenceFragment)
                        } else {
                            val index = SettingTabRegistry.createTabs().indexOfFirst { it.key == SettingTabRegistry.TAB_CONVERSION_ENGINE }
                            activity.findViewById<ViewPager2>(R.id.setting_view_pager).setCurrentItem(index, false)
                        }
                    }
                    await(scenario) { activity ->
                        val fragment = resumed<ConversionEnginePreferenceFragment>(activity)
                        fragment?.findPreference<SwitchPreferenceCompat>(AppPreference.NUMBER_COUNTER_CANDIDATES_ENABLED_KEY) != null &&
                            fragment.findPreference<Preference>("number_candidate_order_preference")?.onPreferenceClickListener != null &&
                            fragment.listView.childCount > 0
                    }
                    scenario.onActivity { activity ->
                        val fragment = resumed<ConversionEnginePreferenceFragment>(activity)!!
                        val toggle = fragment.findPreference<SwitchPreferenceCompat>(AppPreference.NUMBER_COUNTER_CANDIDATES_ENABLED_KEY)!!
                        assertTrue(toggle.isChecked)
                        toggle.isChecked = false
                        assertFalse(AppPreference.number_candidate_config.enhanceCounterCandidates)
                        val pref = fragment.findPreference<Preference>("number_candidate_order_preference")!!
                        assertTrue(pref.onPreferenceClickListener!!.onPreferenceClick(pref))
                    }
                    await(scenario) { resumed<NumberCandidateSettingsFragment>(it)?.view?.findViewById<RecyclerView>(R.id.number_formats)?.childCount == 3 }
                    scenario.onActivity { activity ->
                        val list = resumed<NumberCandidateSettingsFragment>(activity)!!.requireView().findViewById<RecyclerView>(R.id.number_formats)
                        val handle = list.getChildAt(2).findViewById<View>(R.id.drag_handle)
                        assertTrue(handle.performAccessibilityAction(R.id.number_candidate_action_move_up, null))
                        assertEquals(listOf(NumberCandidateFormat.HALF_WIDTH, NumberCandidateFormat.KANJI, NumberCandidateFormat.FULL_WIDTH), AppPreference.number_candidate_config.order)
                    }
                    scenario.recreate()
                    scenario.awaitSettingsContentReady()
                    await(scenario) { activity ->
                        val list = resumed<NumberCandidateSettingsFragment>(activity)?.view?.findViewById<RecyclerView>(R.id.number_formats)
                        list?.isLaidOut == true && list.childCount == 3
                    }
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                    // Wait for the recreated window's first frame to reach SurfaceFlinger.
                    SystemClock.sleep(150)
                    assertEquals(NumberCandidateFormat.KANJI, AppPreference.number_candidate_config.order[1])
                    InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
                        val directory = File(context.filesDir, "conversion-perf").apply { mkdirs() }
                        File(directory, "number-order-${if (newHome) "new" else "legacy"}.png").outputStream().use {
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                        }
                    }
                    scenario.onActivity { activity ->
                        resumed<NumberCandidateSettingsFragment>(activity)!!.requireView().findViewById<View>(R.id.reset_formats).performClick()
                    }
                    assertEquals(NumberCandidateFormat.entries.toList(), AppPreference.number_candidate_config.order)
                    assertFalse(AppPreference.number_candidate_config.enhanceCounterCandidates)
                }
            }
        } finally { AppPreference.importAllFromJson(backup) }
    }
    private fun navHost(activity: MainActivity) = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
    private inline fun <reified T : Fragment> resumed(activity: MainActivity): T? =
        activity.supportFragmentManager.fragments.firstNotNullOfOrNull { findResumed(it, T::class.java) } as? T

    private fun findResumed(fragment: Fragment, type: Class<out Fragment>): Fragment? =
        if (type.isInstance(fragment) && fragment.isResumed) fragment
        else fragment.childFragmentManager.fragments.firstNotNullOfOrNull { findResumed(it, type) }

    private fun await(scenario: ActivityScenario<MainActivity>, check: (MainActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { ready = check(it) }
            if (ready) return
            SystemClock.sleep(20)
        }
        fail("Settings view did not become ready")
    }
}
