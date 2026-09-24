package com.kazumaproject.markdownhelperkeyboard.diagnostics

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.markdownhelperkeyboard.setting_activity.awaitSettingsContentReady
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Runs only in the isolated probe app. Checks that starting preferences never waits for migration. */
@RunWith(AndroidJUnit4::class)
class ColdSettingsReadProbeTest {
    @Test fun startingPreferencesDoesNotBlockMainWhileMigrationLockIsHeld() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(
            "Run with the isolated probe runner",
            context.packageName.endsWith(".freezeprobe") &&
                InstrumentationRegistry.getArguments().getString("contentionProbe") == "true",
        )
        val arguments = InstrumentationRegistry.getArguments()
        val newHome = arguments.getString("homeMode") == "new"
        val held = arguments.getString("coldMode") != "drained"
        val output = File(context.filesDir, "contention-probe").apply { mkdirs() }
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val homeKey = "setting_use_new_home_screen_preference"
        val hadHome = preferences.contains(homeKey)
        val originalHome = preferences.getBoolean(homeKey, true)

        // The application's startup thread must be finished before this test takes its lock.
        AppPreference.awaitInitialization()
        val lock = AppPreference::class.java.getDeclaredField("initializationLock")
            .apply { isAccessible = true }.get(AppPreference)
        val lockHeld = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holder = Thread({
            synchronized(lock) {
                lockHeld.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
        }, "PreferenceMigrationGate")
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            preferences.edit().putBoolean(homeKey, newHome).commit()
            if (held) {
                holder.start()
                assertTrue("Migration gate was not acquired", lockHeld.await(3, TimeUnit.SECONDS))
            }

            val mainReturned = CountDownLatch(1)
            Handler(Looper.getMainLooper()).post {
                AppPreference.startInitialization(context)
                mainReturned.countDown()
            }
            assertTrue(
                "Starting preferences blocked the main thread on the migration lock",
                mainReturned.await(2, TimeUnit.SECONDS),
            )
            File(output, "cold-0.txt").writeText(
                "newHome=$newHome migrationLockHeld=$held mainResponsive=true\n",
            )
            release.countDown()
            val launched = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
            scenario = launched
            launched.awaitSettingsContentReady()
            launched.onActivity { activity ->
                val host = activity.supportFragmentManager.findFragmentById(
                    R.id.nav_host_fragment_activity_main,
                ) as androidx.navigation.fragment.NavHostFragment
                val expected = if (newHome) R.id.navigation_setting else R.id.settingMainFragment
                assertEquals(expected, host.navController.currentDestination?.id)
            }
            File(output, "cold.result").writeText(
                "PASS newHome=$newHome migrationLockHeld=$held mainResponsive=true\n",
            )
        } finally {
            release.countDown()
            if (held) holder.join(3_000)
            scenario?.close()
            val editor = preferences.edit()
            if (hadHome) editor.putBoolean(homeKey, originalHome) else editor.remove(homeKey)
            editor.commit()
        }
    }
}
