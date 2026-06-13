package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidPerformanceGovernorTest {

    @Test
    fun stepParamsUseVelocityDiffusionAndSlowDyeDissipation() {
        val governor = FluidPerformanceGovernor()

        val active = governor.stepParams(FluidRendererState.Active)
        val settling = governor.stepParams(FluidRendererState.Settling)
        val idle = governor.stepParams(FluidRendererState.IdlePersistent)

        assertTrue(active.vorticity <= 5.6f)
        assertTrue(settling.vorticity < active.vorticity)
        assertTrue(idle.vorticity < settling.vorticity)
        assertTrue(active.velocityDissipation < 0.99f)
        assertTrue(active.velocityDiffusionIterations > 0)
        assertTrue(active.velocityViscosity > 0f)
        assertEquals(0.9984f, active.dyeDissipation, 0.001f)
    }

    @Test
    fun userQualityControlsPressureIterationRange() {
        val governor = FluidPerformanceGovernor()

        governor.configureQuality(KeyboardTouchEffectQuality.BALANCED)
        assertEquals(20, governor.stepParams(FluidRendererState.Active).pressureIterations)

        governor.configureQuality(KeyboardTouchEffectQuality.HIGH)
        assertEquals(32, governor.stepParams(FluidRendererState.Active).pressureIterations)

        governor.configureQuality(KeyboardTouchEffectQuality.ULTRA)
        assertEquals(48, governor.stepParams(FluidRendererState.Active).pressureIterations)

        governor.configureQuality(KeyboardTouchEffectQuality.EXTREME)
        assertEquals(64, governor.stepParams(FluidRendererState.Active).pressureIterations)
    }

    @Test
    fun overBudgetFramesReducePressureBeforeGridResolution() {
        val governor = FluidPerformanceGovernor()
        governor.configureQuality(KeyboardTouchEffectQuality.BALANCED)

        repeat(5) {
            assertEquals(false, governor.reportFrameTime(100L, FluidRendererState.Active))
        }

        assertEquals(10, governor.stepParams(FluidRendererState.Active).pressureIterations)
        assertEquals(0, governor.qualityLevel())

        repeat(7) {
            assertEquals(false, governor.reportFrameTime(100L, FluidRendererState.Active))
        }

        assertEquals(true, governor.reportFrameTime(100L, FluidRendererState.Active))
        assertEquals(-1, governor.qualityLevel())
    }
}
