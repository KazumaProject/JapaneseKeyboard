package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FluidInputInjectorTest {

    @Test
    fun fixedColorIsAppliedToDownCommand() {
        val queue = FluidInputCommandQueue()
        val injector = FluidInputInjector(queue, clock = { 10L }, random = Random(0))
        val fixedColor = Color.rgb(64, 96, 128)

        injector.configure(colorMode = "fixed", fixedColor = fixedColor)
        injector.onPointerDown(pointerId = 1, x = 10f, y = 20f)

        val command = queue.drain().single() as FluidInputCommand.Splat
        assertEquals(64 / 255f, command.color.red, 0.001f)
        assertEquals(96 / 255f, command.color.green, 0.001f)
        assertEquals(128 / 255f, command.color.blue, 0.001f)
        assertEquals(1f, command.color.alpha, 0.001f)
    }

    @Test
    fun randomColorModeIgnoresFixedColorForTouchDown() {
        val queue = FluidInputCommandQueue()
        val injector = FluidInputInjector(queue, clock = { 10L }, random = Random(0))

        injector.configure(colorMode = "random", fixedColor = Color.RED)
        injector.onPointerDown(pointerId = 1, x = 10f, y = 20f)

        val command = queue.drain().single() as FluidInputCommand.Splat
        assertNotEquals(1f, command.color.red, 0.001f)
    }

    @Test
    fun randomColorModeUsesMutedTranslucentWaInkColors() {
        val queue = FluidInputCommandQueue()
        val injector = FluidInputInjector(queue, clock = { 10L }, random = Random(1))

        injector.configure(colorMode = "random", fixedColor = Color.RED)

        repeat(12) { pointerId ->
            injector.onPointerDown(pointerId = pointerId, x = 10f, y = 20f)
        }

        val commands = queue.drain().filterIsInstance<FluidInputCommand.Splat>()
        assertEquals(12, commands.size)
        commands.forEach { command ->
            val maxChannel = maxOf(command.color.red, command.color.green, command.color.blue)
            assertTrue("Random suminagashi color should stay rich but muted", maxChannel <= 0.52f)
            assertTrue("Random suminagashi ink should be visibly dense", command.color.alpha in 0.77f..0.94f)
        }
    }

    @Test
    fun moveCommandIncludesPointerVelocity() {
        var now = 100L
        val queue = FluidInputCommandQueue()
        val injector = FluidInputInjector(queue, clock = { now }, random = Random(0))

        injector.configure(colorMode = "fixed", fixedColor = Color.rgb(10, 20, 30))
        injector.onPointerDown(pointerId = 1, x = 10f, y = 10f)
        now = 110L
        injector.onPointerMove(pointerId = 1, x = 25f, y = 15f)

        val move = queue.drain()
            .filterIsInstance<FluidInputCommand.Splat>()
            .last()

        assertEquals(FluidSplatKind.Move, move.kind)
        assertEquals(1.5f, move.velocityX, 0.001f)
        assertEquals(0.5f, move.velocityY, 0.001f)
    }

    @Test
    fun cancelDoesNotBecomePointerUp() {
        val queue = FluidInputCommandQueue()
        val injector = FluidInputInjector(queue, clock = { 10L }, random = Random(0))

        injector.configure(colorMode = "fixed", fixedColor = Color.rgb(10, 20, 30))
        injector.onPointerDown(pointerId = 1, x = 10f, y = 10f)
        injector.onCancel()

        val commands = queue.drain()

        assertTrue(commands.any { it is FluidInputCommand.CancelAll })
        assertTrue(commands.none { it is FluidInputCommand.PointerUp })
        assertEquals(0, injector.pointerStateCountForTesting())
    }
}
