package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardTouchEffectQualityTest {

    @Test
    fun normalizesUnknownValuesToHigh() {
        assertEquals(KeyboardTouchEffectQuality.HIGH, KeyboardTouchEffectQuality.normalize(null))
        assertEquals(KeyboardTouchEffectQuality.HIGH, KeyboardTouchEffectQuality.normalize("bad"))
    }

    @Test
    fun keepsKnownQualityValues() {
        assertEquals(
            KeyboardTouchEffectQuality.BALANCED,
            KeyboardTouchEffectQuality.normalize(KeyboardTouchEffectQuality.BALANCED)
        )
        assertEquals(
            KeyboardTouchEffectQuality.HIGH,
            KeyboardTouchEffectQuality.normalize(KeyboardTouchEffectQuality.HIGH)
        )
        assertEquals(
            KeyboardTouchEffectQuality.ULTRA,
            KeyboardTouchEffectQuality.normalize(KeyboardTouchEffectQuality.ULTRA)
        )
        assertEquals(
            KeyboardTouchEffectQuality.EXTREME,
            KeyboardTouchEffectQuality.normalize(KeyboardTouchEffectQuality.EXTREME)
        )
    }
}
