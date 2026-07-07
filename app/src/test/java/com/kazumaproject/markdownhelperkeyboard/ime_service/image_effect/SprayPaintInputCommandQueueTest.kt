package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SprayPaintInputCommandQueueTest {

    @Test
    fun unknownPaletteFallsBackToPaintSplashStyle() {
        assertEquals(
            SprayPaintSettings.PALETTE_PAINT_SPLASH,
            SprayPaintSettings.normalizePalette("monochrome_ink")
        )
        assertEquals(
            SprayPaintPaletteStyle.PaintSplash,
            SprayPaintSettings.styleForPalette("monochrome_ink")
        )
    }

    @Test
    fun legacyPaletteValuesNormalizeToNewSubEffects() {
        assertEquals(
            SprayPaintSettings.PALETTE_PAINT_SPLASH,
            SprayPaintSettings.normalizePalette("vivid_paint")
        )
        assertEquals(
            SprayPaintSettings.PALETTE_GRAFFITI,
            SprayPaintSettings.normalizePalette("neon_graffiti")
        )
        assertEquals(
            SprayPaintSettings.PALETTE_SPRAY,
            SprayPaintSettings.normalizePalette("soft_pastel")
        )
        assertEquals(
            SprayPaintSettings.PALETTE_FLOWER_PETALS,
            SprayPaintSettings.normalizePalette("sumire")
        )
    }

    @Test
    fun latestMoveForSamePointerReplacesQueuedMoveAndPreservesSegmentStart() {
        val queue = SprayPaintInputCommandQueue(maxSize = 8)

        queue.offer(down(pointerId = 1))
        queue.offer(move(pointerId = 1, previousX = 0f, x = 10f))
        queue.offer(move(pointerId = 1, previousX = 10f, x = 24f))

        val commands = queue.drain()

        assertEquals(2, commands.size)
        val latestMove = commands[1] as SprayPaintInputCommand.Spray
        assertEquals(SprayPaintEmissionKind.Move, latestMove.kind)
        assertEquals(0f, latestMove.previousX)
        assertEquals(24f, latestMove.x)
    }

    @Test
    fun oldestMoveIsDroppedWhenQueueIsFull() {
        val queue = SprayPaintInputCommandQueue(maxSize = 3)

        queue.offer(move(pointerId = 1, previousX = 0f, x = 10f))
        queue.offer(move(pointerId = 2, previousX = 0f, x = 20f))
        queue.offer(move(pointerId = 3, previousX = 0f, x = 30f))
        queue.offer(move(pointerId = 4, previousX = 0f, x = 40f))

        val pointerIds = queue.drain()
            .filterIsInstance<SprayPaintInputCommand.Spray>()
            .map { it.pointerId }

        assertEquals(listOf(2, 3, 4), pointerIds)
    }

    @Test
    fun downUpAndCancelCommandsArePreservedEvenWhenQueueIsFull() {
        val queue = SprayPaintInputCommandQueue(maxSize = 1)

        queue.offer(down(pointerId = 1))
        assertTrue(queue.offer(SprayPaintInputCommand.PointerUp(pointerId = 1, eventTimeMillis = 2L)))
        assertTrue(queue.offer(SprayPaintInputCommand.CancelAll(eventTimeMillis = 3L)))

        val commands = queue.drain()

        assertTrue(commands.any { it is SprayPaintInputCommand.Spray && it.kind == SprayPaintEmissionKind.Down })
        assertTrue(commands.any { it is SprayPaintInputCommand.PointerUp })
        assertTrue(commands.any { it is SprayPaintInputCommand.CancelAll })
    }

    private fun down(pointerId: Int): SprayPaintInputCommand.Spray {
        return SprayPaintInputCommand.Spray(
            pointerId = pointerId,
            x = 0f,
            y = 0f,
            previousX = 0f,
            previousY = 0f,
            velocityX = 0f,
            velocityY = 0f,
            color = SprayPaintColor(0.1f, 0.2f, 0.3f, 1f),
            style = SprayPaintPaletteStyle.PaintSplash,
            kind = SprayPaintEmissionKind.Down,
            eventTimeMillis = 1L
        )
    }

    private fun move(
        pointerId: Int,
        previousX: Float,
        x: Float
    ): SprayPaintInputCommand.Spray {
        return SprayPaintInputCommand.Spray(
            pointerId = pointerId,
            x = x,
            y = 0f,
            previousX = previousX,
            previousY = 0f,
            velocityX = 1f,
            velocityY = 0f,
            color = SprayPaintColor(0.1f, 0.2f, 0.3f, 1f),
            style = SprayPaintPaletteStyle.PaintSplash,
            kind = SprayPaintEmissionKind.Move,
            eventTimeMillis = 1L
        )
    }
}
