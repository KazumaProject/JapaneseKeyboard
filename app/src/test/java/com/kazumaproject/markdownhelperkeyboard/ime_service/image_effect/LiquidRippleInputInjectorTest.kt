package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidRippleInputInjectorTest {

    @Test
    fun downMoveUpCommandsAreQueuedSafely() {
        var now = 100L
        val queue = LiquidRippleInputCommandQueue()
        val injector = LiquidRippleInputInjector(queue, clock = { now })

        injector.configure(enabled = true)
        injector.onPointerDown(pointerId = 1, x = 20f, y = 30f)
        now = 116L
        injector.onPointerMove(pointerId = 1, x = 42f, y = 36f)
        now = 132L
        injector.onPointerUp(pointerId = 1, x = 42f, y = 36f)

        val commands = queue.drain()

        assertTrue(commands.any { it is LiquidRippleInputCommand.Impulse })
        assertTrue(commands.any { it is LiquidRippleInputCommand.PointerUp })
        val move = commands
            .filterIsInstance<LiquidRippleInputCommand.Impulse>()
            .first { it.kind == LiquidRippleImpulseKind.Move }
        assertTrue(move.directionX > 0f)
        assertTrue(move.directionY < 0f)
        assertTrue(move.strength <= 0.078f)
        assertEquals(0, injector.pointerStateCountForTesting())
    }

    @Test
    fun fastMoveImpulseIsCompressedSoFlicksDoNotAccelerateWaterTooMuch() {
        var now = 100L
        val queue = LiquidRippleInputCommandQueue()
        val injector = LiquidRippleInputInjector(queue, clock = { now })

        injector.configure(enabled = true)
        injector.onPointerDown(pointerId = 1, x = 0f, y = 0f)
        now = 101L
        injector.onPointerMove(pointerId = 1, x = 180f, y = 0f)

        val move = queue.drain()
            .filterIsInstance<LiquidRippleInputCommand.Impulse>()
            .first { it.kind == LiquidRippleImpulseKind.Move }

        assertTrue(move.strength <= 0.078f)
        assertTrue(move.radiusPx >= 38f)
    }

    @Test
    fun cancelClearsActivePointersWithoutPointerUp() {
        val queue = LiquidRippleInputCommandQueue()
        val injector = LiquidRippleInputInjector(queue, clock = { 10L })

        injector.configure(enabled = true)
        injector.onPointerDown(pointerId = 7, x = 10f, y = 20f)
        injector.onCancel()

        val commands = queue.drain()

        assertTrue(commands.any { it is LiquidRippleInputCommand.CancelAll })
        assertTrue(commands.none { it is LiquidRippleInputCommand.PointerUp })
        assertEquals(0, injector.pointerStateCountForTesting())
    }

    @Test
    fun disablingClearsQueuedCommandsAndActivePointers() {
        val queue = LiquidRippleInputCommandQueue()
        val injector = LiquidRippleInputInjector(queue, clock = { 10L })

        injector.configure(enabled = true)
        injector.onPointerDown(pointerId = 2, x = 10f, y = 20f)
        injector.disable()

        assertEquals(0, queue.sizeForTesting())
        assertEquals(0, injector.pointerStateCountForTesting())
    }
}
