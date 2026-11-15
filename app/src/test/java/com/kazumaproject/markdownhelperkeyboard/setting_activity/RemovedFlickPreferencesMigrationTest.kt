package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RemovedFlickPreferencesMigrationTest {
    @Test fun oldPreferencesUseNormalFlickAndRemoveUnusedSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putString("sumire_keyboard_style_preference", "third-flick")
            .putString("sumire_input_method_preference", "toggle")
            .putString("sumire_flick_input_mode_preference", "voiced")
            .putInt("hierarchical_flick_mode_switch_angle_margin_preference", 23).commit()
        AppPreference.init(context)
        assertEquals("default", AppPreference.sumire_keyboard_style)
        assertEquals("flick", AppPreference.sumire_input_method)
        assertFalse(prefs.contains("sumire_flick_input_mode_preference"))
        assertFalse(prefs.contains("hierarchical_flick_mode_switch_angle_margin_preference"))
        AppPreference.sumire_keyboard_style = "free-popup"
        AppPreference.init(context)
        assertEquals("free-popup", AppPreference.sumire_keyboard_style)
    }
}
