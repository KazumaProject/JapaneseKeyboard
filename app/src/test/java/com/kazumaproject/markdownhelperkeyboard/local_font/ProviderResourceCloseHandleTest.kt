package com.kazumaproject.markdownhelperkeyboard.local_font

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ProviderResourceCloseHandleTest {
    @Test
    fun normalCloseRacingCancellationDispatchClosesTheResourceOnlyOnce() {
        repeat(40) {
            val closeCalls = AtomicInteger()
            val closeEntered = CountDownLatch(1)
            val releaseClose = CountDownLatch(1)
            val handle = ProviderResourceCloseHandle(Closeable {
                closeCalls.incrementAndGet()
                closeEntered.countDown()
                check(releaseClose.await(2, TimeUnit.SECONDS))
            })
            val normalClose = Thread { handle.close() }.apply { start() }

            assertTrue(closeEntered.await(2, TimeUnit.SECONDS))
            handle.closeAsync()
            handle.closeAsync()
            releaseClose.countDown()
            normalClose.join(2_000)

            assertTrue("normal close should finish", !normalClose.isAlive)
            assertEquals(1, closeCalls.get())
        }
    }

    @Test
    fun cancellationCloseRunsOffCallerAndCloseFailureDoesNotEscape() {
        val closeCalls = AtomicInteger()
        val closeFinished = CountDownLatch(1)
        val closeThread = arrayOfNulls<Thread>(1)
        val caller = Thread.currentThread()
        val handle = ProviderResourceCloseHandle(Closeable {
            closeCalls.incrementAndGet()
            closeThread[0] = Thread.currentThread()
            closeFinished.countDown()
            throw IllegalStateException("provider close failed")
        })

        handle.closeAsync()

        assertTrue("asynchronous close should finish", closeFinished.await(2, TimeUnit.SECONDS))
        assertNotSame(caller, closeThread[0])
        handle.close()
        handle.closeAsync()
        assertEquals(1, closeCalls.get())
    }
}
