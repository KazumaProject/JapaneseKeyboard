package com.kazumaproject.markdownhelperkeyboard.ime_service

import java.util.concurrent.atomic.AtomicLong

/**
 * Orders the destructive parts of a fast-input reset and invalidates work from the previous
 * trial before any new transient state is exposed.
 *
 * The coordinator owns only reset generations. The service supplies the state-specific actions,
 * which keeps this class independent from the large IME state graph.
 */
internal class ImeTransientStateResetCoordinator {
    data class Actions(
        val cancelAsyncWork: () -> Unit,
        val resetGestureState: () -> Unit,
        val clearTransientState: () -> Unit,
        val clearViewsAndEffects: () -> Unit,
        val clearInputConnection: () -> Unit,
    )

    private val generation = AtomicLong(0L)

    fun reset(actions: Actions): Long {
        val currentGeneration = generation.incrementAndGet()
        actions.cancelAsyncWork()
        actions.resetGestureState()
        actions.clearTransientState()
        actions.clearViewsAndEffects()
        actions.clearInputConnection()
        return currentGeneration
    }

    fun isCurrent(candidateGeneration: Long): Boolean =
        generation.get() == candidateGeneration

    fun currentGeneration(): Long = generation.get()
}
