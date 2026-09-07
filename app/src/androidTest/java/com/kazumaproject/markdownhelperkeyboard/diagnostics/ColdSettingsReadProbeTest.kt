package com.kazumaproject.markdownhelperkeyboard.diagnostics

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Must run alone in a fresh process. Gate the real framework loader before Hilt initializes prefs. */
@RunWith(AndroidJUnit4::class)
class ColdSettingsReadProbeTest {
    @Test fun coldLaunchWithControlledPreferenceLoader() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        org.junit.Assume.assumeTrue("Run with the isolated probe runner",
            context.packageName.endsWith(".freezeprobe") &&
                InstrumentationRegistry.getArguments().getString("contentionProbe") == "true")
        val newHome = InstrumentationRegistry.getArguments().getString("homeMode") == "new"
        val held = InstrumentationRegistry.getArguments().getString("coldMode") != "drained"
        val output = File(context.filesDir, "contention-probe").apply { mkdirs() }
        // Only the isolated probe's synthetic settings. Do not call getSharedPreferences before gate.
        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs").apply { mkdirs() }
        File(prefsDir, "${context.packageName}_preferences.xml.bak").delete()
        File(prefsDir, "${context.packageName}_preferences.xml").writeText(
            "<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map>" +
                "<boolean name='setting_use_new_home_screen_preference' value='$newHome' />" +
                "<int name='romaji_map_data_version' value='1' /></map>"
        )
        val framework = Class.forName("android.app.SharedPreferencesImpl")
        val loaderField = framework.getDeclaredField("sLoadExecutor").apply { isAccessible = true }
        val loader = loaderField.get(null) as java.util.concurrent.Executor
        val ready = CountDownLatch(1)
        val release = CountDownLatch(1)
        loader.execute { ready.countDown(); release.await(15, TimeUnit.SECONDS) }
        val executor = Executors.newSingleThreadExecutor()
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            assertTrue(ready.await(3, TimeUnit.SECONDS))
            if (!held) release.countDown()
            val launched = executor.submit<ActivityScenario<MainActivity>> {
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
            }
            if (held) {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                var frames = ""
                while (System.nanoTime() < deadline) {
                    frames = Looper.getMainLooper().thread.stackTrace.joinToString("\n")
                    if (frames.contains("awaitLoadedLocked")) break
                    Thread.sleep(20)
                }
                assertTrue("Expected cold preference load wait: $frames", frames.contains("awaitLoadedLocked"))
                repeat(3) { sample ->
                    val stacks = Thread.getAllStackTraces()
                    assertTrue(stacks[Looper.getMainLooper().thread].orEmpty().any { it.methodName == "awaitLoadedLocked" })
                    File(output, "cold-$sample.txt").writeText(buildString {
                        appendLine("newHome=$newHome loaderHeld=true uptime_ms=${android.os.SystemClock.uptimeMillis()}")
                        stacks.entries.sortedBy { it.key.id }.forEach { (thread, stack) ->
                            appendLine("id=${thread.id} main=${thread === Looper.getMainLooper().thread} state=${thread.state}")
                            stack.forEach { appendLine("  at $it") }
                        }
                    })
                    Thread.sleep(200)
                }
                val ping = CountDownLatch(1)
                Handler(Looper.getMainLooper()).post { ping.countDown() }
                assertFalse(ping.await(300, TimeUnit.MILLISECONDS))
                release.countDown()
                assertTrue("Cold launch must recover when loader progresses", ping.await(5, TimeUnit.SECONDS))
            }
            scenario = launched.get(10, TimeUnit.SECONDS)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                val host = activity.supportFragmentManager.findFragmentById(
                    com.kazumaproject.markdownhelperkeyboard.R.id.nav_host_fragment_activity_main
                ) as androidx.navigation.fragment.NavHostFragment
                val expected = if (newHome) com.kazumaproject.markdownhelperkeyboard.R.id.navigation_setting
                    else com.kazumaproject.markdownhelperkeyboard.R.id.settingMainFragment
                assertEquals(expected, host.navController.currentDestination?.id)
            }
            File(output, "cold.result").writeText("PASS newHome=$newHome loaderHeld=$held launched_after_release=true\n")
        } finally {
            release.countDown()
            executor.shutdownNow()
            scenario?.close()
        }
    }
}
