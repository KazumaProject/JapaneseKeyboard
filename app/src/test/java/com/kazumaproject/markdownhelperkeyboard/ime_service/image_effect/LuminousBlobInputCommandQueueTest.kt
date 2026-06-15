package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LuminousBlobInputCommandQueueTest {

    @Test
    fun latestMoveForSamePointerReplacesQueuedMoveAndPreservesSegmentStart() {
        val queue = LuminousBlobInputCommandQueue(maxSize = 8)

        queue.offer(down(pointerId = 1))
        queue.offer(move(pointerId = 1, previousX = 0f, x = 12f))
        queue.offer(move(pointerId = 1, previousX = 12f, x = 30f))

        val commands = queue.drain()

        assertEquals(2, commands.size)
        val latestMove = commands[1] as LuminousBlobInputCommand.Pointer
        assertEquals(LuminousBlobInputKind.Move, latestMove.kind)
        assertEquals(0f, latestMove.previousX)
        assertEquals(30f, latestMove.x)
    }

    @Test
    fun downUpAndCancelCommandsArePreservedEvenWhenQueueIsFull() {
        val queue = LuminousBlobInputCommandQueue(maxSize = 1)

        queue.offer(down(pointerId = 1))
        assertTrue(queue.offer(LuminousBlobInputCommand.PointerUp(pointerId = 1, eventTimeMillis = 2L)))
        assertTrue(queue.offer(LuminousBlobInputCommand.CancelAll(eventTimeMillis = 3L)))

        val commands = queue.drain()

        assertTrue(commands.any {
            it is LuminousBlobInputCommand.Pointer && it.kind == LuminousBlobInputKind.Down
        })
        assertTrue(commands.any { it is LuminousBlobInputCommand.PointerUp })
        assertTrue(commands.any { it is LuminousBlobInputCommand.CancelAll })
    }

    private fun down(pointerId: Int): LuminousBlobInputCommand.Pointer {
        return LuminousBlobInputCommand.Pointer(
            pointerId = pointerId,
            x = 0f,
            y = 0f,
            previousX = 0f,
            previousY = 0f,
            velocityX = 0f,
            velocityY = 0f,
            colorSet = LuminousBlobColorSet.Default,
            kind = LuminousBlobInputKind.Down,
            eventTimeMillis = 1L
        )
    }

    private fun move(
        pointerId: Int,
        previousX: Float,
        x: Float
    ): LuminousBlobInputCommand.Pointer {
        return LuminousBlobInputCommand.Pointer(
            pointerId = pointerId,
            x = x,
            y = 0f,
            previousX = previousX,
            previousY = 0f,
            velocityX = 1f,
            velocityY = 0f,
            colorSet = LuminousBlobColorSet.Default,
            kind = LuminousBlobInputKind.Move,
            eventTimeMillis = 2L
        )
    }
}
