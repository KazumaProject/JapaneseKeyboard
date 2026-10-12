package com.kazumaproject.markdownhelperkeyboard.ime_service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SelectionOrderedInputTest {
    @Test fun gesturesReadFreshPreeditInOrderAfterSelectionCleanup() = runTest {
        val checked = CompletableDeferred<Unit>()
        var preedit = "old"
        val seen = mutableListOf<String>()
        val queue = SelectionOrderedInput(this, { !checked.isCompleted }, { checked.await() })
        assertTrue(queue.defer({ true }) { seen.add(preedit); preedit = "new" })
        assertTrue(queue.defer({ true }) { seen.add(preedit) })
        runCurrent()
        assertTrue(seen.isEmpty())
        preedit = ""
        checked.complete(Unit); runCurrent()
        assertEquals(listOf("", "new"), seen)
        assertFalse(queue.defer({ true }) { fail("Ordinary input must run at its entry point") })
    }

    @Test fun replayDoesNotDeferItselfAndAnOldEditorReceivesNoInput() = runTest {
        val checked = CompletableDeferred<Unit>()
        var current = true
        var applied = 0
        val queue = SelectionOrderedInput(this, { !checked.isCompleted }, { checked.await() })
        queue.defer({ current }) { applied++ }
        current = false
        queue.defer({ true }) {
            assertFalse(queue.defer({ true }) { fail("Recursive replay") })
            applied++
        }
        checked.complete(Unit); runCurrent()
        assertEquals(1, applied)
    }

    @Test fun canceledWorkerCannotEraseTheNewSessionsQueue() = runTest {
        var check = CompletableDeferred<Unit>()
        val queue = SelectionOrderedInput(this, { true }, { check.await() })
        var applied = 0
        queue.defer({ true }) { fail("Canceled session") }; runCurrent()
        queue.cancel()
        check = CompletableDeferred()
        queue.defer({ true }) { applied++ }; runCurrent()
        check.complete(Unit); runCurrent()
        assertEquals(1, applied)
    }
}
