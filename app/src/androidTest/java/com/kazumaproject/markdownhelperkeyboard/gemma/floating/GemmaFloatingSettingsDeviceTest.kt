package com.kazumaproject.markdownhelperkeyboard.gemma.floating

import android.content.Intent
import android.os.SystemClock
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.viewpager2.widget.ViewPager2
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.markdownhelperkeyboard.setting_activity.awaitSettingsContentReady
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.GemmaPreferenceFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingTabRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GemmaFloatingSettingsDeviceTest {
    /** Run write, force-stop the QA app, then run read with -e persistencePhase. */
    @Test fun rulesSurviveProcessRestartAndExistingBackup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue("Use the isolated QA application ID", context.packageName == "com.kazumaproject.gemmafloatingqa")
        val phase = InstrumentationRegistry.getArguments().getString("persistencePhase")
        assumeTrue("Explicit write/read process-restart probe", phase == "write" || phase == "read")
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        AppPreference.init(context)
        if (phase == "write") {
            preferences.edit().clear().commit()
            val settings = GemmaFloatingSettings(preferences)
            settings.ensureDefaults()
            settings.delete(settings.rules.first { it.id == "english" })
            settings.save(null, "Restart QA", "Keep both lines.\nPreserve meaning.")
            settings.select(settings.rules.last().id)
            preferences.edit().putBoolean(GemmaFloatingSettings.ENABLED, true).commit()
        } else {
            val settings = GemmaFloatingSettings(preferences)
            settings.ensureDefaults()
            assertTrue(settings.enabled)
            assertEquals("Restart QA", settings.selectedRule?.name)
            assertEquals("Keep both lines.\nPreserve meaning.", settings.selectedRule?.instruction)
            assertFalse(settings.rules.any { it.id == "english" })
            val expected = settings.rules
            val selected = settings.selectedId
            val backup = AppPreference.exportAllToJson()
            preferences.edit().clear().commit()
            AppPreference.importAllFromJson(backup)
            val restored = GemmaFloatingSettings(preferences)
            restored.ensureDefaults()
            assertEquals(expected, restored.rules)
            assertEquals(selected, restored.selectedId)
            assertTrue(restored.enabled)
        }
    }

    @Test fun ruleManagementDialogsAddEditSelectAndDelete() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("Use the isolated QA application ID", context.packageName == "com.kazumaproject.gemmafloatingqa")
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit().clear().putBoolean("setting_use_new_home_screen_preference", true).commit()
        AppPreference.init(context)
        val settings = GemmaFloatingSettings(preferences)
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.awaitSettingsContentReady()
            scenario.onActivity { activity ->
                (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment)
                    .navController.navigate(R.id.gemmaPreferenceFragment)
            }
            awaitGemma(scenario)
            fun manage() = scenario.onActivity {
                gemmaFragment(it).findPreference<Preference>(GemmaFloatingSettings.MANAGE)!!.performClick()
            }
            manage()
            onView(withText(R.string.gemma_floating_add)).inRoot(isDialog()).perform(click())
            onView(withHint(R.string.gemma_floating_name)).perform(replaceText("QA rule"))
            onView(withHint(R.string.gemma_floating_instruction)).perform(replaceText("Use polite Japanese."))
            closeSoftKeyboard()
            onView(withText(R.string.save_string)).inRoot(isDialog()).perform(click())
            val added = settings.rules.single { it.name == "QA rule" }
            scenario.onActivity { gemmaFragment(it).findPreference<ListPreference>(GemmaFloatingSettings.SELECTED)!!.performClick() }
            onView(withText("QA rule")).inRoot(isDialog()).perform(click())
            assertEquals(added.id, settings.selectedId)
            manage()
            onView(withText("QA rule")).inRoot(isDialog()).perform(click())
            onView(withText(R.string.gemma_floating_edit)).inRoot(isDialog()).perform(click())
            onView(withHint(R.string.gemma_floating_name)).perform(replaceText("QA edited"))
            closeSoftKeyboard()
            onView(withText(R.string.save_string)).inRoot(isDialog()).perform(click())
            assertEquals("QA edited", settings.selectedRule?.name)
            manage()
            onView(withText("QA edited")).inRoot(isDialog()).perform(click())
            onView(withText(R.string.gemma_floating_delete)).inRoot(isDialog()).perform(click())
            onView(withText(android.R.string.ok)).inRoot(isDialog()).perform(click())
            assertNull(settings.selectedRule)
            assertFalse(settings.rules.any { it.id == added.id })
        }
    }

    @Test fun bothGemmaEntryPointsPersistRuleSelectionAcrossRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("Use the isolated QA application ID", context.packageName == "com.kazumaproject.gemmafloatingqa")
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        for (newHome in listOf(true, false)) {
            preferences.edit().clear().putBoolean("setting_use_new_home_screen_preference", newHome).commit()
            AppPreference.init(context)
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.awaitSettingsContentReady()
                if (newHome) scenario.onActivity { activity ->
                    (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment)
                        .navController.navigate(R.id.gemmaPreferenceFragment)
                } else {
                    val index = SettingTabRegistry.createTabs().indexOfFirst { it.key == SettingTabRegistry.TAB_GEMMA }
                    assertTrue(index >= 0)
                    val deadline = SystemClock.uptimeMillis() + 30_000
                    var pagerReady = false
                    while (!pagerReady && SystemClock.uptimeMillis() < deadline) {
                        scenario.onActivity { pagerReady = it.findViewById<ViewPager2>(R.id.setting_view_pager) != null }
                        if (!pagerReady) SystemClock.sleep(50)
                    }
                    check(pagerReady) { "Legacy settings pager did not load" }
                    scenario.onActivity { activity ->
                        activity.findViewById<ViewPager2>(R.id.setting_view_pager).setCurrentItem(index, false)
                    }
                }
                awaitGemma(scenario)
                scenario.onActivity { activity ->
                    val fragment = gemmaFragment(activity)
                    assertFalse(fragment.findPreference<SwitchPreferenceCompat>(GemmaFloatingSettings.ENABLED)!!.isChecked)
                    assertNotNull(fragment.findPreference<Preference>(GemmaFloatingSettings.MANAGE))
                    val selection = fragment.findPreference<ListPreference>(GemmaFloatingSettings.SELECTED)!!
                    assertEquals("english", selection.value)
                    assertTrue(selection.entryValues.contains("modeless"))
                    selection.value = "modeless"
                }
                scenario.recreate()
                scenario.awaitSettingsContentReady()
                awaitGemma(scenario)
                scenario.onActivity { activity ->
                    assertEquals("modeless", gemmaFragment(activity).findPreference<ListPreference>(GemmaFloatingSettings.SELECTED)!!.value)
                }
            }
        }
    }

    private fun gemmaFragment(activity: MainActivity): GemmaPreferenceFragment {
        fun descendants(fragment: Fragment): Sequence<Fragment> = sequence {
            yield(fragment)
            fragment.childFragmentManager.fragments.forEach { yieldAll(descendants(it)) }
        }
        return activity.supportFragmentManager.fragments.asSequence().flatMap(::descendants)
            .filterIsInstance<GemmaPreferenceFragment>().first { it.isResumed }
    }

    private fun awaitGemma(scenario: ActivityScenario<MainActivity>) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity ->
                ready = runCatching { gemmaFragment(activity).findPreference<ListPreference>(GemmaFloatingSettings.SELECTED) != null }.getOrDefault(false)
            }
            if (!ready) SystemClock.sleep(50)
        }
        check(ready) { "Gemma preferences did not load" }
    }
}
