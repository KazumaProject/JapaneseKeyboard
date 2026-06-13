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
            KeyboardTouchEffectType.SUMINAGASHI_INK,
            KeyboardTouchEffectType.normalize("suminagashi_ink")
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
        assertTrue(KeyboardTouchEffectType.isSuminagashi("suminagashi_ink"))
        assertTrue(KeyboardTouchEffectType.isLiquidRipple("liquid_ripple"))
        assertTrue(KeyboardTouchEffectType.isSprayPaint("spray_paint"))
    }
}
