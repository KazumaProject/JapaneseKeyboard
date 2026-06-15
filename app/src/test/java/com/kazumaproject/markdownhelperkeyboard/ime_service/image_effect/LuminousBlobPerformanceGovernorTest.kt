package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LuminousBlobPerformanceGovernorTest {

    @Test
    fun balancedProfileUsesLowerRenderScaleAndAmbientFps() {
        val governor = LuminousBlobPerformanceGovernor()

        governor.configureQuality(KeyboardTouchEffectQuality.BALANCED)

        assertEquals(
            0.5f,
            governor.stepParams(LuminousBlobRendererState.Ambient).renderScale
        )
        assertEquals(50L, governor.frameIntervalMillis(LuminousBlobRendererState.Ambient))
        assertEquals(22L, governor.frameIntervalMillis(LuminousBlobRendererState.Active))
    }

    @Test
    fun heavyFrameDropsRuntimeQualityForNextFrame() {
        val governor = LuminousBlobPerformanceGovernor()
        governor.configureQuality(KeyboardTouchEffectQuality.HIGH)
        val before = governor.stepParams(LuminousBlobRendererState.Active).renderScale

        val changed = governor.reportFrameTime(
            frameMillis = 200L,
            state = LuminousBlobRendererState.Active
        )
        val after = governor.stepParams(LuminousBlobRendererState.Active).renderScale

        assertTrue(changed)
        assertTrue(after < before)
    }
}
