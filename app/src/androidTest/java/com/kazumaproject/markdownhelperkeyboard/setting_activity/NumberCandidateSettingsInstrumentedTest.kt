package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Intent
import android.os.Bundle
import android.graphics.Bitmap
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.engine.*
import org.hamcrest.Matchers.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NumberCandidateSettingsInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var enabledImeForTest: String? = null

    @org.junit.Before fun enableKeyboardForSettings() {
        val manager = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        val ime = manager.inputMethodList.first { it.packageName == context.packageName }
        if (manager.enabledInputMethodList.none { it.id == ime.id }) {
            enabledImeForTest = ime.id
            shell("ime enable ${ime.id}")
            val deadline = android.os.SystemClock.uptimeMillis() + 5000
            while (manager.enabledInputMethodList.none { it.id == ime.id } && android.os.SystemClock.uptimeMillis() < deadline) android.os.SystemClock.sleep(100)
            assertTrue(manager.enabledInputMethodList.any { it.id == ime.id })
        }
    }

    @org.junit.After fun restoreKeyboardEnabledState() {
        enabledImeForTest?.let { shell("ime disable $it") }
    }

    private fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }

    private var uiContext: android.content.Context? = null
    private fun label(id: Int) = (uiContext ?: context).getString(id)
    private fun fill(id: Int, value: String) {
        onView(withHint(label(id))).perform(scrollTo(), replaceText(value), closeSoftKeyboard())
    }
    private fun click(id: Int) { onView(allOf(withText(label(id)), isAssignableFrom(android.widget.Button::class.java))).perform(scrollTo(), click()) }
    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(context.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun withLegacyEditor(existing: Boolean = false, block: (ActivityScenario<MainActivity>) -> Unit) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val oldConfig = preferences.getString("number_candidate_config_v1", null)
        val oldHome = preferences.getBoolean("setting_use_new_home_screen_preference", true)
        val units = if (existing) listOf(CustomNumberUnit("leave-test", "個", "こ")) else emptyList()
        preferences.edit().putBoolean("setting_use_new_home_screen_preference", false)
            .putString("number_candidate_config_v1", NumberCandidateConfig(units = units).encode()).commit()
        AppPreference.init(context)
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            scenario.onActivity {
                uiContext = it
                val nav = (it.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment).navController
                nav.navigate(R.id.numberCandidateSettingsFragment)
                nav.navigate(R.id.numberUnitEditorFragment, Bundle().apply { if (existing) putString("unitId", "leave-test") })
            }
            block(scenario)
        } finally {
            scenario.close()
            preferences.edit().putBoolean("setting_use_new_home_screen_preference", oldHome)
                .putString("number_candidate_config_v1", oldConfig).commit()
            AppPreference.init(context)
        }
    }

    private fun bottomTab(id: Int) = onView(allOf(withId(id), isDescendantOfA(withId(R.id.nav_view)))).perform(click())
    private fun assertDestination(scenario: ActivityScenario<MainActivity>, id: Int) = scenario.onActivity {
        val nav = (it.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment).navController
        assertEquals(id, nav.currentDestination?.id)
    }
    private fun discardDialog() = onView(withText(label(R.string.number_discard)))
        .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(isDisplayed()))

    @Test fun legacySettingsReselectionPreservesDraftAcrossRotation() = withLegacyEditor { scenario ->
        fill(R.string.number_unit_output, "セット")
        fill(R.string.number_unit_reading, "せっと")
        bottomTab(R.id.navigation_setting)
        discardDialog()
        scenario.onActivity {
            val bottom = it.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.nav_view)
            bottom.selectedItemId = R.id.navigation_setting
            bottom.selectedItemId = R.id.navigation_user_dictionary
            assertEquals(R.id.navigation_setting, bottom.selectedItemId)
        }
        scenario.recreate()
        discardDialog()
        onView(withId(android.R.id.button2)).perform(click())
        onView(withHint(label(R.string.number_unit_output))).check(matches(withText("セット")))
        assertDestination(scenario, R.id.numberUnitEditorFragment)
        assertTrue(AppPreference.number_candidate_config.units.isEmpty())
        bottomTab(R.id.navigation_setting)
        onView(withId(android.R.id.button1)).perform(click())
        assertDestination(scenario, R.id.settingMainFragment)
        assertTrue(AppPreference.number_candidate_config.units.isEmpty())
    }

    @Test fun otherBottomTabGuardsUnappliedSpecialReading() = withLegacyEditor(existing = true) { scenario ->
        click(R.string.number_special_add)
        onView(withHint(startsWith(label(R.string.number_special_value)))).perform(scrollTo(), replaceText("2"), closeSoftKeyboard())
        fill(R.string.number_special_reading, "にこ")
        bottomTab(R.id.navigation_user_dictionary)
        discardDialog()
        onView(withId(android.R.id.button2)).perform(click())
        onView(withHint(label(R.string.number_special_reading))).check(matches(withText("にこ")))
        assertDestination(scenario, R.id.numberUnitEditorFragment)
        bottomTab(R.id.navigation_user_dictionary)
        onView(withId(android.R.id.button1)).perform(click())
        assertDestination(scenario, R.id.navigation_user_dictionary)
        assertTrue(AppPreference.number_candidate_config.units.single().specialReadings.isEmpty())
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        scenario.onActivity {
            val nav = (it.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment).navController
            assertNotEquals(R.id.numberUnitEditorFragment, nav.currentDestination?.id)
        }
    }

    @Test fun backAndSettingsIntentsUseTheSameDiscardGuard() = withLegacyEditor(existing = true) { scenario ->
        fill(R.string.number_unit_output, "セット")
        scenario.onActivity { it.onSupportNavigateUp() }
        discardDialog()
        androidx.test.espresso.Espresso.pressBack()
        androidx.test.espresso.Espresso.pressBack()
        discardDialog()
        onView(withId(android.R.id.button2)).perform(click())
        scenario.onActivity {
            it.startActivity(Intent(it, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("openSettingActivity", "setting_fragment_request"))
        }
        discardDialog()
        onView(withId(android.R.id.button2)).perform(click())
        onView(withHint(label(R.string.number_unit_output))).check(matches(withText("セット")))
        scenario.onActivity {
            it.startActivity(Intent(it, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("openSettingActivity", "dictionary_fragment_request"))
        }
        discardDialog()
        onView(withId(android.R.id.button1)).perform(click())
        assertDestination(scenario, R.id.navigation_learn_dictionary)
        assertEquals("個", AppPreference.number_candidate_config.units.single().output)
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        assertDestination(scenario, R.id.numberCandidateSettingsFragment)
    }

    @Test fun unchangedAndSavedUnitsLeaveWithoutDiscardConfirmation() {
        withLegacyEditor(existing = true) { scenario ->
            bottomTab(R.id.navigation_setting)
            assertDestination(scenario, R.id.settingMainFragment)
        }
        withLegacyEditor(existing = true) { scenario ->
            fill(R.string.number_unit_output, "セット")
            click(R.string.number_save)
            assertDestination(scenario, R.id.numberCandidateSettingsFragment)
            assertEquals("セット", AppPreference.number_candidate_config.units.single().output)
        }
    }

    @Test fun registerPreviewRotateEditAndDeleteAnOfflineUnit() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val original = preferences.getString("number_candidate_config_v1", null)
        preferences.edit().remove("number_candidate_config_v1").commit()
        AppPreference.init(context)
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            scenario.onActivity {
                uiContext = it
                val nav = (it.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment).navController
                nav.navigate(R.id.numberCandidateSettingsFragment)
            }
            onView(withText((uiContext ?: context).getString(R.string.number_type_row, label(R.string.number_kind_time), label(R.string.number_example_time)))).perform(click())
            assertTrue(NumberCandidateKind.TIME in AppPreference.number_candidate_config.disabledKinds)
            onView(withText((uiContext ?: context).getString(R.string.number_type_row, label(R.string.number_kind_time), label(R.string.number_example_time)))).perform(click())
            onView(withText(label(R.string.number_custom_units))).perform(click())
            click(R.string.number_add_unit)
            capture("number-unit-empty")
            fill(R.string.number_unit_output, "個")
            fill(R.string.number_unit_reading, "こ")
            fill(R.string.number_try_reading, "にこ")
            click(R.string.number_special_add)
            onView(withHint(startsWith(label(R.string.number_special_value)))).perform(scrollTo(), replaceText("1"), closeSoftKeyboard())
            fill(R.string.number_special_reading, "いっこ")
            onView(withText("いっこ → 1個・１個・一個")).perform(scrollTo()).check(matches(isDisplayed()))
            capture("number-special-preview")
            onView(withHint(label(R.string.number_special_reading))).perform(scrollTo(), click())
            scenario.onActivity { activity ->
                (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .showSoftInput(activity.currentFocus, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
            var keyboardVisible = false
            val keyboardDeadline = android.os.SystemClock.uptimeMillis() + 5000
            while (!keyboardVisible && android.os.SystemClock.uptimeMillis() < keyboardDeadline) {
                scenario.onActivity { keyboardVisible = androidx.core.view.ViewCompat.getRootWindowInsets(it.window.decorView)
                    ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true }
                android.os.SystemClock.sleep(100)
            }
            assertTrue("The editor layout must be tested with the software keyboard visible", keyboardVisible)
            onView(withText("いっこ → 1個・１個・一個")).perform(scrollTo()).check(matches(isDisplayed()))
            capture("number-special-keyboard")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            scenario.recreate()
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            onView(withText("いっこ → 1個・１個・一個")).perform(scrollTo()).check(matches(isDisplayed()))
            click(R.string.number_apply_reading)
            for (whole in listOf("はっこ", "はちこ")) {
                click(R.string.number_special_add)
                onView(withHint(startsWith(label(R.string.number_special_value)))).perform(scrollTo(), replaceText("8"), closeSoftKeyboard())
                fill(R.string.number_special_reading, whole)
                click(R.string.number_apply_reading)
            }
            click(R.string.number_special_add)
            onView(withHint(startsWith(label(R.string.number_special_value)))).perform(scrollTo(), replaceText("7"), closeSoftKeyboard())
            fill(R.string.number_special_reading, "いっこ")
            click(R.string.number_apply_reading)
            onView(withText(label(R.string.number_duplicate_reading))).perform(scrollTo()).check(matches(isDisplayed()))
            click(R.string.number_cancel)
            for (whole in listOf("はっこ", "はちこ")) {
                onView(withText(label(R.string.number_delete) + "：" + whole)).perform(scrollTo(), click())
                onView(withId(android.R.id.button1)).perform(click())
            }
            fill(R.string.number_try_reading, "いっこ")
            onView(withText("いっこ → 1個・１個・一個")).perform(scrollTo()).check(matches(isDisplayed()))
            fill(R.string.number_try_reading, "じゅういっこ")
            onView(withText(label(R.string.number_try_no_match))).perform(scrollTo()).check(matches(isDisplayed()))
            click(R.string.number_save)
            val unit = AppPreference.number_candidate_config.units.single()
            assertEquals(listOf(SpecialNumberReading(1, "いっこ")), unit.specialReadings)
            scenario.recreate()
            onView(withContentDescription(label(R.string.number_edit) + "：個")).perform(scrollTo(), click())
            fill(R.string.number_unit_output, "セット")
            scenario.onActivity { it.onSupportNavigateUp() }
            capture("number-discard-dialog")
            onView(withText(label(R.string.number_discard))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(isDisplayed()))
            onView(withId(android.R.id.button2)).perform(click())
            click(R.string.number_save)
            assertEquals("セット", AppPreference.number_candidate_config.units.single().output)
            onView(withContentDescription(label(R.string.number_edit) + "：セット")).perform(scrollTo(), click())
            click(R.string.number_delete)
            onView(withText(label(R.string.number_delete_unit))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(isDisplayed()))
            onView(withId(android.R.id.button1)).perform(click())
            assertTrue(AppPreference.number_candidate_config.units.isEmpty())
        } finally {
            scenario.close()
            preferences.edit().apply { if (original == null) remove("number_candidate_config_v1") else putString("number_candidate_config_v1", original) }.commit()
            AppPreference.init(context)
        }
    }
    @Test fun editingSpecialReadingKeepsInvalidUnitFieldsVisible() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val original = preferences.getString("number_candidate_config_v1", null)
        AppPreference.init(context)
        val unit = CustomNumberUnit("pieces", "個", "こ", specialReadings = listOf(SpecialNumberReading(1, "いっこ")))
        AppPreference.number_candidate_config = NumberCandidateConfig(units = listOf(unit))
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.onActivity {
                    uiContext = it
                    (it.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment)
                        .navController.navigate(R.id.numberUnitEditorFragment, Bundle().apply { putString("unitId", unit.id) })
                }
                fill(R.string.number_unit_output, "")
                onView(withContentDescription(label(R.string.number_edit) + "：いっこ")).perform(scrollTo(), click())
                onView(withText(label(R.string.number_invalid_output))).perform(scrollTo()).check(matches(isDisplayed()))
                fill(R.string.number_unit_output, "個")
                fill(R.string.number_unit_reading, "")
                onView(withContentDescription(label(R.string.number_edit) + "：いっこ")).perform(scrollTo(), click())
                onView(withText(label(R.string.number_invalid_reading))).perform(scrollTo()).check(matches(isDisplayed()))
            }
        } finally {
            preferences.edit().apply { if (original == null) remove("number_candidate_config_v1") else putString("number_candidate_config_v1", original) }.commit()
            AppPreference.init(context)
        }
    }

    @Test fun tabsAndCompositionSurviveRecreationAndRemainIndependent() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val original = preferences.getString("number_candidate_config_v1", null)
        AppPreference.init(context)
        AppPreference.number_candidate_config = NumberCandidateConfig()
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.onActivity {
                    uiContext = it
                    (it.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment)
                        .navController.navigate(R.id.numberCandidateSettingsFragment)
                }
                capture("number-built-in-tab")
                onView(withText(startsWith("個\n"))).perform(scrollTo(), click())
                assertTrue("pieces" in AppPreference.number_candidate_config.disabledCounters)
                onView(withText(label(R.string.number_custom_units))).perform(click())
                onView(withText(label(R.string.number_units_empty))).check(matches(isDisplayed()))
                scenario.recreate()
                click(R.string.number_add_unit)
                fill(R.string.number_unit_output, "個")
                fill(R.string.number_unit_reading, "こ")
                click(R.string.number_special_add)
                onView(withHint(startsWith(label(R.string.number_special_value)))).perform(scrollTo(), replaceText("1"), closeSoftKeyboard())
                fill(R.string.number_special_reading, "いっこ")
                onView(withText(label(R.string.number_mode_compose))).perform(scrollTo(), click())
                onView(withHint(label(R.string.number_base_reading))).check(matches(withText("いち")))
                fill(R.string.number_base_reading, "に")
                click(R.string.number_apply_reading)
                onView(withText(label(R.string.number_invalid_base))).perform(scrollTo()).check(matches(isDisplayed()))
                fill(R.string.number_base_reading, "いち")
                scenario.recreate()
                onView(withText(label(R.string.number_mode_compose))).perform(scrollTo()).check(matches(isChecked()))
                onView(withHint(label(R.string.number_base_reading))).check(matches(withText("いち")))
                capture("number-compose-rule")
                click(R.string.number_apply_reading)
                fill(R.string.number_try_reading, "じゅういっこ")
                onView(withText("じゅういっこ → 11個・１１個・十一個")).perform(scrollTo()).check(matches(isDisplayed()))
                click(R.string.number_save)
                assertEquals(SpecialNumberReading(1, "いっこ", SpecialNumberReadingMode.COMPOSE, "いち"),
                    AppPreference.number_candidate_config.units.single().specialReadings.single())
                capture("number-user-tab")
                val enabledDescription = (uiContext ?: context).getString(R.string.number_unit_enabled, "個")
                onView(withContentDescription(enabledDescription)).perform(click())
                assertFalse(AppPreference.number_candidate_config.units.single().enabled)
                click(R.string.number_add_unit)
                scenario.onActivity { it.onSupportNavigateUp() }
                onView(withContentDescription(enabledDescription)).perform(click())
                assertTrue(AppPreference.number_candidate_config.units.single().enabled)
                scenario.recreate()
                onView(withContentDescription(label(R.string.number_edit) + "：個")).perform(scrollTo(), click())
                fill(R.string.number_try_reading, "じゅういっこ")
                onView(withText("じゅういっこ → 11個・１１個・十一個")).perform(scrollTo()).check(matches(isDisplayed()))
                assertTrue("pieces" in AppPreference.number_candidate_config.disabledCounters)
            }
        } finally {
            preferences.edit().apply { if (original == null) remove("number_candidate_config_v1") else putString("number_candidate_config_v1", original) }.commit()
            AppPreference.init(context)
        }
    }

}
