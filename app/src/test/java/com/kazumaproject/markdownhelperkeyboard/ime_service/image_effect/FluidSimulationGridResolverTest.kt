package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidSimulationGridResolverTest {

    private val resolver = FluidSimulationGridResolver()

    @Test
    fun userQualityControlsBaseLongSide() {
        assertEquals(384, longSide(resolve(KeyboardTouchEffectQuality.BALANCED)))
        assertEquals(512, longSide(resolve(KeyboardTouchEffectQuality.HIGH)))
        assertEquals(768, longSide(resolve(KeyboardTouchEffectQuality.ULTRA)))
        assertEquals(1024, longSide(resolve(KeyboardTouchEffectQuality.EXTREME)))
    }

    @Test
    fun runtimeQualityLevelFallsBackInSteps() {
        val full = resolver.resolve(3000, 1200, 0, KeyboardTouchEffectQuality.HIGH)
        val level1 = resolver.resolve(3000, 1200, -1, KeyboardTouchEffectQuality.HIGH)
        val level2 = resolver.resolve(3000, 1200, -2, KeyboardTouchEffectQuality.HIGH)
        val level3 = resolver.resolve(3000, 1200, -3, KeyboardTouchEffectQuality.HIGH)

        assertEquals(512, longSide(full))
        assertEquals(448, longSide(level1))
        assertEquals(384, longSide(level2))
        assertEquals(320, longSide(level3))
    }

    @Test
    fun gridKeepsAspectRatioEvenAlignmentAndMinimums() {
        val grid = resolver.resolve(1200, 360, 0, KeyboardTouchEffectQuality.ULTRA)

        assertEquals(0, grid.width % 2)
        assertEquals(0, grid.height % 2)
        assertTrue(grid.width >= 32)
        assertTrue(grid.height >= 32)
        assertTrue(longSide(grid) >= 128)
        assertEquals(1200f / 360f, grid.width.toFloat() / grid.height, 0.08f)

        val invalid = resolver.resolve(0, 0, 0, KeyboardTouchEffectQuality.EXTREME)
        assertEquals(32, invalid.width)
        assertEquals(32, invalid.height)
    }

    private fun resolve(quality: String): FluidSimulationGrid {
        return resolver.resolve(3000, 1200, 0, quality)
    }

    private fun longSide(grid: FluidSimulationGrid): Int = maxOf(grid.width, grid.height)
}
