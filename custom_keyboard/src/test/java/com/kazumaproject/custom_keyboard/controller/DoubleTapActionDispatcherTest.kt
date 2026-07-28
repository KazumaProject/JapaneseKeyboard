package com.kazumaproject.custom_keyboard.controller

import com.kazumaproject.custom_keyboard.data.DoubleTapBinding
import com.kazumaproject.custom_keyboard.data.DoubleTapPolicy
import com.kazumaproject.custom_keyboard.data.KeyAction
import org.junit.Assert.assertEquals
import org.junit.Test

class DoubleTapActionDispatcherTest {
    private var now = 0L
    private val scheduler = FakeScheduler { now }
    private val dispatched = mutableListOf<KeyAction>()
    private val dispatcher = DoubleTapActionDispatcher(
        timeoutMillis = 300,
        minimumIntervalMillis = 40,
        clockMillis = { now },
        scheduler = scheduler,
        dispatch = dispatched::add
    )

    @Test
    fun promote_dispatchesFirstTapImmediately_thenOnlyDoubleTapAction() {
        val binding = DoubleTapBinding(KeyAction.CapLockKey, DoubleTapPolicy.PROMOTE)

        dispatcher.onCommittedTap("shift", KeyAction.ShiftKey, binding)
        now = 120
        dispatcher.onCommittedTap("shift", KeyAction.ShiftKey, binding)

        assertEquals(listOf(KeyAction.ShiftKey, KeyAction.CapLockKey), dispatched)
        scheduler.runDueAt(500)
        assertEquals(listOf(KeyAction.ShiftKey, KeyAction.CapLockKey), dispatched)
    }

    @Test
    fun exclusive_doubleTap_suppressesNormalAction() {
        val binding = DoubleTapBinding(KeyAction.Copy, DoubleTapPolicy.EXCLUSIVE)

        dispatcher.onCommittedTap("select", KeyAction.SelectAll, binding)
        now = 160
        dispatcher.onCommittedTap("select", KeyAction.SelectAll, binding)

        assertEquals(listOf(KeyAction.Copy), dispatched)
    }

    @Test
    fun exclusive_singleTap_dispatchesAfterTimeout() {
        val binding = DoubleTapBinding(KeyAction.Copy, DoubleTapPolicy.EXCLUSIVE)

        dispatcher.onCommittedTap("select", KeyAction.SelectAll, binding)
        scheduler.runDueAt(299)
        assertEquals(emptyList<KeyAction>(), dispatched)
        scheduler.runDueAt(300)

        assertEquals(listOf(KeyAction.SelectAll), dispatched)
    }

    @Test
    fun differentKey_flushesExclusiveTapBeforeNewAction() {
        val binding = DoubleTapBinding(KeyAction.Copy, DoubleTapPolicy.EXCLUSIVE)

        dispatcher.onCommittedTap("select", KeyAction.SelectAll, binding)
        now = 100
        dispatcher.onCommittedTap("space", KeyAction.Space, null)

        assertEquals(listOf(KeyAction.SelectAll, KeyAction.Space), dispatched)
    }

    @Test
    fun tooFastSecondTap_isIgnoredAsTouchBounce() {
        val binding = DoubleTapBinding(KeyAction.CapLockKey, DoubleTapPolicy.PROMOTE)

        dispatcher.onCommittedTap("shift", KeyAction.ShiftKey, binding)
        now = 20
        dispatcher.onCommittedTap("shift", KeyAction.ShiftKey, binding)

        assertEquals(listOf(KeyAction.ShiftKey), dispatched)
    }

    @Test
    fun cancel_dropsPendingExclusiveAction() {
        val binding = DoubleTapBinding(KeyAction.Copy, DoubleTapPolicy.EXCLUSIVE)

        dispatcher.onCommittedTap("select", KeyAction.SelectAll, binding)
        dispatcher.cancel()
        scheduler.runDueAt(500)

        assertEquals(emptyList<KeyAction>(), dispatched)
    }

    private class FakeScheduler(
        private val clock: () -> Long
    ) : TapTaskScheduler {
        private data class Entry(
            val dueAt: Long,
            var canceled: Boolean,
            val task: () -> Unit
        )

        private val entries = mutableListOf<Entry>()

        override fun schedule(delayMillis: Long, task: () -> Unit): CancellableTask {
            val entry = Entry(clock() + delayMillis, false, task)
            entries += entry
            return CancellableTask { entry.canceled = true }
        }

        fun runDueAt(now: Long) {
            entries
                .filter { !it.canceled && it.dueAt <= now }
                .sortedBy { it.dueAt }
                .forEach {
                    it.canceled = true
                    it.task()
                }
        }
    }
}
