package com.kazumaproject.markdownhelperkeyboard.local_font

import android.content.Context
import android.content.ContextWrapper
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Compare dev's constructor and the lazy constructor with the same delayed storage lookup. */
@RunWith(AndroidJUnit4::class)
class LocalFontConstructionDeviceTest {
    @Test fun delayedStorageLookupCannotBlockMainDuringRepositoryConstruction() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue(context.packageName.endsWith(".freezeprobe"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val expectBlocking = InstrumentationRegistry.getArguments().getString("expectBlocking") == "true"
        val calls = AtomicInteger()
        val delayedContext = object : ContextWrapper(context) {
            override fun getNoBackupFilesDir(): File {
                calls.incrementAndGet()
                Log.i(TAG, "directoryLookup main=${Thread.currentThread() === Looper.getMainLooper().thread}")
                SystemClock.sleep(1_500)
                return super.getNoBackupFilesDir()
            }
        }
        lateinit var repository: LocalFontRepository
        val sampler = Thread {
            repeat(3) {
                SystemClock.sleep(300)
                Log.i(TAG, "mainStack[$it]=" + Looper.getMainLooper().thread.stackTrace.joinToString("\n"))
            }
        }
        sampler.start()
        val start = SystemClock.uptimeMillis()
        instrumentation.runOnMainSync { repository = LocalFontRepository(delayedContext) }
        val elapsed = SystemClock.uptimeMillis() - start
        Log.i(TAG, "constructorMs=$elapsed expectBlocking=$expectBlocking calls=${calls.get()}")
        if (expectBlocking) {
            assertTrue("Baseline must demonstrate the main-thread lookup", elapsed >= 1_500)
            assertEquals(1, calls.get())
        } else {
            assertTrue("Constructing the repository must not touch storage", elapsed < 700)
            assertEquals(0, calls.get())
            val heartbeat = CountDownLatch(1)
            val loading = Thread { runBlocking { repository.loadIfNeeded() } }
            loading.start()
            val deadline = SystemClock.uptimeMillis() + 5_000
            while (calls.get() == 0 && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(10)
            assertEquals(1, calls.get())
            instrumentation.runOnMainSync { heartbeat.countDown() }
            assertTrue("Main must respond during delayed font storage lookup", heartbeat.await(700, TimeUnit.MILLISECONDS))
            loading.join(5_000)
            assertFalse("Font restore did not complete", loading.isAlive)
        }
        sampler.join(3_000)
    }
    @Test fun failedStorageInitializationCanBeRetried() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue(context.packageName.endsWith(".freezeprobe"))
        val calls = AtomicInteger()
        val failingContext = object : ContextWrapper(context) {
            override fun getNoBackupFilesDir(): File {
                if (calls.incrementAndGet() == 1) throw java.io.IOException("Controlled storage failure")
                return super.getNoBackupFilesDir()
            }
        }
        val repository = LocalFontRepository(failingContext)
        try {
            repository.loadIfNeeded()
            fail("First load must expose the storage failure")
        } catch (expected: java.io.IOException) {
            assertEquals("Controlled storage failure", expected.message)
        }
        repository.loadIfNeeded()
        assertEquals("Retry must initialize storage again", 2, calls.get())
    }

    companion object { private const val TAG = "SettingsFontConstruction" }
}
