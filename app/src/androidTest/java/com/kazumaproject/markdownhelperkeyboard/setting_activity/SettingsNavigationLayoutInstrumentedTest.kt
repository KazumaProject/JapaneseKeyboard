package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme.KeyboardThemeFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.P)
    fun settingsContentInitializesWhileActivityIsStartedWithoutResuming() {
        val gate = InitializationGate(expectedEntries = 1)
        gate.install()
        try {
            withHomeMode(useNewHome = true, awaitContentReady = false) { scenario ->
                gate.awaitEntries()
                scenario.moveToState(Lifecycle.State.STARTED)
                assertEquals(Lifecycle.State.STARTED, scenario.state)
                scenario.onActivity { activity ->
                    assertFalse(activity.isSettingsContentReady)
                    assertNull(activity.findViewById<View>(R.id.nav_host_fragment_activity_main))
                }

                gate.release()
                scenario.awaitSettingsContentReady()
                assertEquals(Lifecycle.State.STARTED, scenario.state)
                scenario.onActivity { activity ->
                    assertTrue(activity.isSettingsContentReady)
                    val navHostView = activity.findViewById<View>(
                        R.id.nav_host_fragment_activity_main,
                    )
                    assertNotNull("Settings NavHost should be inflated", navHostView)
                    assertTrue(
                        "Settings NavHost should be attached",
                        navHostView.isAttachedToWindow,
                    )
                    assertEquals(View.VISIBLE, navHostView.visibility)
                    assertNotNull(
                        "Settings navigation graph should be installed",
                        navController(activity).currentDestination,
                    )
                }
            }
        } finally {
            gate.release()
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
                scenario.awaitSettingsContentReady()
                scenario.onActivity { activity ->
                    assertEquals(R.id.navigation_learn_dictionary, navController(activity).currentDestination?.id)
                }
            }
        }
    }

    @Test
    fun restoredDetailAndBackStackSurviveRepeatedRecreationWhileInitializationWaits() {
        for (useNewHome in listOf(false, true)) {
            withHomeMode(useNewHome) { scenario ->
                var previousDestination = 0
                scenario.onActivity { activity ->
                    val nav = navController(activity)
                    previousDestination = checkNotNull(nav.currentDestination).id
                    nav.navigate(R.id.keyboardThemeFragment)
                }
                instrumentation.waitForIdleSync()

                val gate = InitializationGate(expectedEntries = 2)
                gate.install()
                try {
                    scenario.recreate()
                    gate.awaitEntries(1)
                    scenario.recreate()
                    gate.awaitEntries(2)
                    scenario.onActivity { activity ->
                        val host = activity.supportFragmentManager
                            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                        assertEquals(Lifecycle.State.INITIALIZED, host.lifecycle.currentState)
                    }

                    gate.release()
                    scenario.awaitSettingsContentReady()
                    scenario.onActivity { activity ->
                        val nav = navController(activity)
                        assertEquals(R.id.keyboardThemeFragment, nav.currentDestination?.id)
                        val returnedToPrevious = CountDownLatch(1)
                        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
                            if (destination.id == previousDestination) returnedToPrevious.countDown()
                        }
                        nav.addOnDestinationChangedListener(listener)
                        try {
                            assertTrue("Restored detail should retain its previous entry", nav.popBackStack())
                            assertTrue(
                                "Back stack did not return to $previousDestination",
                                returnedToPrevious.await(2, TimeUnit.SECONDS),
                            )
                        } finally {
                            nav.removeOnDestinationChangedListener(listener)
                        }
                    }
                } finally {
                    gate.release()
                }
            }
        }
    }

    @Test
    fun coldDictionaryIntentSurvivesRecreationDuringInitializationAndRunsOnlyOnce() {
        for (useNewHome in listOf(false, true)) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val gate = InitializationGate(expectedEntries = 2)
            gate.install()
            try {
                withHomeMode(
                    useNewHome = useNewHome,
                    launchIntent = Intent(context, MainActivity::class.java).putExtra(
                        OPEN_SETTING_ACTIVITY_EXTRA,
                        DICTIONARY_FRAGMENT_REQUEST,
                    ),
                    awaitContentReady = false,
                ) { scenario ->
                    gate.awaitEntries(1)
                    scenario.recreate()
                    gate.awaitEntries(2)
                    gate.release()
                    assertDictionaryRequestWasHandledOnce(scenario, useNewHome)
                }
            } finally {
                gate.release()
            }
        }
    }

    @Test
    fun newIntentReceivedDuringInitializationSurvivesRecreationAndRunsOnlyOnce() {
        for (useNewHome in listOf(false, true)) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val gate = InitializationGate(expectedEntries = 2)
            gate.install()
            try {
                withHomeMode(useNewHome, awaitContentReady = false) { scenario ->
                    gate.awaitEntries(1)
                    instrumentation.runOnMainSync {
                        context.startActivity(Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            putExtra(OPEN_SETTING_ACTIVITY_EXTRA, DICTIONARY_FRAGMENT_REQUEST)
                        })
                    }
                    awaitIntentRequest(scenario, DICTIONARY_FRAGMENT_REQUEST)
                    scenario.recreate()
                    gate.awaitEntries(2)
                    gate.release()
                    assertDictionaryRequestWasHandledOnce(scenario, useNewHome)
                }
            } finally {
                gate.release()
            }
        }
    }

    @Test
    fun recreationRestoresThemePreferencesInBothHomeModes() {
        for (useNewHome in listOf(false, true)) {
            withHomeMode(useNewHome) { scenario ->
                scenario.onActivity { activity ->
                    navController(activity).navigate(R.id.keyboardThemeFragment)
                }
                instrumentation.waitForIdleSync()
                scenario.recreate()
                scenario.awaitSettingsContentReady()
                scenario.onActivity { activity ->
                    assertEquals(R.id.keyboardThemeFragment, navController(activity).currentDestination?.id)
                    val host = activity.supportFragmentManager
                        .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                    val theme = host.childFragmentManager.primaryNavigationFragment as PreferenceFragmentCompat
                    assertTrue(theme.findPreference<Preference>("theme_default") != null)
                }
            }
        }
    }

    @Test
    fun restoredThemeIsCreatedAfterActivityOnCreate() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val application = ApplicationProvider.getApplicationContext<Application>()
        val events = CopyOnWriteArrayList<String>()
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity !is MainActivity || savedInstanceState == null) return
                activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
                    object : FragmentManager.FragmentLifecycleCallbacks() {
                        override fun onFragmentPreCreated(
                            fm: FragmentManager,
                            fragment: Fragment,
                            savedInstanceState: Bundle?,
                        ) {
                            if (fragment is KeyboardThemeFragment) {
                                events += "theme"
                            }
                        }
                    },
                    true,
                )
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is MainActivity && savedInstanceState != null) events += "activity"
            }
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        application.registerActivityLifecycleCallbacks(callbacks)
        try {
            withHomeMode(useNewHome = true) { scenario ->
                scenario.onActivity { navController(it).navigate(R.id.keyboardThemeFragment) }
                instrumentation.waitForIdleSync()
                scenario.recreate()
                scenario.awaitSettingsContentReady()
                assertEquals(listOf("activity", "theme"), events)
            }
        } finally {
            application.unregisterActivityLifecycleCallbacks(callbacks)
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
                val destinations = CopyOnWriteArrayList<Int>()
                val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
                    destinations += destination.id
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
                        val currentDestination = nav.currentDestination?.id
                        assertTrue(
                            "Expected home or first-run keyboard setup, got $currentDestination; history was $destinations",
                            currentDestination == expectedHome ||
                                currentDestination == R.id.enableKeyboardFragment,
                        )
                        assertTrue("Home request did not visit the selected home", destinations.contains(expectedHome))
                        assertFalse(
                            "Dictionary history remained after returning home: $destinations",
                            nav.popBackStack(R.id.navigation_learn_dictionary, true),
                        )
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
                    scenario.awaitSettingsContentReady()
                    assertLayout(scenario, View.GONE, if (useNewHome) 0 else 1)
                    scenario.onActivity { assertTrue(navController(it).popBackStack()) }
                    instrumentation.waitForIdleSync()
                    assertLayout(scenario, if (useNewHome) View.GONE else View.VISIBLE, if (useNewHome) 0 else 1)
                }
            }
        }
    }

    @Test
    fun splitQwertyScreensOpenFromBothHomeModes() {
        val destinations = mapOf(
            R.id.qwertyPreferenceFragment to "qwerty_button_size_preference",
            R.id.qwertyEnglishPreferenceFragment to "qwerty_english_space_flick_preference",
            R.id.qwertyRomajiPreferenceFragment to "qwerty_romaji_space_flick_preference",
            R.id.kanaPreferenceFragment to "tenkey_space_flick_preference",
        )
        for (useNewHome in listOf(false, true)) {
            withHomeMode(useNewHome) { scenario ->
                for ((destination, preferenceKey) in destinations) {
                    scenario.onActivity { navController(it).navigate(destination) }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        val host = activity.supportFragmentManager
                            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                        val screen = host.childFragmentManager.primaryNavigationFragment as PreferenceFragmentCompat
                        assertTrue(screen.findPreference<Preference>(preferenceKey) != null)
                        if (destination == R.id.qwertyEnglishPreferenceFragment) {
                            assertTrue(screen.findPreference<Preference>("qwerty_romaji_space_flick_preference") == null)
                        }
                        assertTrue(navController(activity).popBackStack())
                    }
                    instrumentation.waitForIdleSync()
                }
            }
        }
    }

    private fun navController(activity: MainActivity) =
        (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment).navController

    private fun assertDictionaryRequestWasHandledOnce(
        scenario: ActivityScenario<MainActivity>,
        useNewHome: Boolean,
    ) {
        scenario.awaitSettingsContentReady()
        scenario.onActivity { activity ->
            val nav = navController(activity)
            assertEquals(R.id.navigation_learn_dictionary, nav.currentDestination?.id)
        }

        scenario.recreate()
        scenario.awaitSettingsContentReady()
        scenario.onActivity { activity ->
            val nav = navController(activity)
            assertEquals(R.id.navigation_learn_dictionary, nav.currentDestination?.id)
            assertTrue("Dictionary should have one home entry", nav.popBackStack())
            assertEquals(
                if (useNewHome) R.id.navigation_setting else R.id.settingMainFragment,
                nav.currentDestination?.id,
            )
            assertFalse("Dictionary request should not be replayed", nav.popBackStack())
        }
    }

    private fun awaitIntentRequest(
        scenario: ActivityScenario<MainActivity>,
        request: String,
    ) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        var received = false
        while (!received && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity ->
                received = activity.intent?.getStringExtra(OPEN_SETTING_ACTIVITY_EXTRA) == request
            }
            if (!received) SystemClock.sleep(20)
        }
        assertTrue("Expected onNewIntent request was not delivered", received)
    }

    private fun withHomeMode(
        useNewHome: Boolean,
        launchIntent: Intent? = null,
        awaitContentReady: Boolean = true,
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
                launchIntent ?: Intent(context, MainActivity::class.java),
            )
            scenario = launchedScenario
            if (awaitContentReady) launchedScenario.awaitSettingsContentReady()
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
        relaunchedScenario.awaitSettingsContentReady()
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
        const val OPEN_SETTING_ACTIVITY_EXTRA = "openSettingActivity"
        const val DICTIONARY_FRAGMENT_REQUEST = "dictionary_fragment_request"
    }

    private class InitializationGate(private val expectedEntries: Int) {
        private val entered = AtomicInteger()
        private val release = CountDownLatch(1)
        private var installed = false

        fun install() {
            check(MainActivity.initializationGateForTest == null)
            MainActivity.initializationGateForTest = {
                entered.incrementAndGet()
                check(release.await(20, TimeUnit.SECONDS)) {
                    "Timed out waiting for the test to release settings initialization"
                }
            }
            installed = true
        }

        fun awaitEntries(expected: Int = expectedEntries) {
            val deadline = SystemClock.uptimeMillis() + 10_000
            while (entered.get() < expected && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(20)
            }
            assertTrue(
                "Settings initialization did not reach the test gate",
                entered.get() >= expected,
            )
        }

        fun release() {
            release.countDown()
            if (installed) {
                MainActivity.initializationGateForTest = null
                installed = false
            }
        }
    }
}
