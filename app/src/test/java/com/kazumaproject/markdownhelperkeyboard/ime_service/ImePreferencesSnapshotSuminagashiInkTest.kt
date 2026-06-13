package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.graphics.Color
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.KeyboardTouchEffectType
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
        assertFalse(snapshot.suminagashiInkEffectPreference)
        assertEquals("random", snapshot.suminagashiInkColorModePreference)
        assertEquals(Color.rgb(17, 17, 17), snapshot.suminagashiInkColorPreference)
    }

    @Test
    fun snapshotContainsSavedSuminagashiInkPreferences() {
        val fixedColor = Color.rgb(180, 48, 42)
        AppPreference.suminagashi_ink_effect_preference = true
        AppPreference.suminagashi_ink_color_mode_preference = "fixed"
        AppPreference.suminagashi_ink_color_preference = fixedColor

        val snapshot = ImePreferencesSnapshot.from(AppPreference)

        assertEquals(
            KeyboardTouchEffectType.SUMINAGASHI_INK,
            snapshot.keyboardTouchEffectTypePreference
        )
        assertTrue(snapshot.suminagashiInkEffectPreference)
        assertEquals("fixed", snapshot.suminagashiInkColorModePreference)
        assertEquals(fixedColor, snapshot.suminagashiInkColorPreference)
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
}
