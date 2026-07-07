package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidRipplePerformanceGovernorTest {

    @Test
    fun userQualityControlsNormalSamplingAndDamping() {
        val governor = LiquidRipplePerformanceGovernor()

        governor.configureQuality(KeyboardTouchEffectQuality.BALANCED)
        val balanced = governor.stepParams(LiquidRippleRendererState.Active)
        assertEquals(0, balanced.normalSampleMode)
        assertTrue(balanced.damping < 0.99f)

        governor.configureQuality(KeyboardTouchEffectQuality.HIGH)
        val highActive = governor.stepParams(LiquidRippleRendererState.Active)
        val highSettling = governor.stepParams(LiquidRippleRendererState.Settling)
        assertEquals(1, highActive.normalSampleMode)
        assertTrue(highActive.waveSpeed < 0.15f)
        assertTrue(highActive.damping < highSettling.damping)

        governor.configureQuality(KeyboardTouchEffectQuality.ULTRA)
        assertEquals(2, governor.stepParams(LiquidRippleRendererState.Active).normalSampleMode)

        governor.configureQuality(KeyboardTouchEffectQuality.EXTREME)
        assertEquals(3, governor.stepParams(LiquidRippleRendererState.Active).normalSampleMode)
    }

    @Test
    fun highQualityWaitsTwentyOverBudgetFramesBeforeGridFallback() {
        val governor = LiquidRipplePerformanceGovernor()
        governor.configureQuality(KeyboardTouchEffectQuality.HIGH)

        repeat(19) {
            assertEquals(false, governor.reportFrameTime(100L, LiquidRippleRendererState.Active))
        }

        assertEquals(0, governor.qualityLevel())
        assertEquals(true, governor.reportFrameTime(100L, LiquidRippleRendererState.Active))
        assertEquals(-1, governor.qualityLevel())
    }
}
