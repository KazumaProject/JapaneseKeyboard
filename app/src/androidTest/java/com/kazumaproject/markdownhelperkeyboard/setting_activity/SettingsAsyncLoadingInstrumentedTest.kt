package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.Preference
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.*
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class SettingsAsyncLoadingInstrumentedTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Before fun setUp() {
        assumeTrue("Use an isolated probe APK", context.packageName.endsWith(".freezeprobe"))
        AppPreference.awaitInitialization()
        AppPreference.setting_use_new_home_screen_preference = false
    }

    @After fun tearDown() { SettingsLoadDiagnostics.beforeLoad = null }

    @Test fun startupDisplaysProgressAndAcceptsBackWhilePreferencesAreDelayed() {
        val gate = Gate(SettingsLoadStage.PREFERENCES)
        gate.install()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            gate.awaitEntry()
            assertProgressAndHeartbeat(scenario)
            scenario.onActivity { assertFalse(it.isSettingsContentReady) }
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            await("Back did not close loading activity") { scenario.state == Lifecycle.State.DESTROYED }
            gate.release()
        }
    }

    @Test fun startupFontLoadingIsOffMainAndKeepsProgressVisible() {
        val gate = Gate(SettingsLoadStage.FONT)
        gate.install()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            gate.awaitEntry()
            assertProgressAndHeartbeat(scenario)
            gate.release()
            scenario.awaitSettingsContentReady()
        }
    }

    @Test fun legacyXmlLoadingAllowsSwitchingToReadyTabAndSurvivesRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.awaitSettingsContentReady()
            awaitPreference(scenario, R.id.kanaPreferenceFragment, "tenkey_space_flick_preference")
            val gate = Gate(SettingsLoadStage.XML)
            gate.install()
            scenario.onActivity { nav(it).navigate(R.id.qwertyEnglishPreferenceFragment) }
            gate.awaitEntry()
            assertProgressAndHeartbeat(scenario)
            scenario.recreate()
            scenario.awaitSettingsContentReady()
            assertProgressAndHeartbeat(scenario)
            gate.release()
            awaitCurrentPreference(scenario, "qwerty_english_space_flick_preference")
            scenario.onActivity { assertEquals(R.id.qwertyEnglishPreferenceFragment, nav(it).currentDestination?.id) }
        }
    }

    @Test fun packageInfoLoadingIsOffMainAndCanBeLeftWithBack() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.awaitSettingsContentReady()
            awaitPreference(scenario, R.id.kanaPreferenceFragment, "tenkey_space_flick_preference")
            val gate = Gate(SettingsLoadStage.PACKAGE_INFO)
            gate.install()
            scenario.onActivity { nav(it).navigate(R.id.commonPreferenceFragment) }
            gate.awaitEntry()
            assertProgressAndHeartbeat(scenario)
            scenario.onActivity { assertTrue(nav(it).popBackStack()) }
            awaitCurrentPreference(scenario, "tenkey_space_flick_preference")
            gate.release()
        }
    }

    @Test fun failedXmlLoadShowsRetryAndSuccessfulRetryBindsPreferences() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.awaitSettingsContentReady()
            awaitPreference(scenario, R.id.kanaPreferenceFragment, "tenkey_space_flick_preference")
            val failOnce = AtomicBoolean(true)
            SettingsLoadDiagnostics.beforeLoad = { stage ->
                if (stage == SettingsLoadStage.XML && failOnce.compareAndSet(true, false)) {
                    throw IllegalStateException("Controlled XML failure")
                }
            }
            scenario.onActivity { nav(it).navigate(R.id.qwertyEnglishPreferenceFragment) }
            await("Retry was not shown") {
                var visible = false
                scenario.onActivity { visible = shownView(it.window.decorView, R.id.settings_loading_retry) != null }
                visible
            }
            scenario.onActivity {
                assertNull(shownView(it.window.decorView, R.id.settings_loading_progress))
                shownView(it.window.decorView, R.id.settings_loading_retry)!!.performClick()
            }
            awaitCurrentPreference(scenario, "qwerty_english_space_flick_preference")
        }
    }

    @Test fun searchIndexLoadingShowsProgressAndAcceptsBack() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.awaitSettingsContentReady()
            awaitPreference(scenario, R.id.kanaPreferenceFragment, "tenkey_space_flick_preference")
            val gate = Gate(SettingsLoadStage.SEARCH)
            gate.install()
            scenario.onActivity { nav(it).navigate(R.id.settingSearchFragment) }
            gate.awaitEntry()
            assertProgressAndHeartbeat(scenario)
            scenario.onActivity { assertTrue(nav(it).popBackStack()) }
            gate.release()
            awaitCurrentPreference(scenario, "tenkey_space_flick_preference")
        }
    }

    @Test fun textMacroInitialReadShowsProgressAndAcceptsBack() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.awaitSettingsContentReady()
            awaitPreference(scenario, R.id.kanaPreferenceFragment, "tenkey_space_flick_preference")
            val gate = Gate(SettingsLoadStage.DATABASE)
            gate.install()
            scenario.onActivity { nav(it).navigate(R.id.textMacroFragment) }
            gate.awaitEntry()
            assertProgressAndHeartbeat(scenario)
            scenario.onActivity { assertTrue(nav(it).popBackStack()) }
            gate.release()
            awaitCurrentPreference(scenario, "tenkey_space_flick_preference")
        }
    }

    @Test fun databaseInitializationNeverBlocksMainInEitherHome() {
        for (newHome in listOf(false, true)) {
            AppPreference.setting_use_new_home_screen_preference = newHome
            val gate = Gate(SettingsLoadStage.DATABASE)
            gate.install()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.awaitSettingsContentReady()
                gate.awaitEntry()
                assertProgressAndHeartbeat(scenario)
                gate.release()
            }
        }
    }

    @Test fun gemmaModelDiscoveryNeverBlocksMain() {
        assumeTrue(AppVariantConfig.hasGemma)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.awaitSettingsContentReady()
            awaitPreference(scenario, R.id.kanaPreferenceFragment, "tenkey_space_flick_preference")
            val gate = Gate(SettingsLoadStage.MODELS)
            gate.install()
            scenario.onActivity { nav(it).navigate(R.id.gemmaPreferenceFragment) }
            gate.awaitEntry()
            assertProgressAndHeartbeat(scenario)
            gate.release()
            awaitCurrentPreference(scenario, "gemma_model_selection_preference")
        }
    }

    @Test fun everyLegacyPreferenceTabBindsItsItemsWithoutLoadingErrors() {
        for (newHome in listOf(false, true)) {
            AppPreference.setting_use_new_home_screen_preference = newHome
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.awaitSettingsContentReady()
                val destinations = SettingTabRegistry.createTabs()
                    .filter { it.xmlRes != null || it.key == SettingTabRegistry.TAB_THEME }
                    .map { it.key to it.destinationId } + listOf(
                        "external_dictionary" to R.id.externalDictionarySettingsFragment,
                        "utility_candidates" to R.id.utilityCandidatePreferenceFragment,
                    )
                for ((name, destination) in destinations) {
                    scenario.onActivity { nav(it).navigate(destination) }
                    await("Tab $name failed to load") {
                        var ready = false
                        scenario.onActivity {
                            val host = it.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                            val screen = host.childFragmentManager.primaryNavigationFragment as? androidx.preference.PreferenceFragmentCompat
                            ready = screen?.listView?.adapter?.itemCount?.let { count -> count > 0 } == true &&
                                shownView(screen.requireView(), R.id.settings_loading_overlay) == null
                        }
                        ready
                    }
                    scenario.onActivity { assertTrue(nav(it).popBackStack()) }
                }
            }
        }
    }

    private fun awaitPreference(scenario: ActivityScenario<MainActivity>, destination: Int, key: String) {
        scenario.onActivity { nav(it).navigate(destination) }
        awaitCurrentPreference(scenario, key)
    }

    private fun awaitCurrentPreference(scenario: ActivityScenario<MainActivity>, key: String) {
        await("Preference $key was not bound") {
            var ready = false
            scenario.onActivity {
                val host = it.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                val fragment = host.childFragmentManager.primaryNavigationFragment as? androidx.preference.PreferenceFragmentCompat
                ready = fragment?.findPreference<Preference>(key) != null && fragment.listView.adapter != null
            }
            ready
        }
    }

    private fun assertProgressAndHeartbeat(scenario: ActivityScenario<MainActivity>) {
        repeat(3) {
            val heartbeat = CountDownLatch(1)
            scenario.onActivity { activity ->
                assertTrue("Progress must be drawn while waiting", shownView(activity.window.decorView, R.id.settings_loading_progress) != null)
                activity.window.decorView.post { heartbeat.countDown() }
            }
            assertTrue("Main looper stalled", heartbeat.await(1, TimeUnit.SECONDS))
            SystemClock.sleep(50)
        }
    }

    private fun shownView(root: View, id: Int): View? {
        if (root.id == id && root.isShown) return root
        if (root is ViewGroup) for (index in 0 until root.childCount) {
            shownView(root.getChildAt(index), id)?.let { return it }
        }
        return null
    }

    private fun nav(activity: MainActivity) =
        (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment).navController

    private fun await(message: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 10_000
        while (!condition() && SystemClock.uptimeMillis() < end) SystemClock.sleep(20)
        assertTrue(message, condition())
    }

    private class Gate(private val stage: SettingsLoadStage) {
        private val entered = CountDownLatch(1)
        private val released = CompletableDeferred<Unit>()
        fun install() {
            SettingsLoadDiagnostics.beforeLoad = { current ->
                if (current == stage) {
                    assertNotEquals("Load must run off main", Looper.getMainLooper().thread, Thread.currentThread())
                    entered.countDown()
                    withTimeout(15_000) { released.await() }
                }
            }
        }
        fun awaitEntry() { assertTrue("Load did not enter $stage", entered.await(10, TimeUnit.SECONDS)) }
        fun release() { released.complete(Unit) }
    }
}
