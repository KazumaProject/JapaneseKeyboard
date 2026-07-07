package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.graphics.Color
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.CinematicWaveSettings
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.KeyboardTouchEffectQuality
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.KeyboardTouchEffectType
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.SprayPaintSettings
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
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
class ImePreferencesSnapshotSuminagashiInkTest {

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun snapshotContainsDefaultSuminagashiInkPreferences() {
        val snapshot = ImePreferencesSnapshot.from(AppPreference)

        assertEquals(KeyboardTouchEffectType.NONE, snapshot.keyboardTouchEffectTypePreference)
        assertEquals(
            KeyboardTouchEffectQuality.HIGH,
            snapshot.keyboardTouchEffectQualityPreference
        )
        assertFalse(snapshot.suminagashiInkEffectPreference)
        assertEquals("random", snapshot.suminagashiInkColorModePreference)
        assertEquals(Color.rgb(17, 17, 17), snapshot.suminagashiInkColorPreference)
        assertEquals("random", snapshot.keyboardTouchEffectColorModePreference)
        assertEquals(Color.rgb(17, 17, 17), snapshot.keyboardTouchEffectColorPreference)
        assertEquals(
            SprayPaintSettings.PALETTE_PAINT_SPLASH,
            snapshot.keyboardTouchEffectPalettePreference
        )
        assertEquals(
            CinematicWaveSettings.COLOR_MODE_CINEMATIC_RANDOM,
            snapshot.cinematicWaveColorModePreference
        )
        assertEquals(
            CinematicWaveSettings.WAVE_TYPE_AURORA_MEMBRANE,
            snapshot.cinematicWaveTypePreference
        )
        assertEquals(
            CinematicWaveSettings.QUALITY_BALANCED,
            snapshot.cinematicWaveQualityPreference
        )
    }

    @Test
    fun snapshotContainsSavedSuminagashiInkPreferences() {
        val fixedColor = Color.rgb(180, 48, 42)
        AppPreference.suminagashi_ink_effect_preference = true
        AppPreference.keyboard_touch_effect_quality_preference = KeyboardTouchEffectQuality.EXTREME
        AppPreference.suminagashi_ink_color_mode_preference = "fixed"
        AppPreference.suminagashi_ink_color_preference = fixedColor

        val snapshot = ImePreferencesSnapshot.from(AppPreference)

        assertEquals(
            KeyboardTouchEffectType.LIQUID_INK,
            snapshot.keyboardTouchEffectTypePreference
        )
        assertTrue(snapshot.suminagashiInkEffectPreference)
        assertEquals(
            KeyboardTouchEffectQuality.EXTREME,
            snapshot.keyboardTouchEffectQualityPreference
        )
        assertEquals("fixed", snapshot.suminagashiInkColorModePreference)
        assertEquals(fixedColor, snapshot.suminagashiInkColorPreference)
        assertEquals("fixed", snapshot.keyboardTouchEffectColorModePreference)
        assertEquals(fixedColor, snapshot.keyboardTouchEffectColorPreference)
    }

    @Test
    fun snapshotContainsSavedLiquidRippleTouchEffect() {
        AppPreference.keyboard_touch_effect_type_preference = KeyboardTouchEffectType.LIQUID_RIPPLE

        val snapshot = ImePreferencesSnapshot.from(AppPreference)

        assertEquals(
            KeyboardTouchEffectType.LIQUID_RIPPLE,
            snapshot.keyboardTouchEffectTypePreference
        )
        assertFalse(snapshot.suminagashiInkEffectPreference)
    }

    @Test
    fun snapshotContainsSavedAuroraInkTouchEffect() {
        AppPreference.keyboard_touch_effect_type_preference = KeyboardTouchEffectType.AURORA_INK

        val snapshot = ImePreferencesSnapshot.from(AppPreference)

        assertEquals(
            KeyboardTouchEffectType.AURORA_INK,
            snapshot.keyboardTouchEffectTypePreference
        )
        assertFalse(snapshot.suminagashiInkEffectPreference)
    }

