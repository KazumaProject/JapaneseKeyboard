package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_cursor

import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35])
class CursorAnchorCompositionProbeTest {
    private fun anchor(caret: Int, composing: String? = null) = CursorAnchorInfo.Builder()
        .setSelectionRange(caret, caret)
        .apply { if (composing != null) setComposingText(0, composing) }
        .build()

    @Test fun nativeCompletionAndFilteredLiveCompositionAreDistinguishedWithoutTextStyles() = runTest {
        val editor = mock<InputConnection>()
        whenever(editor.requestCursorUpdates(any())).thenReturn(true)
        val probe = CursorAnchorCompositionProbe({ editor }, { 1L }, { 0 }, StandardTestDispatcher(testScheduler))
        val complete = async { probe.read(editor, 0, 0) }
        runCurrent()
        probe.onUpdate(anchor(0))
        assertEquals(false, complete.await())

        val live = async { probe.read(editor, 2, 2) }
        runCurrent()
        probe.onUpdate(anchor(2, "AA"))
        assertEquals(true, live.await())
    }

    @Test fun unsupportedRequestIsUnknownAndRetainsExistingMonitoring() = runTest {
        val editor = mock<InputConnection>()
        whenever(editor.requestCursorUpdates(any())).thenReturn(false)
        val probe = CursorAnchorCompositionProbe({ editor }, { 1L },
            { InputConnection.CURSOR_UPDATE_MONITOR }, StandardTestDispatcher(testScheduler))
        val read = async { probe.read(editor, 0, 0) }
        runCurrent()
        assertNull(read.await())
        verify(editor).requestCursorUpdates(InputConnection.CURSOR_UPDATE_IMMEDIATE or InputConnection.CURSOR_UPDATE_MONITOR)
    }

    @Test fun aDifferentSelectionIsNotAnAnswerToTheCurrentSnapshot() = runTest {
        val editor = mock<InputConnection>()
        whenever(editor.requestCursorUpdates(any())).thenReturn(true)
        val probe = CursorAnchorCompositionProbe({ editor }, { 1L }, { 0 }, StandardTestDispatcher(testScheduler))
        val read = async { probe.read(editor, 2, 2) }
        runCurrent()
        probe.onUpdate(anchor(0))
        assertFalse(read.isCompleted)
        advanceTimeBy(301)
        runCurrent()
        assertNull(read.await())
    }

    @Test fun writesAndEditorChangesInvalidatePendingAnchorInfo() = runTest {
        val first = mock<InputConnection>()
        val second = mock<InputConnection>()
        whenever(first.requestCursorUpdates(any())).thenReturn(true)
        whenever(second.requestCursorUpdates(any())).thenReturn(true)
        var editor = first
        var revision = 1L
        val probe = CursorAnchorCompositionProbe({ editor }, { revision }, { 0 }, StandardTestDispatcher(testScheduler))
        val written = async { probe.read(first, 0, 0) }
        runCurrent()
        revision++
        probe.onUpdate(anchor(0))
        advanceTimeBy(301)
        runCurrent()
        assertNull(written.await())

        val switched = async { probe.read(first, 0, 0) }
        runCurrent()
        editor = second
        probe.onUpdate(anchor(0))
        advanceTimeBy(301)
        runCurrent()
        assertNull(switched.await())
    }

    @Test fun resetAndCancellationRemoveThePendingCallback() = runTest {
        val editor = mock<InputConnection>()
        whenever(editor.requestCursorUpdates(any())).thenReturn(true)
        val probe = CursorAnchorCompositionProbe({ editor }, { 1L }, { 0 }, StandardTestDispatcher(testScheduler))
        val reset = async { probe.read(editor, 0, 0) }
        runCurrent()
        probe.reset()
        probe.onUpdate(anchor(0))
        assertNull(reset.await())

        val canceled = async { probe.read(editor, 0, 0) }
        runCurrent()
        canceled.cancel()
        runCurrent()
        probe.onUpdate(anchor(0))
        assertTrue(canceled.isCancelled)
    }

    @Test fun callbackBeforeRequestAcceptanceIsNotUsedAsCompletion() = runTest {
        val editor = mock<InputConnection>()
        val probe = CursorAnchorCompositionProbe({ editor }, { 1L }, { 0 }, StandardTestDispatcher(testScheduler))
        var requests = 0
        whenever(editor.requestCursorUpdates(any())).thenAnswer {
            requests++
            probe.onUpdate(if (requests == 1) anchor(0) else anchor(0, "あ"))
            true
        }
        val read = async { probe.read(editor, 0, 0) }
        runCurrent()
        assertEquals(true, read.await())
        assertEquals(2, requests)
    }

    @Test fun inputWaitTimesOutEvenWhileTheRemoteRequestIsBlocked() = runTest {
        val editor = mock<InputConnection>()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        whenever(editor.requestCursorUpdates(any())).thenAnswer {
            entered.countDown()
            release.await(2, TimeUnit.SECONDS)
            true
        }
        val probe = CursorAnchorCompositionProbe({ editor }, { 1L }, { 0 }, Dispatchers.IO)
        val read = async { probe.read(editor, 0, 0) }
        try {
            runCurrent()
            assertTrue("Remote request did not start", entered.await(1, TimeUnit.SECONDS))
            advanceTimeBy(301)
            runCurrent()
            assertTrue("Blocked request held the input wait past its timeout", read.isCompleted)
            assertNull(read.await())
            probe.onUpdate(anchor(0))
        } finally {
            release.countDown()
            probe.reset()
        }
    }
}
