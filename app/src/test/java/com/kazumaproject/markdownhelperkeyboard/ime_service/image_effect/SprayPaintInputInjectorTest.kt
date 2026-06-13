package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SprayPaintInputInjectorTest {

    @Test
    fun paletteModeCarriesGraffitiStyleIntoSprayCommand() {
        val queue = SprayPaintInputCommandQueue()
        val injector = SprayPaintInputInjector(
            queue = queue,
            clock = { 100L },
            random = Random(0L)
        )

        injector.configure(
            SprayPaintSettings(
                enabled = true,
                colorMode = SprayPaintSettings.COLOR_MODE_PALETTE,
                fixedColor = SprayPaintSettings.DEFAULT_PAINT_COLOR,
                palette = SprayPaintSettings.PALETTE_GRAFFITI
            )
        )
        injector.onPointerDown(pointerId = 7, x = 12f, y = 34f)

        val command = queue.drain().single() as SprayPaintInputCommand.Spray
        assertEquals(SprayPaintPaletteStyle.Graffiti, command.style)
    }

    @Test
    fun fixedColorModeStillCarriesSelectedPaletteStyle() {
        val queue = SprayPaintInputCommandQueue()
        val injector = SprayPaintInputInjector(
            queue = queue,
            clock = { 100L },
            random = Random(0L)
        )

        injector.configure(
            SprayPaintSettings(
                enabled = true,
                colorMode = SprayPaintSettings.COLOR_MODE_FIXED,
                fixedColor = 0xFF8844CC.toInt(),
                palette = SprayPaintSettings.PALETTE_FLOWER_PETALS
            )
        )
        injector.onPointerDown(pointerId = 3, x = 18f, y = 42f)

        val command = queue.drain().single() as SprayPaintInputCommand.Spray
        assertEquals(SprayPaintPaletteStyle.FlowerPetals, command.style)
    }

    @Test
    fun randomColorModeDoesNotRandomizeSelectedPaletteStyle() {
        val queue = SprayPaintInputCommandQueue()
        val injector = SprayPaintInputInjector(
            queue = queue,
            clock = { 100L },
            random = Random(3L)
        )

        injector.configure(
            SprayPaintSettings(
                enabled = true,
                colorMode = SprayPaintSettings.COLOR_MODE_RANDOM,
                fixedColor = SprayPaintSettings.DEFAULT_PAINT_COLOR,
                palette = SprayPaintSettings.PALETTE_LIQUID_PAINT
            )
        )

        repeat(6) { index ->
            injector.onPointerDown(pointerId = index, x = 18f + index, y = 42f)
        }

        val commands = queue.drain().filterIsInstance<SprayPaintInputCommand.Spray>()
        commands.forEach { command ->
            assertEquals(SprayPaintPaletteStyle.LiquidPaint, command.style)
        }
    }
}