    @Test
    fun snapshotContainsSavedSprayPaintTouchEffectPreferences() {
        val fixedColor = Color.rgb(0, 199, 255)
        AppPreference.keyboard_touch_effect_type_preference = KeyboardTouchEffectType.SPRAY_PAINT
        AppPreference.keyboard_touch_effect_color_mode_preference = "palette"
        AppPreference.keyboard_touch_effect_color_preference = fixedColor
        AppPreference.keyboard_touch_effect_palette_preference = SprayPaintSettings.PALETTE_GRAFFITI

        val snapshot = ImePreferencesSnapshot.from(AppPreference)

        assertEquals(
            KeyboardTouchEffectType.SPRAY_PAINT,
            snapshot.keyboardTouchEffectTypePreference
        )
        assertEquals("palette", snapshot.keyboardTouchEffectColorModePreference)
        assertEquals(fixedColor, snapshot.keyboardTouchEffectColorPreference)
        assertEquals(
            SprayPaintSettings.PALETTE_GRAFFITI,
            snapshot.keyboardTouchEffectPalettePreference
        )
        assertFalse(snapshot.suminagashiInkEffectPreference)
    }

    @Test
    fun snapshotContainsSavedCinematicWavePreferences() {
        val primary = Color.rgb(70, 210, 255)
        val secondary = Color.rgb(160, 90, 255)
        AppPreference.keyboard_touch_effect_type_preference = KeyboardTouchEffectType.CINEMATIC_WAVE
        AppPreference.keyboard_touch_effect_cinematic_wave_color_mode_preference =
            CinematicWaveSettings.COLOR_MODE_CUSTOM
        AppPreference.keyboard_touch_effect_cinematic_wave_primary_color_preference = primary
        AppPreference.keyboard_touch_effect_cinematic_wave_secondary_color_preference = secondary
        AppPreference.keyboard_touch_effect_cinematic_wave_secondary_color_auto_preference = false
        AppPreference.keyboard_touch_effect_cinematic_wave_type_preference =
            CinematicWaveSettings.WAVE_TYPE_SILK_SINE
        AppPreference.keyboard_touch_effect_cinematic_wave_opacity_percent_preference = 50
        AppPreference.keyboard_touch_effect_cinematic_wave_intensity_percent_preference = 120
        AppPreference.keyboard_touch_effect_cinematic_wave_motion_preference =
            CinematicWaveSettings.MOTION_DYNAMIC
        AppPreference.keyboard_touch_effect_cinematic_wave_touch_response_preference =
            CinematicWaveSettings.TOUCH_RESPONSE_DEEP
        AppPreference.keyboard_touch_effect_cinematic_wave_quality_preference =
            CinematicWaveSettings.QUALITY_CINEMATIC

        val snapshot = ImePreferencesSnapshot.from(AppPreference)

        assertEquals(
            KeyboardTouchEffectType.CINEMATIC_WAVE,
            snapshot.keyboardTouchEffectTypePreference
        )
        assertFalse(snapshot.suminagashiInkEffectPreference)
        assertEquals(
            CinematicWaveSettings.COLOR_MODE_CUSTOM,
            snapshot.cinematicWaveColorModePreference
        )
        assertEquals(primary, snapshot.cinematicWavePrimaryColorPreference)
        assertEquals(secondary, snapshot.cinematicWaveSecondaryColorPreference)
        assertFalse(snapshot.cinematicWaveSecondaryColorAutoPreference)
        assertEquals(
            CinematicWaveSettings.WAVE_TYPE_SILK_SINE,
            snapshot.cinematicWaveTypePreference
        )
        assertEquals(50, snapshot.cinematicWaveOpacityPercentPreference)
        assertEquals(120, snapshot.cinematicWaveIntensityPercentPreference)
        assertEquals(CinematicWaveSettings.MOTION_DYNAMIC, snapshot.cinematicWaveMotionPreference)
        assertEquals(
            CinematicWaveSettings.TOUCH_RESPONSE_DEEP,
            snapshot.cinematicWaveTouchResponsePreference
        )
        assertEquals(
            CinematicWaveSettings.QUALITY_CINEMATIC,
            snapshot.cinematicWaveQualityPreference
        )
    }
}
