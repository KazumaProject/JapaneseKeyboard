package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardTouchEffectTypeTest {

    @Test
    fun normalizeReturnsKnownValuesAndFallsBackToNone() {
        assertEquals(
            KeyboardTouchEffectType.NONE,
            KeyboardTouchEffectType.normalize(null)
        )
        assertEquals(
            KeyboardTouchEffectType.NONE,
            KeyboardTouchEffectType.normalize("unknown")
        )
        assertEquals(
            KeyboardTouchEffectType.LIQUID_INK,
            KeyboardTouchEffectType.normalize("liquid_ink")
        )
        assertEquals(
            KeyboardTouchEffectType.LIQUID_INK,
            KeyboardTouchEffectType.normalize("suminagashi")
        )
        assertEquals(
            KeyboardTouchEffectType.LIQUID_INK,
            KeyboardTouchEffectType.normalize("suminagashi_ink")
        )
        assertEquals(
            KeyboardTouchEffectType.AURORA_INK,
            KeyboardTouchEffectType.normalize("aurora_ink")
        )
        assertEquals(
            KeyboardTouchEffectType.LIQUID_RIPPLE,
            KeyboardTouchEffectType.normalize("liquid_ripple")
        )
        assertEquals(
            KeyboardTouchEffectType.SPRAY_PAINT,
            KeyboardTouchEffectType.normalize("spray_paint")
        )
    }

    @Test
    fun typeChecksNormalizeValuesBeforeMatching() {
        assertFalse(KeyboardTouchEffectType.isEnabled("unknown"))
        assertTrue(KeyboardTouchEffectType.isLiquidInk("liquid_ink"))
        assertTrue(KeyboardTouchEffectType.isLiquidInk("suminagashi"))
        assertTrue(KeyboardTouchEffectType.isLiquidInk("suminagashi_ink"))
        assertTrue(KeyboardTouchEffectType.isAuroraInk("aurora_ink"))
        assertTrue(KeyboardTouchEffectType.isSuminagashi("suminagashi_ink"))
        assertTrue(KeyboardTouchEffectType.isLiquidRipple("liquid_ripple"))
        assertTrue(KeyboardTouchEffectType.isSprayPaint("spray_paint"))
        assertFalse(KeyboardTouchEffectType.isLiquidRipple("aurora_ink"))
        assertFalse(KeyboardTouchEffectType.isSprayPaint("aurora_ink"))
    }
}
