package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsNavigationLayoutInstrumentedTest {

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun newHomeStartsWithoutLegacyNavigationAndKeepsTheStaticNavHostAnchor() {
        withHomeMode(useNewHome = true) { scenario ->
            assertLayout(
                scenario = scenario,
                expectedContainerVisibility = View.GONE,
                expectedChildCount = 0,
            )
        }
    }

    @Test
    fun switchingHomeModesDoesNotDuplicateLegacyNavigation() {
        withHomeMode(useNewHome = true) { scenario ->
            assertLayout(
                scenario = scenario,
                expectedContainerVisibility = View.GONE,
                expectedChildCount = 0,
            )

            setHomeMode(scenario, useNewHome = false)
            assertLayout(
                scenario = scenario,
                expectedContainerVisibility = View.VISIBLE,
                expectedChildCount = 1,
            )

            setHomeMode(scenario, useNewHome = true)
            assertLayout(
                scenario = scenario,
                expectedContainerVisibility = View.GONE,
                expectedChildCount = 1,
            )

            setHomeMode(scenario, useNewHome = false)
            assertLayout(
                scenario = scenario,
                expectedContainerVisibility = View.VISIBLE,
                expectedChildCount = 1,
            )
        }
    }

    @Test
    fun freshActivityCreationKeepsTheSelectedHomeAndInflatesOnlyItsRequiredNavigation() {
        withHomeMode(useNewHome = true) { scenario ->
            val relaunchedScenario = relaunchAfterClosing(scenario)
            try {
                assertLayout(
                    scenario = relaunchedScenario,
                    expectedContainerVisibility = View.GONE,
                    expectedChildCount = 0,
                )
            } finally {
                relaunchedScenario.close()
            }
        }

        withHomeMode(useNewHome = false) { scenario ->
            val relaunchedScenario = relaunchAfterClosing(scenario)
            try {
                assertLayout(
                    scenario = relaunchedScenario,
                    expectedContainerVisibility = View.VISIBLE,
                    expectedChildCount = 1,
                )
            } finally {
                relaunchedScenario.close()
            }
        }
    }

    private fun withHomeMode(
        useNewHome: Boolean,
        block: (ActivityScenario<MainActivity>) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val hadValue = preferences.contains(SETTING_USE_NEW_HOME_SCREEN)
        val originalValue = preferences.getBoolean(SETTING_USE_NEW_HOME_SCREEN, true)
        var scenario: ActivityScenario<MainActivity>? = null

        try {
            preferences.edit()
                .putBoolean(SETTING_USE_NEW_HOME_SCREEN, useNewHome)
                .commit()
            AppPreference.init(context)

            val launchedScenario = ActivityScenario.launch<MainActivity>(
                Intent(context, MainActivity::class.java),
            )
            scenario = launchedScenario
            instrumentation.waitForIdleSync()
            block(launchedScenario)
        } finally {
            scenario?.close()
            val editor = preferences.edit()
            if (hadValue) {
                editor.putBoolean(SETTING_USE_NEW_HOME_SCREEN, originalValue)
            } else {
                editor.remove(SETTING_USE_NEW_HOME_SCREEN)
            }
            editor.commit()
            AppPreference.init(context)
        }
    }

    private fun relaunchAfterClosing(
        scenario: ActivityScenario<MainActivity>,
    ): ActivityScenario<MainActivity> {
        scenario.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val relaunchedScenario = ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java),
        )
        instrumentation.waitForIdleSync()
        return relaunchedScenario
    }

    private fun setHomeMode(
        scenario: ActivityScenario<MainActivity>,
        useNewHome: Boolean,
    ) {
        scenario.onActivity { activity ->
            PreferenceManager.getDefaultSharedPreferences(activity)
                .edit()
                .putBoolean(SETTING_USE_NEW_HOME_SCREEN, useNewHome)
                .commit()
            activity.applySettingHomeModeFromPreference()
        }
        instrumentation.waitForIdleSync()
    }

    private fun assertLayout(
        scenario: ActivityScenario<MainActivity>,
        expectedContainerVisibility: Int,
        expectedChildCount: Int,
    ) {
        scenario.onActivity { activity ->
            val navigationContainer =
                activity.findViewById<ViewGroup>(R.id.nav_view_container)
            val navHost = activity.findViewById<View>(R.id.nav_host_fragment_activity_main)
            val layoutParams = navHost.layoutParams as ConstraintLayout.LayoutParams

            assertEquals(expectedContainerVisibility, navigationContainer.visibility)
            assertEquals(expectedChildCount, navigationContainer.childCount)
            if (expectedChildCount == 1) {
                assertEquals(R.id.nav_view, navigationContainer.getChildAt(0).id)
            }
            assertEquals(R.id.nav_view_container, layoutParams.bottomToTop)
            assertEquals(ConstraintLayout.LayoutParams.UNSET, layoutParams.bottomToBottom)
        }
    }

    private companion object {
        const val SETTING_USE_NEW_HOME_SCREEN = "setting_use_new_home_screen_preference"
    }
}
