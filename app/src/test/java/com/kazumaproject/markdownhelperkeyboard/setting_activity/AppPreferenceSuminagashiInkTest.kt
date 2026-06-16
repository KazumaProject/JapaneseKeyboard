package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.graphics.Color
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.CinematicWaveSettings
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.KeyboardTouchEffectType
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.SprayPaintSettings
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
        assertEquals(
            SprayPaintSettings.PALETTE_PAINT_SPLASH,
            AppPreference.keyboard_touch_effect_palette_preference
        )
        assertEquals(
            CinematicWaveSettings.COLOR_MODE_CINEMATIC_RANDOM,
            AppPreference.keyboard_touch_effect_cinematic_wave_color_mode_preference
        )
        assertEquals(
            CinematicWaveSettings.DEFAULT_PRIMARY_COLOR,
            AppPreference.keyboard_touch_effect_cinematic_wave_primary_color_preference
        )
        assertEquals(
            CinematicWaveSettings.DEFAULT_SECONDARY_COLOR,
            AppPreference.keyboard_touch_effect_cinematic_wave_secondary_color_preference
        )
        assertTrue(AppPreference.keyboard_touch_effect_cinematic_wave_secondary_color_auto_preference)
        assertEquals(
            CinematicWaveSettings.WAVE_TYPE_AURORA_MEMBRANE,
            AppPreference.keyboard_touch_effect_cinematic_wave_type_preference
        )
        assertEquals(46, AppPreference.keyboard_touch_effect_cinematic_wave_opacity_percent_preference)
        assertEquals(100, AppPreference.keyboard_touch_effect_cinematic_wave_intensity_percent_preference)
        assertEquals(
            CinematicWaveSettings.MOTION_ELEGANT,
            AppPreference.keyboard_touch_effect_cinematic_wave_motion_preference
        )
        assertEquals(
            CinematicWaveSettings.TOUCH_RESPONSE_NORMAL,
            AppPreference.keyboard_touch_effect_cinematic_wave_touch_response_preference
        )
        assertEquals(
            CinematicWaveSettings.QUALITY_BALANCED,
            AppPreference.keyboard_touch_effect_cinematic_wave_quality_preference
        )
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
            KeyboardTouchEffectType.LIQUID_INK,
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
        AppPreference.keyboard_touch_effect_type_preference = KeyboardTouchEffectType.LIQUID_INK

        assertEquals(
            KeyboardTouchEffectType.LIQUID_INK,
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
    fun keyboardTouchEffectTypeReadsLegacyValuesAsLiquidInk() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)

        listOf("suminagashi", "suminagashi_ink").forEach { legacyValue ->
            preferences.edit()
                .putString("keyboard_touch_effect_type_preference", legacyValue)
                .commit()

            assertEquals(
                KeyboardTouchEffectType.LIQUID_INK,
                AppPreference.keyboard_touch_effect_type_preference
            )
        }
    }

    @Test
    fun keyboardTouchEffectTypeSavesAuroraInkWithoutEnablingLegacyBoolean() {
        AppPreference.keyboard_touch_effect_type_preference = KeyboardTouchEffectType.AURORA_INK

        assertEquals(
            KeyboardTouchEffectType.AURORA_INK,
            AppPreference.keyboard_touch_effect_type_preference
        )
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
    fun keyboardTouchEffectPaletteNormalizesUnknownToPaintSplash() {
        AppPreference.keyboard_touch_effect_palette_preference = "unexpected"

        assertEquals(
            SprayPaintSettings.PALETTE_PAINT_SPLASH,
            AppPreference.keyboard_touch_effect_palette_preference
        )
    }

    @Test
    fun keyboardTouchEffectPaletteNormalizesRemovedMonochromeToPaintSplash() {
        AppPreference.keyboard_touch_effect_palette_preference = "monochrome_ink"

        assertEquals(
            SprayPaintSettings.PALETTE_PAINT_SPLASH,
            AppPreference.keyboard_touch_effect_palette_preference
        )
    }

    @Test
    fun keyboardTouchEffectPaletteNormalizesLegacySprayPaintValues() {
        val cases = mapOf(
            "vivid_paint" to SprayPaintSettings.PALETTE_PAINT_SPLASH,
            "neon_graffiti" to SprayPaintSettings.PALETTE_GRAFFITI,
            "soft_pastel" to SprayPaintSettings.PALETTE_SPRAY,
            "sumire" to SprayPaintSettings.PALETTE_FLOWER_PETALS
        )

        cases.forEach { (legacyValue, expectedValue) ->
            AppPreference.keyboard_touch_effect_palette_preference = legacyValue

            assertEquals(expectedValue, AppPreference.keyboard_touch_effect_palette_preference)
        }
    }

    @Test
    fun cinematicWavePreferencesSaveAndNormalizeValues() {
        val primary = Color.argb(0, 255, 32, 64)
        val secondary = Color.rgb(80, 120, 220)

        AppPreference.keyboard_touch_effect_type_preference = KeyboardTouchEffectType.CINEMATIC_WAVE
        AppPreference.keyboard_touch_effect_cinematic_wave_color_mode_preference = "custom"
        AppPreference.keyboard_touch_effect_cinematic_wave_primary_color_preference = primary
        AppPreference.keyboard_touch_effect_cinematic_wave_secondary_color_preference = secondary
        AppPreference.keyboard_touch_effect_cinematic_wave_secondary_color_auto_preference = false
        AppPreference.keyboard_touch_effect_cinematic_wave_type_preference = "silk_sine"
        AppPreference.keyboard_touch_effect_cinematic_wave_opacity_percent_preference = 200
        AppPreference.keyboard_touch_effect_cinematic_wave_intensity_percent_preference = 1
        AppPreference.keyboard_touch_effect_cinematic_wave_motion_preference = "dynamic"
        AppPreference.keyboard_touch_effect_cinematic_wave_touch_response_preference = "deep"
        AppPreference.keyboard_touch_effect_cinematic_wave_quality_preference = "cinematic"

        assertEquals(
            KeyboardTouchEffectType.CINEMATIC_WAVE,
            AppPreference.keyboard_touch_effect_type_preference
        )
        assertFalse(AppPreference.suminagashi_ink_effect_preference)
        assertEquals(
            CinematicWaveSettings.COLOR_MODE_CUSTOM,
            AppPreference.keyboard_touch_effect_cinematic_wave_color_mode_preference
        )
        assertEquals(
            Color.rgb(255, 32, 64),
            AppPreference.keyboard_touch_effect_cinematic_wave_primary_color_preference
        )
        assertEquals(
            secondary,
            AppPreference.keyboard_touch_effect_cinematic_wave_secondary_color_preference
        )
        assertFalse(AppPreference.keyboard_touch_effect_cinematic_wave_secondary_color_auto_preference)
        assertEquals(
            CinematicWaveSettings.WAVE_TYPE_SILK_SINE,
            AppPreference.keyboard_touch_effect_cinematic_wave_type_preference
        )
        assertEquals(68, AppPreference.keyboard_touch_effect_cinematic_wave_opacity_percent_preference)
        assertEquals(35, AppPreference.keyboard_touch_effect_cinematic_wave_intensity_percent_preference)
        assertEquals(
            CinematicWaveSettings.MOTION_DYNAMIC,
            AppPreference.keyboard_touch_effect_cinematic_wave_motion_preference
        )
        assertEquals(
            CinematicWaveSettings.TOUCH_RESPONSE_DEEP,
            AppPreference.keyboard_touch_effect_cinematic_wave_touch_response_preference
        )
        assertEquals(
            CinematicWaveSettings.QUALITY_CINEMATIC,
            AppPreference.keyboard_touch_effect_cinematic_wave_quality_preference
        )
    }

    @Test
    fun cinematicWavePreferencesNormalizeUnexpectedValuesToDefaults() {
        AppPreference.keyboard_touch_effect_cinematic_wave_color_mode_preference = "surprise"
        AppPreference.keyboard_touch_effect_cinematic_wave_type_preference = "visualizer"
        AppPreference.keyboard_touch_effect_cinematic_wave_motion_preference = "fast"
        AppPreference.keyboard_touch_effect_cinematic_wave_touch_response_preference = "massive"
        AppPreference.keyboard_touch_effect_cinematic_wave_quality_preference = "ultra"

        assertEquals(
            CinematicWaveSettings.COLOR_MODE_CINEMATIC_RANDOM,
            AppPreference.keyboard_touch_effect_cinematic_wave_color_mode_preference
        )
        assertEquals(
            CinematicWaveSettings.WAVE_TYPE_AURORA_MEMBRANE,
            AppPreference.keyboard_touch_effect_cinematic_wave_type_preference
        )
        assertEquals(
            CinematicWaveSettings.MOTION_ELEGANT,
            AppPreference.keyboard_touch_effect_cinematic_wave_motion_preference
        )
        assertEquals(
            CinematicWaveSettings.TOUCH_RESPONSE_NORMAL,
            AppPreference.keyboard_touch_effect_cinematic_wave_touch_response_preference
        )
        assertEquals(
            CinematicWaveSettings.QUALITY_BALANCED,
            AppPreference.keyboard_touch_effect_cinematic_wave_quality_preference
        )
    }
}
