package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.graphics.Color
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.KeyboardTouchEffectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPreferenceSuminagashiInkTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun suminagashiInkPreferences_useDefaultsWhenUnset() {
        assertFalse(AppPreference.suminagashi_ink_effect_preference)
        assertEquals(KeyboardTouchEffectType.NONE, AppPreference.keyboard_touch_effect_type_preference)
        assertEquals("random", AppPreference.suminagashi_ink_color_mode_preference)
        assertEquals(Color.rgb(17, 17, 17), AppPreference.suminagashi_ink_color_preference)
        assertEquals("random", AppPreference.keyboard_touch_effect_color_mode_preference)
        assertEquals(Color.rgb(17, 17, 17), AppPreference.keyboard_touch_effect_color_preference)
        assertEquals("vivid_paint", AppPreference.keyboard_touch_effect_palette_preference)
    }

    @Test
    fun suminagashiInkPreferences_saveAndReadBack() {
        val fixedColor = Color.rgb(50, 110, 78)

        AppPreference.suminagashi_ink_effect_preference = true
        AppPreference.suminagashi_ink_color_mode_preference = "fixed"
        AppPreference.suminagashi_ink_color_preference = fixedColor

        assertTrue(AppPreference.suminagashi_ink_effect_preference)
        assertEquals("fixed", AppPreference.suminagashi_ink_color_mode_preference)
        assertEquals(fixedColor, AppPreference.suminagashi_ink_color_preference)
    }

    @Test
    fun suminagashiInkColorMode_normalizesUnknownValuesToRandom() {
        AppPreference.suminagashi_ink_color_mode_preference = "unexpected"

        assertEquals("random", AppPreference.suminagashi_ink_color_mode_preference)
        assertEquals(
            "random",
            PreferenceManager.getDefaultSharedPreferences(context)
                .getString("suminagashi_ink_color_mode_preference", null)
        )
    }

    @Test
    fun keyboardTouchEffectTypeUsesLegacySuminagashiBooleanWhenUnset() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)

        preferences.edit()
            .putBoolean("suminagashi_ink_effect_preference", true)
            .remove("keyboard_touch_effect_type_preference")
            .commit()

        assertEquals(
            KeyboardTouchEffectType.SUMINAGASHI_INK,
            AppPreference.keyboard_touch_effect_type_preference
        )

        preferences.edit()
            .putBoolean("suminagashi_ink_effect_preference", false)
            .remove("keyboard_touch_effect_type_preference")
            .commit()

        assertEquals(
            KeyboardTouchEffectType.NONE,
            AppPreference.keyboard_touch_effect_type_preference
        )
    }

    @Test
    fun keyboardTouchEffectTypeSyncsLegacySuminagashiBoolean() {
        AppPreference.keyboard_touch_effect_type_preference = KeyboardTouchEffectType.SUMINAGASHI_INK

        assertEquals(
            KeyboardTouchEffectType.SUMINAGASHI_INK,
            AppPreference.keyboard_touch_effect_type_preference
        )
        assertTrue(AppPreference.suminagashi_ink_effect_preference)

        AppPreference.keyboard_touch_effect_type_preference = KeyboardTouchEffectType.LIQUID_RIPPLE

        assertEquals(
            KeyboardTouchEffectType.LIQUID_RIPPLE,
            AppPreference.keyboard_touch_effect_type_preference
        )
        assertFalse(AppPreference.suminagashi_ink_effect_preference)

        AppPreference.keyboard_touch_effect_type_preference = KeyboardTouchEffectType.NONE

        assertEquals(KeyboardTouchEffectType.NONE, AppPreference.keyboard_touch_effect_type_preference)
        assertFalse(AppPreference.suminagashi_ink_effect_preference)
    }

    @Test
    fun keyboardTouchEffectTypeNormalizesUnknownValuesToNone() {
        AppPreference.keyboard_touch_effect_type_preference = "unexpected"

        assertEquals(KeyboardTouchEffectType.NONE, AppPreference.keyboard_touch_effect_type_preference)
        assertFalse(AppPreference.suminagashi_ink_effect_preference)
    }

    @Test
    fun keyboardTouchEffectColorModeFallsBackToLegacySuminagashiKeyWhenUnset() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit()
            .putString("suminagashi_ink_color_mode_preference", "fixed")
            .remove("keyboard_touch_effect_color_mode_preference")
            .commit()

        assertEquals("fixed", AppPreference.keyboard_touch_effect_color_mode_preference)
    }

    @Test
    fun keyboardTouchEffectColorFallsBackToLegacySuminagashiKeyWhenUnset() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val legacyColor = Color.rgb(50, 110, 78)
        preferences.edit()
            .putInt("suminagashi_ink_color_preference", legacyColor)
            .remove("keyboard_touch_effect_color_preference")
            .commit()

        assertEquals(legacyColor, AppPreference.keyboard_touch_effect_color_preference)
    }

    @Test
    fun keyboardTouchEffectColorKeysPreferNewValuesWhenSet() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val legacyColor = Color.rgb(50, 110, 78)
        val newColor = Color.rgb(180, 48, 42)
        preferences.edit()
            .putString("suminagashi_ink_color_mode_preference", "fixed")
            .putInt("suminagashi_ink_color_preference", legacyColor)
            .putString("keyboard_touch_effect_color_mode_preference", "palette")
            .putInt("keyboard_touch_effect_color_preference", newColor)
            .commit()

        assertEquals("palette", AppPreference.keyboard_touch_effect_color_mode_preference)
        assertEquals(newColor, AppPreference.keyboard_touch_effect_color_preference)
    }

    @Test
    fun keyboardTouchEffectPaletteNormalizesUnknownToVividPaint() {
        AppPreference.keyboard_touch_effect_palette_preference = "unexpected"

        assertEquals("vivid_paint", AppPreference.keyboard_touch_effect_palette_preference)
    }

    @Test
    fun keyboardTouchEffectPaletteNormalizesRemovedMonochromeToVividPaint() {
        AppPreference.keyboard_touch_effect_palette_preference = "monochrome_ink"

        assertEquals("vivid_paint", AppPreference.keyboard_touch_effect_palette_preference)
    }
}
