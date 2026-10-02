package com.kazumaproject.core.domain.flick

import android.view.MotionEvent
import android.view.View
import com.kazumaproject.core.domain.extensions.touchScreenCoordinates
import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.listener.KeyTouchCancelReason
import com.kazumaproject.core.domain.state.PressedKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One single-pointer recognizer session, owned by an Android pointer ID. */
class IndependentKeyTouchSession(
    val pointerId: Int,
    val key: Key,
    var pressedKey: PressedKey,
    val thresholdPx: Float,
    val thresholdShape: FlickThresholdShape,
    val longPressTimeoutMillis: Long,
    var lastEvent: MotionEvent
) {
    var longPressJob: Job? = null
    var isLongPressed = false
}

/**
 * Routes independent pointers through the keyboard's existing single-finger recognizer.
 * Events delivered to that recognizer always have one pointer with ID/index zero. Android's
 * actual IDs remain the ownership keys here, including when its pointer indices change.
 */
class IndependentKeyTouchDispatcher(
    private val view: View,
    private val scope: CoroutineScope,
    private val hitTest: (MotionEvent, Int) -> Key,
    private val threshold: () -> Float,
    private val thresholdShape: () -> FlickThresholdShape,
    private val longPressTimeout: () -> Long,
    private val isExclusiveKey: (Key) -> Boolean,
    private val isExclusiveMode: () -> Boolean,
    private val dispatch: (IndependentKeyTouchSession, MotionEvent) -> Unit,
    private val onLongPress: (IndependentKeyTouchSession) -> Unit,
    private val onCancel: (IndependentKeyTouchSession, KeyTouchCancelReason) -> Unit,
    private val restore: (List<IndependentKeyTouchSession>, IndependentKeyTouchSession?) -> Unit
) {
    private val sessions = linkedMapOf<Int, IndependentKeyTouchSession>()
    private var focusedPointerId: Int? = null
    val activeSessions: List<IndependentKeyTouchSession> get() = sessions.values.toList()

    fun contains(session: IndependentKeyTouchSession): Boolean = sessions[session.pointerId] === session

    fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelAll(KeyTouchCancelReason.PointerInterrupted)
                begin(event, event.actionIndex)
            }
            MotionEvent.ACTION_POINTER_DOWN -> begin(event, event.actionIndex)
            MotionEvent.ACTION_MOVE -> {
                // Copy before dispatch: callbacks may hide/rebuild the keyboard and cancel all.
                sessions.values.toList().forEach { session ->
                    val index = event.findPointerIndex(session.pointerId)
                    if (index >= 0 && contains(session)) {
                        val (x, y) = view.touchScreenCoordinates(event, index)
                        if (x != session.lastEvent.rawX || y != session.lastEvent.rawY) {
                            updateEvent(session, event, index, MotionEvent.ACTION_MOVE)
                            focusedPointerId = session.pointerId
                            dispatchSession(session)
                        }
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val index = event.actionIndex
                val session = sessions[event.getPointerId(index)]
                if (session != null) {
                    updateEvent(session, event, index, MotionEvent.ACTION_UP)
                    // Remove before dispatch so callbacks can cancel the remaining sessions.
                    sessions.remove(session.pointerId)
                    session.longPressJob?.cancel()
                    dispatchSession(session)
                    session.lastEvent.recycle()
                    if (focusedPointerId == session.pointerId) {
                        focusedPointerId = sessions.keys.lastOrNull()
                    }
                    restore(activeSessions, sessions[focusedPointerId])
                }
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    cancelAll(KeyTouchCancelReason.PointerInterrupted)
                }
            }
            MotionEvent.ACTION_CANCEL -> cancelAll(KeyTouchCancelReason.ActionCancel)
        }
        return true
    }

    private fun begin(event: MotionEvent, index: Int) {
        if ((isExclusiveMode() && sessions.isNotEmpty()) || sessions.values.any { isExclusiveKey(it.key) }) return
        val key = hitTest(event, index)
        if (key == Key.NotSelected || sessions.values.any { it.key == key }) return
        if (isExclusiveKey(key)) cancelAll(KeyTouchCancelReason.PointerInterrupted)
        val (x, y) = view.touchScreenCoordinates(event, index)
        val session = IndependentKeyTouchSession(
            pointerId = event.getPointerId(index), key = key,
            pressedKey = PressedKey(key, 0, x, y),
            thresholdPx = threshold(), thresholdShape = thresholdShape(),
            longPressTimeoutMillis = longPressTimeout(),
            lastEvent = singlePointerEvent(event, index, MotionEvent.ACTION_DOWN, event.eventTime)
        )
        sessions[session.pointerId] = session
        focusedPointerId = session.pointerId
        dispatchSession(session)
        // The legacy recognizer decides which keys support long press. Replace its timer with
        // one that restores this pointer's session before calling the same long-press handler.
        val supportsLongPress = session.longPressJob != null
        session.longPressJob?.cancel()
        if (supportsLongPress && contains(session)) {
            session.longPressJob = scope.launch {
                delay(session.longPressTimeoutMillis)
                if (!contains(session)) return@launch
                session.isLongPressed = true
                focusedPointerId = session.pointerId
                onLongPress(session)
                if (isExclusiveMode()) cancelOthers(session)
            }
        }
        restore(activeSessions, null)
    }

    private fun cancelOthers(owner: IndependentKeyTouchSession) {
        if (!contains(owner)) return
        sessions.values.toList().filter { it !== owner }.forEach {
            if (contains(it)) {
                sessions.remove(it.pointerId)
                cancel(it, KeyTouchCancelReason.PointerInterrupted)
            }
        }
        if (!contains(owner)) return
        focusedPointerId = owner.pointerId
        restore(activeSessions, owner)
    }

    fun cancelAll(reason: KeyTouchCancelReason) {
        val canceled = activeSessions
        sessions.clear()
        focusedPointerId = null
        canceled.forEach { cancel(it, reason) }
    }

    private fun cancel(session: IndependentKeyTouchSession, reason: KeyTouchCancelReason) {
        session.longPressJob?.cancel()
        onCancel(session, reason)
        session.lastEvent.recycle()
    }

    private fun dispatchSession(session: IndependentKeyTouchSession) {
        // A callback can hide/rebuild the view and cancel this session. Keep the event being
        // dispatched alive independently of the retained event recycled by cancelAll().
        val event = MotionEvent.obtain(session.lastEvent)
        try { dispatch(session, event) } finally { event.recycle() }
    }

    private fun updateEvent(session: IndependentKeyTouchSession, source: MotionEvent, index: Int, action: Int) {
        val previous = session.lastEvent
        session.lastEvent = singlePointerEvent(source, index, action, previous.downTime)
        previous.recycle()
    }

    private fun singlePointerEvent(source: MotionEvent, index: Int, action: Int, downTime: Long): MotionEvent {
        val (rawX, rawY) = view.touchScreenCoordinates(source, index)
        return MotionEvent.obtain(downTime, source.eventTime, action, rawX, rawY, source.metaState).apply {
            // Keep local hit-testing coordinates and raw screen coordinates consistent.
            offsetLocation(source.getX(index) - rawX, source.getY(index) - rawY)
        }
    }
}
