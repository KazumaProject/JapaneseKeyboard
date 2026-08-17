package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.data.keyboard.KeyboardSkinId
import com.kazumaproject.core.data.keyboard.KeyboardSkinMotionMode
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPreferenceKeyboardSkinTest {

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun keyboardSkinDefaultsToExistingAppearanceWithFullMotion() {
        assertEquals(KeyboardSkinId.DEFAULT.preferenceValue, AppPreference.keyboard_skin)
        assertEquals(
            KeyboardSkinMotionMode.FULL.preferenceValue,
            AppPreference.keyboard_skin_motion,
        )
    }

    @Test
    fun cupertinoDarkAndMotionModePersistIndependentlyFromThemeMode() {
        AppPreference.theme_mode = "custom"
        AppPreference.keyboard_skin = KeyboardSkinId.CUPERTINO_DARK.preferenceValue
        AppPreference.keyboard_skin_motion = KeyboardSkinMotionMode.REDUCED.preferenceValue

        assertEquals("custom", AppPreference.theme_mode)
        assertEquals(KeyboardSkinId.CUPERTINO_DARK.preferenceValue, AppPreference.keyboard_skin)
        assertEquals(
            KeyboardSkinMotionMode.REDUCED.preferenceValue,
            AppPreference.keyboard_skin_motion,
        )
    }
}
