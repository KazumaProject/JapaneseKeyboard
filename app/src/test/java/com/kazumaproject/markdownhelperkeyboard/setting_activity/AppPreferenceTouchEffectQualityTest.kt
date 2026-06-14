package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.KeyboardTouchEffectQuality
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPreferenceTouchEffectQualityTest {

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun touchEffectQualityUsesHighByDefault() {
        assertEquals(
            KeyboardTouchEffectQuality.HIGH,
            AppPreference.keyboard_touch_effect_quality_preference
        )
    }

    @Test
    fun touchEffectQualitySavesKnownValues() {
        listOf(
            KeyboardTouchEffectQuality.BALANCED,
            KeyboardTouchEffectQuality.HIGH,
            KeyboardTouchEffectQuality.ULTRA,
            KeyboardTouchEffectQuality.EXTREME
        ).forEach { quality ->
            AppPreference.keyboard_touch_effect_quality_preference = quality

            assertEquals(quality, AppPreference.keyboard_touch_effect_quality_preference)
        }
    }

    @Test
    fun touchEffectQualityNormalizesInvalidValuesToHigh() {
        AppPreference.keyboard_touch_effect_quality_preference = "unexpected"

        assertEquals(
            KeyboardTouchEffectQuality.HIGH,
            AppPreference.keyboard_touch_effect_quality_preference
        )
    }
}
