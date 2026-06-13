package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.KeyboardTouchEffectType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardTouchEffectPreferenceVisibilityTest {

    @Test
    fun suminagashiShowsColorModeAndFixedColorOnlyWhenFixed() {
        val random = resolveKeyboardTouchEffectPreferenceVisibility(
            effectType = KeyboardTouchEffectType.SUMINAGASHI_INK,
            colorMode = "random"
        )
        val fixed = resolveKeyboardTouchEffectPreferenceVisibility(
            effectType = KeyboardTouchEffectType.SUMINAGASHI_INK,
            colorMode = "fixed"
        )

        assertTrue(random.showQuality)
        assertTrue(random.showSuminagashiColorMode)
        assertFalse(random.showFixedSuminagashiColor)
        assertTrue(fixed.showQuality)
        assertTrue(fixed.showSuminagashiColorMode)
        assertTrue(fixed.showFixedSuminagashiColor)
    }

    @Test
    fun liquidRippleAndNoneHideSuminagashiColorPreferences() {
        val liquid = resolveKeyboardTouchEffectPreferenceVisibility(
            effectType = KeyboardTouchEffectType.LIQUID_RIPPLE,
            colorMode = "fixed"
        )
        val none = resolveKeyboardTouchEffectPreferenceVisibility(
            effectType = KeyboardTouchEffectType.NONE,
            colorMode = "fixed"
        )

        assertTrue(liquid.showQuality)
        assertFalse(liquid.showSuminagashiColorMode)
        assertFalse(liquid.showFixedSuminagashiColor)
        assertFalse(none.showQuality)
        assertFalse(none.showSuminagashiColorMode)
        assertFalse(none.showFixedSuminagashiColor)
    }
}
