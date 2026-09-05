package com.kazumaproject.custom_keyboard.view

import android.view.InputDevice
import android.view.MotionEvent
import com.kazumaproject.core.domain.touch.PointerGestureEvent
import com.kazumaproject.core.domain.touch.PointerGestureRouter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PointerGestureRouterTest {
    @Test
    fun actionIndexSelectsLiftedIdAndExistingPointerMovesBeforeNewDown() {
        val router = PointerGestureRouter()
        val events = mutableListOf<String>()

        route(
            router,
            action = MotionEvent.ACTION_DOWN,
            pointers = listOf(Pointer(7, 10f, 20f)),
            eventTime = 100L,
            events = events,
        )
        route(
            router,
            action = MotionEvent.ACTION_POINTER_DOWN,
            actionIndex = 1,
            pointers = listOf(
                Pointer(7, 30f, 40f),
                Pointer(19, 50f, 60f),
            ),
            eventTime = 110L,
            events = events,
        )
        route(
            router,
            action = MotionEvent.ACTION_POINTER_UP,
            actionIndex = 0,
            pointers = listOf(
                Pointer(7, 35f, 45f),
                Pointer(19, 50f, 60f),
            ),
            eventTime = 120L,
            events = events,
        )
        route(
            router,
            action = MotionEvent.ACTION_UP,
            pointers = listOf(Pointer(19, 55f, 65f)),
            eventTime = 130L,
            events = events,
        )

        assertEquals(
            listOf("DOWN:7", "MOVE:7", "DOWN:19", "UP:7", "UP:19"),
            events,
        )
    }

    @Test
    fun newerPointerCanLiftBeforeOlderPointerWithoutSwappingOwnership() {
        val router = PointerGestureRouter()
        val events = mutableListOf<String>()

        route(
            router,
            action = MotionEvent.ACTION_DOWN,
            pointers = listOf(Pointer(7, 10f, 20f)),
            eventTime = 300L,
            events = events,
        )
        route(
            router,
            action = MotionEvent.ACTION_POINTER_DOWN,
            actionIndex = 1,
            pointers = listOf(
                Pointer(7, 30f, 40f),
                Pointer(19, 50f, 60f),
            ),
            eventTime = 310L,
            events = events,
        )
        route(
            router,
            action = MotionEvent.ACTION_POINTER_UP,
            actionIndex = 1,
            pointers = listOf(
                Pointer(7, 35f, 45f),
                Pointer(19, 55f, 65f),
            ),
            eventTime = 320L,
            events = events,
        )
        route(
            router,
            action = MotionEvent.ACTION_UP,
            pointers = listOf(Pointer(7, 40f, 50f)),
            eventTime = 330L,
            events = events,
        )

        assertEquals(
            listOf("DOWN:7", "MOVE:7", "DOWN:19", "UP:19", "UP:7"),
            events,
        )
    }

    @Test
    fun actionCancelClosesEveryStillOwnedPointerExactlyOnce() {
        val router = PointerGestureRouter()
        val events = mutableListOf<String>()

        route(
            router,
            action = MotionEvent.ACTION_DOWN,
            pointers = listOf(Pointer(7, 10f, 20f)),
            eventTime = 200L,
            events = events,
        )
        route(
            router,
            action = MotionEvent.ACTION_POINTER_DOWN,
            actionIndex = 1,
            pointers = listOf(
                Pointer(7, 30f, 40f),
                Pointer(19, 50f, 60f),
            ),
            eventTime = 210L,
            events = events,
        )
        route(
            router,
            action = MotionEvent.ACTION_CANCEL,
            pointers = listOf(
                Pointer(7, 30f, 40f),
                Pointer(19, 50f, 60f),
            ),
            eventTime = 220L,
            events = events,
        )
        route(
            router,
            action = MotionEvent.ACTION_UP,
            pointers = listOf(Pointer(19, 55f, 65f)),
            eventTime = 230L,
            events = events,
        )

        assertEquals(
            listOf("DOWN:7", "MOVE:7", "DOWN:19", "CANCEL:7", "CANCEL:19"),
            events,
        )
    }

    private fun route(
        router: PointerGestureRouter,
        action: Int,
        actionIndex: Int = 0,
        pointers: List<Pointer>,
        eventTime: Long,
        events: MutableList<String>,
    ) {
        val properties = Array(pointers.size) {
            MotionEvent.PointerProperties().apply {
                id = pointers[it].id
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coordinates = Array(pointers.size) {
            MotionEvent.PointerCoords().apply {
                x = pointers[it].x
                y = pointers[it].y
                pressure = 1f
                size = 1f
            }
        }
        val encodedAction = action or
            (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        val event = MotionEvent.obtain(
            eventTime,
            eventTime,
            encodedAction,
            pointers.size,
            properties,
            coordinates,
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0,
        )
        try {
            router.route(event) { semanticEvent ->
                val type = when (semanticEvent) {
                    is PointerGestureEvent.Down -> "DOWN"
                    is PointerGestureEvent.Move -> "MOVE"
                    is PointerGestureEvent.Up -> "UP"
                    is PointerGestureEvent.Cancel -> "CANCEL"
                }
                events += "${type}:${semanticEvent.pointerId}"
            }
        } finally {
            event.recycle()
        }
    }

    private data class Pointer(
        val id: Int,
        val x: Float,
        val y: Float,
    )
}
