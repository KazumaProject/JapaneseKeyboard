package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazumaproject.markdownhelperkeyboard.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingSearchEntryInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun host(activity: MainActivity) = activity.supportFragmentManager
        .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment

    private fun hasLoadingOverlay(view: View): Boolean =
        (view.id == R.id.settings_loading_overlay && view.isShown) ||
            (view is ViewGroup && (0 until view.childCount).any { hasLoadingOverlay(view.getChildAt(it)) })

    private fun await(scenario: ActivityScenario<MainActivity>, message: String, ready: (MainActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (SystemClock.uptimeMillis() < deadline) {
            var result = false
            scenario.onActivity { result = ready(it) }
            if (result) return
            SystemClock.sleep(40)
        }
        fail(message)
    }

    @Test fun conventionalSearchTapFocusesInputAndShowsKeyboard() = checkSearchEntry(false)
    @Test fun newSettingsSearchTapFocusesInputAndShowsKeyboard() = checkSearchEntry(true)

    private fun checkSearchEntry(newHome: Boolean) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val key = "setting_use_new_home_screen_preference"
        val hadMode = preferences.contains(key)
        val original = preferences.getBoolean(key, true)
        try {
            preferences.edit().putBoolean(key, newHome).commit()
            AppPreference.init(context)
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.awaitSettingsContentReady()
                if (newHome) {
                    await(scenario, "New settings search button did not load") {
                        it.findViewById<View>(R.id.setting_home_search_button)?.isShown == true && !hasLoadingOverlay(it.window.decorView)
                    }
                    onView(withId(R.id.setting_home_search_button)).perform(click())
                } else {
                    scenario.onActivity { host(it).navController.navigate(R.id.settingMainFragment) }
                    await(scenario, "Conventional search entry did not load") {
                        it.findViewById<View>(R.id.action_legacy_setting_search)?.isShown == true &&
                            !hasLoadingOverlay(it.window.decorView)
                    }
                    onView(withId(R.id.action_legacy_setting_search)).perform(click())
                }
                await(scenario, "Search screen did not open") { host(it).navController.currentDestination?.id == R.id.settingSearchFragment }
                fun awaitFocusedKeyboard() = await(scenario, "Search did not focus its input and show the keyboard") { activity ->
                    activity.findViewById<EditText>(R.id.setting_search_input)?.let { input ->
                        input.hasFocus() && ViewCompat.getRootWindowInsets(input)?.isVisible(WindowInsetsCompat.Type.ime()) == true
                    } == true
                }
                awaitFocusedKeyboard()
                onView(withId(R.id.setting_search_input)).perform(typeText("keyboard"))
                await(scenario, "Search results did not load") {
                    val list = it.findViewById<RecyclerView>(R.id.setting_search_result_recycler_view)
                    (list?.adapter?.itemCount ?: 0) > 0 && list!!.childCount > 0
                }
                scenario.onActivity { activity ->
                    val list = activity.findViewById<RecyclerView>(R.id.setting_search_result_recycler_view)
                    for (index in 0 until list.childCount) {
                        assertFalse(list.getChildAt(index).findViewById<View>(R.id.setting_search_result_arrow).isShown)
                    }
                }
                scenario.recreate()
                scenario.awaitSettingsContentReady()
                awaitFocusedKeyboard()
                scenario.onActivity { assertEquals("keyboard", it.findViewById<EditText>(R.id.setting_search_input).text.toString()) }
            }
        } finally {
            preferences.edit().apply { if (hadMode) putBoolean(key, original) else remove(key) }.commit()
            AppPreference.init(context)
        }
    }
}
