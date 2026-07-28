package com.kazumaproject.custom_keyboard.controller

import com.kazumaproject.custom_keyboard.data.DoubleTapBinding
import com.kazumaproject.custom_keyboard.data.DoubleTapPolicy
import com.kazumaproject.custom_keyboard.data.KeyAction

fun interface CancellableTask {
    fun cancel()
}

fun interface TapTaskScheduler {
    fun schedule(delayMillis: Long, task: () -> Unit): CancellableTask
}

/**
 * Resolves committed taps into normal or double-tap actions.
 *
 * This class is intentionally independent from MotionEvent. FlickKeyboardView already owns
 * gesture recognition for normal, flick and long-press keys; feeding only committed taps here
 * prevents a long press or a cancelled gesture from being mistaken for a double tap.
 */
class DoubleTapActionDispatcher(
    private val timeoutMillis: Long,
    private val minimumIntervalMillis: Long,
    private val clockMillis: () -> Long,
    private val scheduler: TapTaskScheduler,
    private val dispatch: (KeyAction) -> Unit
) {
    private data class PendingTap(
        val keyIdentity: String,
        val normalAction: KeyAction,
        val binding: DoubleTapBinding,
        val startedAtMillis: Long,
        val generation: Long,
        val scheduledTask: CancellableTask
    )

    private var pending: PendingTap? = null
    private var generation: Long = 0

    fun onCommittedTap(
        keyIdentity: String,
        normalAction: KeyAction,
        binding: DoubleTapBinding?
    ) {
        val now = clockMillis()
        val previous = pending
        val interval = previous?.let { now - it.startedAtMillis }
        if (
            previous != null &&
            previous.keyIdentity == keyIdentity &&
            interval != null &&
            interval in 0 until minimumIntervalMillis
        ) {
            return
        }
        val isDoubleTap = previous != null &&
            previous.keyIdentity == keyIdentity &&
            previous.binding == binding &&
            interval != null &&
            interval in minimumIntervalMillis..timeoutMillis

        if (isDoubleTap) {
            clearPending()
            dispatch(requireNotNull(binding).action)
            return
        }

        flushPending()
        if (binding == null) {
            dispatch(normalAction)
            return
        }

        if (binding.policy == DoubleTapPolicy.PROMOTE) {
            dispatch(normalAction)
        }

        val thisGeneration = ++generation
        val task = scheduler.schedule(timeoutMillis) {
            val active = pending
            if (active?.generation != thisGeneration) return@schedule
            pending = null
            if (active.binding.policy == DoubleTapPolicy.EXCLUSIVE) {
                dispatch(active.normalAction)
            }
        }
        pending = PendingTap(
            keyIdentity = keyIdentity,
            normalAction = normalAction,
            binding = binding,
            startedAtMillis = now,
            generation = thisGeneration,
            scheduledTask = task
        )
    }

    /**
     * Ends double-tap recognition because a different committed gesture is about to run.
     * An exclusive normal tap is delivered before the interrupting action.
     */
    fun interrupt() {
        flushPending()
    }

    /**
     * Drops all pending work. Use when a keyboard is detached or replaced.
     */
    fun cancel() {
        clearPending()
    }

    private fun flushPending() {
        val active = pending ?: return
        clearPending()
        if (active.binding.policy == DoubleTapPolicy.EXCLUSIVE) {
            dispatch(active.normalAction)
        }
    }

    private fun clearPending() {
        pending?.scheduledTask?.cancel()
        pending = null
        generation++
    }
}
