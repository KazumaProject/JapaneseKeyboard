package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.KeyboardTouchEffectType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardTouchEffectPreferenceVisibilityTest {

    @Test
    fun inkEffectsShowColorModeAndFixedColorOnlyWhenFixed() {
        val random = resolveKeyboardTouchEffectPreferenceVisibility(
            effectType = KeyboardTouchEffectType.LIQUID_INK,
            colorMode = "random"
        )
        val fixed = resolveKeyboardTouchEffectPreferenceVisibility(
            effectType = KeyboardTouchEffectType.AURORA_INK,
            colorMode = "fixed"
        )

        assertTrue(random.showQuality)
        assertTrue(random.showColorMode)
        assertFalse(random.showFixedColor)
        assertFalse(random.showPalette)
        assertTrue(fixed.showQuality)
        assertTrue(fixed.showColorMode)
        assertTrue(fixed.showFixedColor)
        assertFalse(fixed.showPalette)
    }

    @Test
    fun sprayPaintShowsColorControlsAndPalette() {
        val random = resolveKeyboardTouchEffectPreferenceVisibility(
            effectType = KeyboardTouchEffectType.SPRAY_PAINT,
            colorMode = "random"
        )
        val fixed = resolveKeyboardTouchEffectPreferenceVisibility(
            effectType = KeyboardTouchEffectType.SPRAY_PAINT,
            colorMode = "fixed"
        )

        assertTrue(random.showQuality)
        assertTrue(random.showColorMode)
        assertFalse(random.showFixedColor)
        assertTrue(random.showPalette)
        assertTrue(fixed.showFixedColor)
        assertTrue(fixed.showPalette)
    }

    @Test
    fun liquidRippleAndNoneHideColorPreferences() {
        val liquid = resolveKeyboardTouchEffectPreferenceVisibility(
            effectType = KeyboardTouchEffectType.LIQUID_RIPPLE,
            colorMode = "fixed"
        )
        val none = resolveKeyboardTouchEffectPreferenceVisibility(
            effectType = KeyboardTouchEffectType.NONE,
            colorMode = "fixed"
        )

        assertTrue(liquid.showQuality)
        assertFalse(liquid.showColorMode)
        assertFalse(liquid.showFixedColor)
        assertFalse(liquid.showPalette)
        assertFalse(none.showQuality)
        assertFalse(none.showColorMode)
        assertFalse(none.showFixedColor)
        assertFalse(none.showPalette)
    }

    @Test
    fun luminousBlobShowsColorModeAndFixedColorOnlyWhenFixedWithoutPalette() {
        val random = resolveKeyboardTouchEffectPreferenceVisibility(
            effectType = KeyboardTouchEffectType.LUMINOUS_BLOB,
            colorMode = "random"
        )
        val fixed = resolveKeyboardTouchEffectPreferenceVisibility(
            effectType = KeyboardTouchEffectType.LUMINOUS_BLOB,
            colorMode = "fixed"
        )

        assertTrue(random.showQuality)
        assertTrue(random.showColorMode)
        assertFalse(random.showFixedColor)
        assertFalse(random.showPalette)
        assertTrue(fixed.showQuality)
        assertTrue(fixed.showColorMode)
        assertTrue(fixed.showFixedColor)
        assertFalse(fixed.showPalette)
    }

    @Test
    fun cinematicWaveUsesDedicatedSettingsInsteadOfSharedColorControls() {
        val random = resolveKeyboardTouchEffectPreferenceVisibility(
            effectType = KeyboardTouchEffectType.CINEMATIC_WAVE,
            colorMode = "cinematic_random"
        )
        val custom = resolveKeyboardTouchEffectPreferenceVisibility(
            effectType = KeyboardTouchEffectType.CINEMATIC_WAVE,
            colorMode = "custom"
        )

        assertFalse(random.showQuality)
        assertFalse(random.showColorMode)
        assertFalse(random.showFixedColor)
        assertFalse(random.showPalette)
        assertTrue(random.showCinematicWaveSettings)
        assertFalse(random.showCinematicWaveCustomColors)

        assertTrue(custom.showCinematicWaveSettings)
        assertTrue(custom.showCinematicWaveCustomColors)
    }
}
