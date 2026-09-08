package com.kazumaproject.markdownhelperkeyboard.diagnostics

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Opt-in, isolated emulator experiment. No hooks in production code. */
@RunWith(AndroidJUnit4::class)
class SettingsContentionProbeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val output get() = File(context.filesDir, "contention-probe").apply { mkdirs() }

    @Volatile private var sampling = false
    private var sampler: Thread? = null

    @org.junit.Before fun startSampling() {
        org.junit.Assume.assumeTrue("Run with the isolated probe runner",
            context.packageName.endsWith(".freezeprobe") &&
                InstrumentationRegistry.getArguments().getString("contentionProbe") == "true")
        sampling = true
        sampler = Thread {
            var sample = 0
            while (sampling && sample < 90) {
                snapshot("watch-${sample++ % 8}")
                Thread.sleep(1_000)
            }
        }.apply { isDaemon = true; start() }
    }

    @org.junit.After fun stopSampling() {
        sampling = false
        sampler?.join(1_500)
    }

    private fun prepare(newHome: Boolean) {
        check(context.packageName.endsWith(".freezeprobe"))
        instrumentation.runOnMainSync {
            AppPreference.init(context)
            AppPreference.setting_use_new_home_screen_preference = newHome
            AppPreference.romaji_map_data_version = 1
        }
        flushPreferences()
    }

    private fun flushPreferences() {
        Class.forName("android.app.QueuedWork").getDeclaredMethod("waitToFinish").invoke(null)
    }

    private fun snapshot(label: String): String {
        val stacks = Thread.getAllStackTraces()
        val mainFrames = stacks[Looper.getMainLooper().thread].orEmpty()
        File(output, "$label.txt").writeText(buildString {
            appendLine("case=$label uptime_ms=${android.os.SystemClock.uptimeMillis()}")
            stacks.entries.sortedBy { it.key.id }.forEach { (thread, frames) ->
                appendLine("id=${thread.id} main=${thread === Looper.getMainLooper().thread} state=${thread.state}")
                frames.forEach { appendLine("  at $it") }
            }
        })
        return mainFrames.joinToString("\n")
    }

    @Test fun pendingPreferenceWriteBlocksLegacyStop() = preferenceCase(false, true)
    @Test fun drainedPreferenceWriteDoesNotBlockLegacyStop() = preferenceCase(false, false)
    @Test fun pendingPreferenceWriteBlocksNewStop() = preferenceCase(true, true)
    @Test fun drainedPreferenceWriteDoesNotBlockNewStop() = preferenceCase(true, false)

    private fun preferenceCase(newHome: Boolean, pending: Boolean) {
        prepare(newHome)
        val label = "preferences-${if (newHome) "new" else "legacy"}-${if (pending) "pending" else "drained"}"
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        lateinit var testedActivity: MainActivity
        scenario.onActivity { testedActivity = it }
        instrumentation.waitForIdleSync()
        flushPreferences()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val field = preferences.javaClass.getDeclaredField("mWritingToDiskLock").apply { isAccessible = true }
        val diskLock = field.get(preferences)
        val ready = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holder = Thread {
            synchronized(diskLock) {
                ready.countDown()
                release.await(15, TimeUnit.SECONDS) // Fail-safe even if the test assertion fails.
            }
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            // This is a real production setter called by IME mode persistence.
            if (!pending) {
                AppPreference.sumire_last_input_mode_saved_at_epoch_millis_preference = System.currentTimeMillis()
                flushPreferences()
            }
            holder.start()
            assertTrue(ready.await(3, TimeUnit.SECONDS))
            if (pending) {
                AppPreference.sumire_last_input_mode_saved_at_epoch_millis_preference = System.currentTimeMillis()
            }
            val stopped = executor.submit { scenario.moveToState(Lifecycle.State.CREATED) }
            if (pending) {
                // Wait for the actual Android wait path, not an arbitrary delay on main.
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                var frames = ""
                while (System.nanoTime() < deadline) {
                    frames = Looper.getMainLooper().thread.stackTrace.joinToString("\n")
                    if (frames.contains("QueuedWork")) break
                    Thread.sleep(20)
                }
                assertTrue("Expected Android lifecycle disk wait: $frames", frames.contains("QueuedWork"))
                repeat(3) { sample ->
                    assertTrue(snapshot("$label-$sample").contains("QueuedWork"))
                    Thread.sleep(200)
                }
                val heartbeat = CountDownLatch(1)
                Handler(Looper.getMainLooper()).post { heartbeat.countDown() }
                assertFalse("Main should be waiting for disk", heartbeat.await(300, TimeUnit.MILLISECONDS))
                release.countDown()
                assertTrue("Main must recover after releasing disk", heartbeat.await(5, TimeUnit.SECONDS))
            } else {
                stopped.get(5, TimeUnit.SECONDS)
                val heartbeat = CountDownLatch(1)
                Handler(Looper.getMainLooper()).post { heartbeat.countDown() }
                assertTrue("No pending write: main remains responsive", heartbeat.await(2, TimeUnit.SECONDS))
                snapshot(label)
            }
            release.countDown()
            stopped.get(5, TimeUnit.SECONDS)
            File(output, "$label.result").writeText("PASS pending=$pending recovered=true\n")
        } finally {
            release.countDown()
            holder.join(2_000)
            executor.shutdownNow()
            // Avoid ActivityScenario's extra EmptyActivity round-trip from an already stopped state.
            instrumentation.runOnMainSync { testedActivity.finish() }
            val destroyDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (scenario.state != Lifecycle.State.DESTROYED && System.nanoTime() < destroyDeadline) {
                Thread.sleep(20)
            }
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
            scenario.close()
        }
    }

    @Test fun databaseWriterContentionDoesNotBlockLegacyMain() = databaseCase(false)
    @Test fun databaseWriterContentionDoesNotBlockNewMain() = databaseCase(true)

    private fun databaseCase(newHome: Boolean) {
        prepare(newHome)
        val label = "database-${if (newHome) "new" else "legacy"}"
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "learn_database").build()
        db.openHelper.writableDatabase.execSQL(
            "DELETE FROM user_word WHERE reading IN ('びゃん', 'びゃんびゃんめん')"
        )
        AppPreference.romaji_map_data_version = 0
        flushPreferences()
        val ready = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holder = Thread {
            db.runInTransaction {
                ready.countDown()
                release.await(15, TimeUnit.SECONDS)
            }
        }
        try {
            holder.start()
            assertTrue(ready.await(3, TimeUnit.SECONDS))
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
                repeat(3) { sample ->
                    val heartbeat = CountDownLatch(1)
                    Handler(Looper.getMainLooper()).post { heartbeat.countDown() }
                    assertTrue("DB writer must not block main", heartbeat.await(2, TimeUnit.SECONDS))
                    snapshot("$label-$sample")
                    Thread.sleep(200)
                }
                assertEquals("Initializer must still be waiting", 0, AppPreference.romaji_map_data_version)
                release.countDown()
                holder.join(5_000)
                assertFalse(holder.isAlive)
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (AppPreference.romaji_map_data_version != 1 && System.nanoTime() < deadline) {
                    Thread.sleep(20)
                }
                assertEquals("Initializer should finish after DB writer releases", 1, AppPreference.romaji_map_data_version)
                File(output, "$label.result").writeText("PASS responsive_while_writer_held=true\n")
            }
        } finally {
            release.countDown()
            holder.join(5_000)
            db.close()
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun blockedPlaybackThreadHasBoundedReleaseWait() {
        prepare(false)
        lateinit var player: androidx.media3.exoplayer.ExoPlayer
        instrumentation.runOnMainSync { player = androidx.media3.exoplayer.ExoPlayer.Builder(context).build() }
        val ready = CountDownLatch(1)
        val release = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val began = CountDownLatch(1)
        var elapsed = 0L
        Handler(player.playbackLooper).post {
            ready.countDown()
            release.await(10, TimeUnit.SECONDS)
        }
        try {
            assertTrue(ready.await(3, TimeUnit.SECONDS))
            Handler(Looper.getMainLooper()).post {
                val start = android.os.SystemClock.uptimeMillis()
                began.countDown()
                try { player.release() } finally {
                    elapsed = android.os.SystemClock.uptimeMillis() - start
                    completed.countDown()
                }
            }
            assertTrue(began.await(3, TimeUnit.SECONDS))
            repeat(3) { sample ->
                snapshot("video-release-$sample")
                Thread.sleep(100)
            }
            assertTrue("Release must time out even while playback is held", completed.await(3, TimeUnit.SECONDS))
            assertTrue("Expected timeout path", elapsed >= 400)
            File(output, "video-release.result").writeText("PASS bounded_release_ms=$elapsed playback_still_held=true\n")
        } finally {
            release.countDown()
            assertTrue(completed.await(5, TimeUnit.SECONDS))
        }
    }
}
