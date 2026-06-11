package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.graphics.Color
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
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
        assertEquals("random", AppPreference.suminagashi_ink_color_mode_preference)
        assertEquals(Color.rgb(17, 17, 17), AppPreference.suminagashi_ink_color_preference)
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
}
