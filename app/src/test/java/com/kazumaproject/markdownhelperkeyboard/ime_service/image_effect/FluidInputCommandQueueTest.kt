package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidInputCommandQueueTest {

    @Test
    fun consecutiveMovesForSamePointerAreCoalesced() {
        val queue = FluidInputCommandQueue(maxSize = 8)

        queue.offer(down(pointerId = 1))
        queue.offer(move(pointerId = 1, x = 10f))
        queue.offer(move(pointerId = 1, x = 20f))

        val commands = queue.drain()

        assertEquals(2, commands.size)
        assertTrue(commands[0] is FluidInputCommand.Splat)
        val latestMove = commands[1] as FluidInputCommand.Splat
        assertEquals(FluidSplatKind.Move, latestMove.kind)
        assertEquals(20f, latestMove.x)
    }

    @Test
    fun oldestMoveIsDroppedWhenQueueIsFull() {
        val queue = FluidInputCommandQueue(maxSize = 3)

        queue.offer(move(pointerId = 1, x = 10f))
        queue.offer(move(pointerId = 2, x = 20f))
        queue.offer(move(pointerId = 3, x = 30f))
        queue.offer(move(pointerId = 4, x = 40f))

        val pointerIds = queue.drain()
            .filterIsInstance<FluidInputCommand.Splat>()
            .map { it.pointerId }

        assertEquals(listOf(2, 3, 4), pointerIds)
    }

    @Test
    fun moveIsRejectedWhenQueueContainsOnlyCriticalCommands() {
        val queue = FluidInputCommandQueue(maxSize = 1)

        queue.offer(down(pointerId = 1))
        val accepted = queue.offer(move(pointerId = 2, x = 20f))

        assertFalse(accepted)
        assertEquals(listOf(1), queue.drain().filterIsInstance<FluidInputCommand.Splat>().map { it.pointerId })
    }

    @Test
    fun upAndCancelCommandsArePreservedEvenWhenQueueIsFull() {
        val queue = FluidInputCommandQueue(maxSize = 1)

        queue.offer(down(pointerId = 1))
        assertTrue(queue.offer(FluidInputCommand.PointerUp(pointerId = 1, eventTimeMillis = 2L)))
        assertTrue(queue.offer(FluidInputCommand.CancelAll(eventTimeMillis = 3L)))

        val commands = queue.drain()

        assertTrue(commands.any { it is FluidInputCommand.PointerUp })
        assertTrue(commands.any { it is FluidInputCommand.CancelAll })
    }

    private fun down(pointerId: Int): FluidInputCommand.Splat {
        return FluidInputCommand.Splat(
            pointerId = pointerId,
            x = 0f,
            y = 0f,
            velocityX = 0f,
            velocityY = 0f,
            color = FluidInkColor(0.1f, 0.2f, 0.3f, 1f),
            radiusPx = 32f,
            kind = FluidSplatKind.Down,
            eventTimeMillis = 1L
        )
    }

    private fun move(pointerId: Int, x: Float): FluidInputCommand.Splat {
        return FluidInputCommand.Splat(
            pointerId = pointerId,
            x = x,
            y = 0f,
            velocityX = 1f,
            velocityY = 0f,
            color = FluidInkColor(0.1f, 0.2f, 0.3f, 1f),
            radiusPx = 24f,
            kind = FluidSplatKind.Move,
            eventTimeMillis = 1L
        )
    }
}
