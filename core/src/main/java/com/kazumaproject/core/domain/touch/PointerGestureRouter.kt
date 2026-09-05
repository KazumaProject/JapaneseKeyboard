package com.kazumaproject.core.domain.touch

import android.view.MotionEvent

/**
 * Semantic touch events used by keyboard surfaces.
 *
 * A semantic event always carries the stable pointer ID and display coordinates. The raw
 * MotionEvent index is deliberately not part of the event, because an index can change after a
 * different pointer is lifted.
 */
sealed interface PointerGestureEvent {
    val pointerId: Int
    val eventTime: Long
    val screenX: Float
    val screenY: Float

    data class Down(
        override val pointerId: Int,
        override val eventTime: Long,
        override val screenX: Float,
        override val screenY: Float,
    ) : PointerGestureEvent

    data class Move(
        override val pointerId: Int,
        override val eventTime: Long,
        override val screenX: Float,
        override val screenY: Float,
    ) : PointerGestureEvent

    data class Up(
        override val pointerId: Int,
        override val eventTime: Long,
        override val screenX: Float,
        override val screenY: Float,
    ) : PointerGestureEvent

    data class Cancel(
        override val pointerId: Int,
        override val eventTime: Long,
        override val screenX: Float,
        override val screenY: Float,
    ) : PointerGestureEvent
}

/**
 * Converts a raw MotionEvent stream into pointer-ID based semantic events.
 *
 * The router owns only stream identity. A keyboard surface still decides whether a second
 * pointer commits an older gesture, suppresses a special key, or is allowed to remain active.
 * That policy belongs above this class and is therefore testable without duplicating Android's
 * pointer-index rules in every view.
 */
class PointerGestureRouter {
    private val activePointers = LinkedHashMap<Int, PointerSample>()
    private var streamActive = false

    fun route(
        event: MotionEvent,
        emit: (PointerGestureEvent) -> Unit,
    ) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointers.clear()
                streamActive = true
                PointerEventResolver.actionPointer(event)?.let { sample ->
                    activePointers[sample.pointerId] = sample
                    emit(sample.toDown(event.eventTime))
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // The POINTER_DOWN frame contains the latest coordinate for every existing
                // pointer. Publish those samples before registering the new pointer so a view can
                // finalize an older gesture without reading a stale frame.
                activePointers.keys.toList().forEach { pointerId ->
                    val sample = PointerEventResolver.forId(event, pointerId)
                    if (sample == null) {
                        val last = activePointers.remove(pointerId)
                        last?.let { emit(it.toCancel(event.eventTime)) }
                    } else {
                        activePointers[pointerId] = sample
                        emit(sample.toMove(event.eventTime))
                    }
                }

                PointerEventResolver.actionPointer(event)?.let { sample ->
                    // A duplicate pointer ID is malformed input. Close the old ownership before
                    // accepting the new down so one ID can never own two gestures.
                    activePointers.remove(sample.pointerId)?.let {
                        emit(it.toCancel(event.eventTime))
                    }
                    activePointers[sample.pointerId] = sample
                    emit(sample.toDown(event.eventTime))
                }
            }

            MotionEvent.ACTION_MOVE -> {
                activePointers.keys.toList().forEach { pointerId ->
                    val sample = PointerEventResolver.forId(event, pointerId)
                    if (sample == null) {
                        val last = activePointers.remove(pointerId)
                        last?.let { emit(it.toCancel(event.eventTime)) }
                    } else {
                        activePointers[pointerId] = sample
                        emit(sample.toMove(event.eventTime))
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                PointerEventResolver.actionPointer(event)?.let { sample ->
                    if (activePointers.remove(sample.pointerId) != null) {
                        emit(sample.toUp(event.eventTime))
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                if (!streamActive) return
                // ACTION_UP is the stream terminator. Emit exactly one terminal event for every
                // pointer that is still owned, even if a malformed producer omitted an earlier
                // POINTER_UP.
                val remaining = activePointers.keys.toList()
                if (remaining.isEmpty()) {
                    streamActive = false
                } else {
                    remaining.forEach { pointerId ->
                        val sample = PointerEventResolver.forId(event, pointerId)
                            ?: activePointers[pointerId]
                        activePointers.remove(pointerId)
                        sample?.let { emit(it.toUp(event.eventTime)) }
                    }
                }
                streamActive = false
            }

            MotionEvent.ACTION_CANCEL -> {
                activePointers.values.toList().forEach { sample ->
                    emit(sample.toCancel(event.eventTime))
                }
                activePointers.clear()
                streamActive = false
            }
        }
    }

    fun reset() {
        activePointers.clear()
        streamActive = false
    }

    private fun PointerSample.toDown(eventTime: Long) = PointerGestureEvent.Down(
        pointerId = pointerId,
        eventTime = eventTime,
        screenX = screenX,
        screenY = screenY,
    )

    private fun PointerSample.toMove(eventTime: Long) = PointerGestureEvent.Move(
        pointerId = pointerId,
        eventTime = eventTime,
        screenX = screenX,
        screenY = screenY,
    )

    private fun PointerSample.toUp(eventTime: Long) = PointerGestureEvent.Up(
        pointerId = pointerId,
        eventTime = eventTime,
        screenX = screenX,
        screenY = screenY,
    )

    private fun PointerSample.toCancel(eventTime: Long) = PointerGestureEvent.Cancel(
        pointerId = pointerId,
        eventTime = eventTime,
        screenX = screenX,
        screenY = screenY,
    )
}
