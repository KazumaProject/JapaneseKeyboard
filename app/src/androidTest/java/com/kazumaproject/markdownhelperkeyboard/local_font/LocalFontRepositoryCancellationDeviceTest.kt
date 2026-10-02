package com.kazumaproject.markdownhelperkeyboard.local_font

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LocalFontRepositoryCancellationDeviceTest {
    @Test
    fun cancellationDuringQueryDoesNotReadLateReturnedCursor() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val repository = LocalFontRepository(context)
        val resolver = context.contentResolver
        val uri = Uri.parse(
            "content://com.kazumaproject.markdownhelperkeyboard.lite.localfonttest/font/late-blocked-cursor",
        )
        repository.loadIfNeeded()

        var selection: kotlinx.coroutines.Job? = null
        try {
            val started = launch(Dispatchers.IO) { repository.prepare(uri) }
            selection = started
            waitForLateCursorQuery(resolver, uri)

            val cancelStartedAt = SystemClock.elapsedRealtime()
            instrumentation.runOnMainSync { started.cancel() }
            val cancelDuration = SystemClock.elapsedRealtime() - cancelStartedAt
            assertTrue(
                "cancellation on Main waited ${cancelDuration}ms for provider query",
                cancelDuration < MAIN_CANCELLATION_LIMIT_MS,
            )

            val mainEventHandled = CountDownLatch(1)
            Handler(Looper.getMainLooper()).post { mainEventHandled.countDown() }
            assertTrue(
                "Main should continue processing while provider query is blocked",
                mainEventHandled.await(MAIN_CANCELLATION_LIMIT_MS, TimeUnit.MILLISECONDS),
            )

            resolver.call(uri, "releaseLateCursorQuery", null, null)
            withTimeout(OPERATION_TIMEOUT_MS) {
                while (resolver.call(uri, "lateCursorReturned", null, null)?.getBoolean("returned") != true) {
                    delay(25)
                }
            }
            withTimeout(OPERATION_TIMEOUT_MS) { started.join() }
            withTimeout(OPERATION_TIMEOUT_MS) {
                while (resolver.call(uri, "lateCursorCloseCount", null, null)?.getInt("count") != 1) {
                    delay(25)
                }
            }
            assertEquals(0, resolver.call(uri, "lateCursorMoveCount", null, null)?.getInt("count"))
            assertEquals(1, resolver.call(uri, "lateCursorCloseCount", null, null)?.getInt("count"))
        } finally {
            runCatching { resolver.call(uri, "releaseLateCursorQuery", null, null) }
            selection?.cancelAndJoin()
        }
    }

    @Test
    fun cancellingOnMainDoesNotWaitForBlockedCursorClose() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val repository = LocalFontRepository(context)
        val resolver = context.contentResolver
        val blockedUri = Uri.parse(
            "content://com.kazumaproject.markdownhelperkeyboard.lite.localfonttest/font/blocked-cursor",
        )
        repository.loadIfNeeded()

        var selection: kotlinx.coroutines.Job? = null
        try {
            val started = launch(Dispatchers.IO) { repository.prepare(blockedUri) }
            selection = started
            waitForBlockedCursor(resolver, blockedUri)

            val releaseThread = Thread {
                Thread.sleep(CURSOR_RELEASE_DELAY_MS)
                resolver.call(blockedUri, "releaseBlockedCursor", null, null)
            }.apply {
                name = "LocalFontTestCursorRelease"
                isDaemon = true
                start()
            }

            val cancelStartedAt = SystemClock.elapsedRealtime()
            instrumentation.runOnMainSync {
                started.cancel()
                started.cancel()
            }
            val cancelDuration = SystemClock.elapsedRealtime() - cancelStartedAt
            assertTrue(
                "cancellation on Main waited ${cancelDuration}ms for Cursor.close()",
                cancelDuration < MAIN_CANCELLATION_LIMIT_MS,
            )

            val mainEventHandled = CountDownLatch(1)
            Handler(Looper.getMainLooper()).post { mainEventHandled.countDown() }
            assertTrue(
                "Main should continue processing events while Cursor.close() is blocked",
                mainEventHandled.await(MAIN_CANCELLATION_LIMIT_MS, TimeUnit.MILLISECONDS),
            )
            withTimeout(OPERATION_TIMEOUT_MS) { started.join() }
            releaseThread.join(OPERATION_TIMEOUT_MS)
            waitForBlockedCursorClose(resolver, blockedUri)
        } finally {
            runCatching { resolver.call(blockedUri, "releaseBlockedCursor", null, null) }
            selection?.cancelAndJoin()
        }
    }

    @Test
    fun cancellingBlockedProviderReadAllowsAnotherSelectionAndRestore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = LocalFontRepository(context)
        val resolver = context.contentResolver
        val authority = "com.kazumaproject.markdownhelperkeyboard.lite.localfonttest"
        val blockedUri = Uri.parse("content://$authority/font/blocked")
        val validUri = Uri.parse("content://$authority/font/valid")
        repository.loadIfNeeded()

        val selections = mutableListOf<kotlinx.coroutines.Job>()
        try {
            resolver.call(blockedUri, "reset", null, null)
            val firstSelection = launch(Dispatchers.IO) { repository.prepare(blockedUri) }
            selections += firstSelection
            waitForBlockedRead(resolver, blockedUri)
            firstSelection.cancel()
            withTimeout(OPERATION_TIMEOUT_MS) { firstSelection.join() }

            val nextSelection = withTimeout(OPERATION_TIMEOUT_MS) { repository.prepare(validUri) }
            repository.cancelPreview(nextSelection)

            resolver.call(blockedUri, "reset", null, null)
            val finalSelection = launch(Dispatchers.IO) { repository.prepare(blockedUri) }
            selections += finalSelection
            waitForBlockedRead(resolver, blockedUri)
            withTimeout(OPERATION_TIMEOUT_MS) { repository.restoreStandard() }
            withTimeout(OPERATION_TIMEOUT_MS) { finalSelection.join() }
            assertNull(repository.state.value.displayName)
        } finally {
            runCatching { resolver.call(blockedUri, "release", null, null) }
            selections.forEach { it.cancelAndJoin() }
        }
    }

    private suspend fun waitForBlockedRead(resolver: android.content.ContentResolver, uri: Uri) {
        withTimeout(OPERATION_TIMEOUT_MS) {
            while (resolver.call(uri, "opened", null, null)?.getBoolean("opened") != true) {
                delay(25)
            }
        }
        delay(300)
    }

    private suspend fun waitForBlockedCursor(resolver: android.content.ContentResolver, uri: Uri) {
        withTimeout(OPERATION_TIMEOUT_MS) {
            while (resolver.call(uri, "cursorMoveStarted", null, null)?.getBoolean("started") != true) {
                delay(25)
            }
        }
    }

    private suspend fun waitForLateCursorQuery(resolver: android.content.ContentResolver, uri: Uri) {
        withTimeout(OPERATION_TIMEOUT_MS) {
            while (resolver.call(uri, "lateCursorQueryStarted", null, null)?.getBoolean("started") != true) {
                delay(25)
            }
        }
    }

    private suspend fun waitForBlockedCursorClose(resolver: android.content.ContentResolver, uri: Uri) {
        withTimeout(OPERATION_TIMEOUT_MS) {
            while (resolver.call(uri, "cursorCloseCount", null, null)?.getInt("count") != 1) {
                delay(25)
            }
        }
        assertEquals(1, resolver.call(uri, "cursorCloseCount", null, null)?.getInt("count"))
    }

    private companion object {
        const val OPERATION_TIMEOUT_MS = 5_000L
        const val CURSOR_RELEASE_DELAY_MS = 1_500L
        const val MAIN_CANCELLATION_LIMIT_MS = 750L
    }
}
