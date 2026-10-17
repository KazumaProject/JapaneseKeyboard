package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.os.SystemClock
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.ListPreference
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SumirePreferenceFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises AndroidX's real preference dialogs, including their rendered choice lists. */
@RunWith(AndroidJUnit4::class)
class SumireFlickSettingsDialogInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences get() = PreferenceManager.getDefaultSharedPreferences(context)

    @Test
    fun rotationAndInputFeaturesDialogsShowChoicesAndPersistSelections() {
        assumeTrue("Only change the isolated Sumire verification package",
            context.packageName.startsWith("com.kazumaproject.sumirerotation535"))
        AppPreference.awaitInitialization()
        val originalBackup = AppPreference.exportAllToJson()
        try {
            check(preferences.edit()
                .putString("sumire_keyboard_style_preference", "third-flick")
                .putString(AppPreference.SUMIRE_FLICK_INPUT_MODE_KEY, "contracted")
                .putString(AppPreference.SUMIRE_FLICK_ROTATION_KEY, "default")
                .commit())
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.awaitSettingsContentReady()
                scenario.onActivity { host(it).navController.navigate(R.id.sumirePreferenceFragment) }
                await("Sumire preferences did not finish loading") {
                    var ready = false
                    scenario.onActivity {
                        val fragment = sumire(it) ?: return@onActivity
                        ready = fragment.findPreference<ListPreference>(AppPreference.SUMIRE_FLICK_INPUT_MODE_KEY)
                            ?.isVisible == true && fragment.listView.childCount > 0
                    }
                    ready
                }
                assertEquals("contracted", preferences.getString(AppPreference.SUMIRE_FLICK_INPUT_MODE_KEY, null))

                // Rotation remains configurable while contracted-only input is selected.
                selectVisibleChoice(scenario, AppPreference.SUMIRE_FLICK_ROTATION_KEY, "clockwise")
                selectVisibleChoice(scenario, AppPreference.SUMIRE_FLICK_INPUT_MODE_KEY, "voiced")
                selectVisibleChoice(scenario, AppPreference.SUMIRE_FLICK_INPUT_MODE_KEY, "all")
                assertEquals("clockwise", preferences.getString(AppPreference.SUMIRE_FLICK_ROTATION_KEY, null))
            }
        } finally {
            AppPreference.importAllFromJson(originalBackup)
        }
    }

    private fun selectVisibleChoice(
        scenario: ActivityScenario<MainActivity>,
        key: String,
        value: String
    ) {
        var labels = emptyList<String>()
        var selectedIndex = -1
        scenario.onActivity {
            val preference = requireNotNull(sumire(it)?.findPreference<ListPreference>(key))
            assertTrue("$key must be available", preference.isVisible && preference.isEnabled)
            labels = preference.entries.map(CharSequence::toString)
            assertEquals("Both settings must offer three choices", 3, labels.size)
            selectedIndex = preference.findIndexOfValue(value)
            assertTrue("Requested value must exist", selectedIndex >= 0)
            preference.performClick()
        }
        labels.forEach { label ->
            onView(withText(label)).inRoot(isDialog()).check(matches(isDisplayed()))
        }
        onView(withText(labels[selectedIndex])).inRoot(isDialog()).perform(click())
        await("Selecting $key=$value did not persist") { preferences.getString(key, null) == value }
        scenario.onActivity {
            val summary = requireNotNull(sumire(it)?.findPreference<ListPreference>(key))
                .summary.toString()
            assertTrue("Summary must follow the saved selection", summary.startsWith(labels[selectedIndex] + "\n"))
            assertFalse("Summary must not expose format placeholders", summary.contains("%"))
        }
    }

    private fun host(activity: MainActivity): NavHostFragment =
        activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment

    private fun sumire(activity: MainActivity): SumirePreferenceFragment? =
        host(activity).childFragmentManager.primaryNavigationFragment as? SumirePreferenceFragment

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        error(message)
    }
}
