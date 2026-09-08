package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import androidx.navigation.NavController
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
                closeScenario(relaunchedScenario)
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
                closeScenario(relaunchedScenario)
            }
        }
    }

    @Test
    fun recreationPreservesADetailDestinationInBothHomeModes() {
        for (useNewHome in listOf(false, true)) {
            withHomeMode(useNewHome) { scenario ->
                scenario.onActivity { activity ->
                    navController(activity).navigate(R.id.navigation_learn_dictionary)
                }
                instrumentation.waitForIdleSync()
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertEquals(R.id.navigation_learn_dictionary, navController(activity).currentDestination?.id)
                }
            }
        }
    }

    @Test
    fun returningToEitherHomeClearsDictionaryHistory() {
        for (useNewHome in listOf(false, true)) {
            withHomeMode(useNewHome) { scenario ->
                scenario.onActivity { activity ->
                    navController(activity).navigate(R.id.navigation_learn_dictionary)
                }
                instrumentation.waitForIdleSync()
                val context = ApplicationProvider.getApplicationContext<Context>()
                val expectedHome = if (useNewHome) R.id.navigation_setting else R.id.settingMainFragment
                val returnedHome = CountDownLatch(1)
                val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
                    if (destination.id == expectedHome) returnedHome.countDown()
                }
                scenario.onActivity { navController(it).addOnDestinationChangedListener(listener) }
                try {
                    instrumentation.runOnMainSync {
                        context.startActivity(Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            putExtra("openSettingActivity", "setting_fragment_request")
                        })
                    }
                    // Idle on this process does not imply delivery of onNewIntent by system_server.
                    assertTrue("Home intent was not delivered", returnedHome.await(5, TimeUnit.SECONDS))
                    scenario.onActivity { activity ->
                        val nav = navController(activity)
                        assertEquals(expectedHome, nav.currentDestination?.id)
                        assertFalse(nav.popBackStack())
                    }
                } finally {
                    scenario.onActivity { navController(it).removeOnDestinationChangedListener(listener) }
                }
            }
        }
    }

    @Test
    fun candidatePreviewsKeepNavigationHiddenAcrossRecreationAndRestoreItOnReturn() {
        for (useNewHome in listOf(false, true)) {
            withHomeMode(useNewHome) { scenario ->
                for (destination in listOf(R.id.candidateViewHeightSettingFragment, R.id.candidateHeightLandscapeSettingFragment)) {
                    scenario.onActivity { navController(it).navigate(destination) }
                    instrumentation.waitForIdleSync()
                    scenario.recreate()
                    assertLayout(scenario, View.GONE, if (useNewHome) 0 else 1)
                    scenario.onActivity { assertTrue(navController(it).popBackStack()) }
                    instrumentation.waitForIdleSync()
                    assertLayout(scenario, if (useNewHome) View.GONE else View.VISIBLE, if (useNewHome) 0 else 1)
                }
            }
        }
    }

    private fun navController(activity: MainActivity) =
        (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment).navController

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
            scenario?.let(::closeScenario)
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

    private fun closeScenario(scenario: ActivityScenario<MainActivity>) {
        if (scenario.state != Lifecycle.State.DESTROYED) {
            scenario.onActivity { it.finish() }
            instrumentation.waitForIdleSync()
        }
        scenario.close()
    }

    private fun relaunchAfterClosing(
        scenario: ActivityScenario<MainActivity>,
    ): ActivityScenario<MainActivity> {
        closeScenario(scenario)
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
