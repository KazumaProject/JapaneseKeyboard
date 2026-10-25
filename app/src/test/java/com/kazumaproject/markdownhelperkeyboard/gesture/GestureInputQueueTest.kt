package com.kazumaproject.markdownhelperkeyboard.gesture

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GestureInputQueueTest {
    @Test fun enterCannotOvertakeAnUnfinishedConversion() = runTest {
        val ready = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val queue = GestureInputQueue(this)
        queue.enqueue({ true }) { ready.await(); events.add("convert") }
        queue.enqueue({ true }) { events.add("enter") }
        runCurrent()
        assertTrue(events.isEmpty())
        ready.complete(Unit); runCurrent()
        assertEquals(listOf("convert", "enter"), events)
    }
    @Test fun endingTheSessionCancelsBothPendingAndWaitingActions() = runTest {
        val ready = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val queue = GestureInputQueue(this)
        queue.enqueue({ true }) { ready.await(); events.add("old conversion") }
        queue.enqueue({ true }) { events.add("old enter") }
        runCurrent(); queue.cancel()
        ready.complete(Unit)
        queue.enqueue({ true }) { events.add("new editor") }
        runCurrent()
        assertEquals(listOf("new editor"), events)
    }
    @Test fun changedKeyboardOrConnectionDoesNotReceiveQueuedInput() = runTest {
        val events = mutableListOf<String>()
        var active = true
        val queue = GestureInputQueue(this)
        queue.enqueue({ active }) { events.add("stale") }
        active = false
        runCurrent()
        assertTrue(events.isEmpty())
    }
}
