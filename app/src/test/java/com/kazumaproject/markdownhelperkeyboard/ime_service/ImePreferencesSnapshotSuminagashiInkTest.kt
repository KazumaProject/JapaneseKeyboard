package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.graphics.Color
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
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
            KeyboardTouchEffectType.SUMINAGASHI_INK,
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
}
