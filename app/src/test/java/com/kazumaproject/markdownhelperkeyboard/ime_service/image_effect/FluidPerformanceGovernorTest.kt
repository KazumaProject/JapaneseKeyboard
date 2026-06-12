package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidPerformanceGovernorTest {

    @Test
    fun stepParamsFavorCalmWaterDriftOverAggressiveSwirls() {
        val governor = FluidPerformanceGovernor()

        val active = governor.stepParams(FluidRendererState.Active)
        val settling = governor.stepParams(FluidRendererState.Settling)
        val idle = governor.stepParams(FluidRendererState.IdlePersistent)

        assertTrue(active.vorticity <= 5.6f)
        assertTrue(settling.vorticity < active.vorticity)
        assertTrue(idle.vorticity < settling.vorticity)
        assertTrue(active.velocityDissipation < 0.99f)
        assertTrue(idle.waterDrift > active.waterDrift)
        assertEquals(1f, active.dyeDissipation, 0.001f)
    }
}
