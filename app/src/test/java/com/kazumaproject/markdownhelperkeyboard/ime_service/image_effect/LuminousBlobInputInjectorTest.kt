package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LuminousBlobInputInjectorTest {

    @Test
    fun fixedColorModeCarriesFixedColorIntoDownCommand() {
        val queue = LuminousBlobInputCommandQueue()
        val injector = LuminousBlobInputInjector(
            queue = queue,
            clock = { 100L },
            random = Random(0L)
        )

        injector.configure(
            LuminousBlobSettings(
                enabled = true,
                colorMode = LuminousBlobSettings.COLOR_MODE_FIXED,
                fixedColor = 0xFF8844CC.toInt()
            )
        )
        injector.onPointerDown(pointerId = 7, x = 12f, y = 34f)

        val command = queue.drain().single() as LuminousBlobInputCommand.Pointer
        assertEquals(LuminousBlobInputKind.Down, command.kind)
        assertEquals(0x88 / 255f, command.colorSet.base.red)
        assertEquals(0x44 / 255f, command.colorSet.base.green)
        assertEquals(0xCC / 255f, command.colorSet.base.blue)
    }

    @Test
    fun moveUpdatesVelocityAndCancelClearsPointerState() {
        var now = 100L
        val queue = LuminousBlobInputCommandQueue()
        val injector = LuminousBlobInputInjector(
            queue = queue,
            clock = { now },
            random = Random(0L)
        )

        injector.configure(
            LuminousBlobSettings(
                enabled = true,
                colorMode = LuminousBlobSettings.COLOR_MODE_RANDOM,
                fixedColor = LuminousBlobSettings.DEFAULT_BASE_COLOR
            )
        )
        injector.onPointerDown(pointerId = 3, x = 10f, y = 20f)
        now = 116L
        injector.onPointerMove(pointerId = 3, x = 26f, y = 28f)
        injector.onCancel()

        val commands = queue.drain()
        val move = commands.filterIsInstance<LuminousBlobInputCommand.Pointer>()
            .single { it.kind == LuminousBlobInputKind.Move }

        assertEquals(1f, move.velocityX)
        assertEquals(0.5f, move.velocityY)
        assertEquals(0, injector.pointerStateCountForTesting())
        assertTrue(commands.any { it is LuminousBlobInputCommand.CancelAll })
    }
}
