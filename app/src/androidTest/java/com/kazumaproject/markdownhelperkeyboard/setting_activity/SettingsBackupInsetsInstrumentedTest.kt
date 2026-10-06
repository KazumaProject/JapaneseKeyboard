package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.Preference
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.CommonPreferenceFragment
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SettingsBackupInsetsInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private var originalBackup: String? = null

    @Before fun setUp() {
        assumeTrue("Only change isolated probe preferences", context.packageName.endsWith(".freezeprobe"))
        AppPreference.awaitInitialization()
        originalBackup = AppPreference.exportAllToJson()
        AppPreference.setting_use_new_home_screen_preference = false
    }

    @After fun tearDown() {
        MainActivity.initializationGateForTest = null
        originalBackup?.let { AppPreference.importAllFromJson(it) }
    }

    @Test fun delayedStartupKeepsLegacyTabsBelowActionBar() {
        val gate = InitializationGate()
        gate.install()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                gate.awaitLoadingLayout(scenario)
                gate.release()
                scenario.awaitSettingsContentReady()
                assertLegacyTabsBelowActionBar(scenario, "delayed-startup")
            }
        } finally { gate.release() }
    }

    @Test fun exportedBackupImportKeepsLegacyTabsBelowActionBarAfterDelayedRecreation() {
        checkBackupImport(reapplyEdgeToEdge = false)
    }

    @Test fun exportedBackupImportKeepsLegacyTabsBelowActionBarWithStableEdgeToEdgeLayout() {
        checkBackupImport(reapplyEdgeToEdge = true)
    }

    private fun checkBackupImport(reapplyEdgeToEdge: Boolean) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.awaitSettingsContentReady()
            awaitCommonPreference(scenario, "pref_backup_export")
            val mode = if (reapplyEdgeToEdge) "stable" else "restored"
            recordGeometry(scenario, "$mode-before-import")
            val uri = Uri.parse("content://com.kazumaproject.markdownhelperkeyboard.settingsbackuptest/backup.json")
            withDocumentResult(Intent.ACTION_CREATE_DOCUMENT, uri) {
                clickPreference(scenario, "pref_backup_export")
            }
            val exported = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            assertTrue("Export must contain the legacy home setting", exported.contains("setting_use_new_home_screen_preference"))
            // The backup must restore a value that has changed since export.
            AppPreference.setting_use_new_home_screen_preference = true
            val gate = InitializationGate()
            gate.install()
            try {
                withDocumentResult(Intent.ACTION_OPEN_DOCUMENT, uri) {
                    clickPreference(scenario, "pref_backup_import")
                }
                gate.awaitLoadingLayout(scenario, reapplyEdgeToEdge)
                assertFalse("Import must restore legacy home", AppPreference.setting_use_new_home_screen_preference)
                gate.release()
                scenario.awaitSettingsContentReady()
                assertLegacyTabsBelowActionBar(scenario, "$mode-after-import")
                // A second recreation must retain both the layout and navigation state.
                scenario.recreate()
                scenario.awaitSettingsContentReady()
                assertLegacyTabsBelowActionBar(scenario, "$mode-after-second-recreation")
            } finally { gate.release() }
        }
    }

    private fun withDocumentResult(action: String, uri: Uri, work: () -> Unit) {
        val filter = IntentFilter(action).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            addCategory(Intent.CATEGORY_DEFAULT)
            addDataType("*/*")
        }
        val monitor = instrumentation.addMonitor(
            filter, Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)), true,
        )
        try {
            work()
            assertEquals("The real document launcher must run", 1, monitor.hits)
            instrumentation.waitForIdleSync()
        } finally { instrumentation.removeMonitor(monitor) }
    }

    private fun clickPreference(scenario: ActivityScenario<MainActivity>, key: String) {
        scenario.onActivity { activity ->
            val pref = requireNotNull(commonFragment(activity)?.findPreference<Preference>(key))
            assertTrue(requireNotNull(pref.onPreferenceClickListener).onPreferenceClick(pref))
        }
    }

    private fun commonFragment(activity: MainActivity): CommonPreferenceFragment? {
        fun find(fragment: Fragment): CommonPreferenceFragment? =
            if (fragment is CommonPreferenceFragment && fragment.isResumed) fragment
            else fragment.childFragmentManager.fragments.firstNotNullOfOrNull(::find)
        return activity.supportFragmentManager.fragments.firstNotNullOfOrNull(::find)
    }

    private fun awaitCommonPreference(scenario: ActivityScenario<MainActivity>, key: String) {
        await("Common preference $key did not load") {
            var ready = false
            scenario.onActivity { activity ->
                val fragment = commonFragment(activity) ?: return@onActivity
                ready = fragment.findPreference<Preference>(key)?.onPreferenceClickListener != null &&
                    fragment.listView.childCount > 0 &&
                    fragment.requireView().findViewById<View>(R.id.settings_loading_overlay).visibility == View.GONE
            }
            ready
        }
    }

    private data class Geometry(val barBottom: Int, val tabsTop: Int, val rootPadding: Int, val contentPadding: Int)

    private fun recordGeometry(scenario: ActivityScenario<MainActivity>, label: String): Geometry {
        instrumentation.waitForIdleSync()
        var result: Geometry? = null
        scenario.onActivity { activity ->
            val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
            assertEquals(R.id.settingMainFragment, host.navController.currentDestination?.id)
            val bar = activity.findViewById<View>(androidx.appcompat.R.id.action_bar_container)
            val tabs = activity.findViewById<View>(R.id.setting_tab_layout)
            assertEquals(View.VISIBLE, bar.visibility)
            assertTrue(tabs.isLaidOut)
            val barPosition = IntArray(2).also(bar::getLocationOnScreen)
            val tabsPosition = IntArray(2).also(tabs::getLocationOnScreen)
            result = Geometry(
                barPosition[1] + bar.height, tabsPosition[1],
                activity.findViewById<View>(R.id.container).paddingTop,
                activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0).paddingTop,
            )
        }
        val geometry = requireNotNull(result)
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "$label: $geometry\n") })
        val directory = File(context.getExternalFilesDir(null), "backup-insets").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { screenshot ->
            File(directory, "$label.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
        }
        return geometry
    }

    private fun assertLegacyTabsBelowActionBar(scenario: ActivityScenario<MainActivity>, label: String) {
        awaitCommonPreference(scenario, "pref_backup_import")
        val geometry = recordGeometry(scenario, label)
        assertEquals("$label: tabs must start immediately below ActionBar: $geometry", geometry.barBottom, geometry.tabsTop)
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(25)
        }
        fail(message)
    }

    private inner class InitializationGate {
        private val entered = CountDownLatch(1)
        private val released = CountDownLatch(1)

        fun install() {
            check(MainActivity.initializationGateForTest == null)
            MainActivity.initializationGateForTest = {
                entered.countDown()
                check(released.await(20, TimeUnit.SECONDS)) { "Initialization gate timed out" }
            }
        }

        fun awaitLoadingLayout(scenario: ActivityScenario<MainActivity>, reapplyEdgeToEdge: Boolean = false) {
            assertTrue("Activity must reach initialization gate", entered.await(10, TimeUnit.SECONDS))
            if (reapplyEdgeToEdge) {
                // API 35 may restore a fitting decor after onCreate. Stable layout flags
                // exercise AppCompat's inset-based ActionBar placement instead of margins,
                // independently of saved window flags. This is a controlled layout case.
                scenario.onActivity {
                    it.enableEdgeToEdge()
                    @Suppress("DEPRECATION")
                    it.window.decorView.systemUiVisibility = it.window.decorView.systemUiVisibility or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    ViewCompat.requestApplyInsets(it.window.decorView)
                }
            }
            await("Loading content must receive its first insets before release") {
                var laidOut = false
                scenario.onActivity { activity ->
                    val content = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
                    laidOut = content.isLaidOut && ViewCompat.getRootWindowInsets(content) != null &&
                        !activity.isSettingsContentReady
                }
                laidOut
            }
            instrumentation.waitForIdleSync()
        }

        fun release() {
            released.countDown()
            MainActivity.initializationGateForTest = null
        }
    }
}
