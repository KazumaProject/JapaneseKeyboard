package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyboardSkinPreferenceTest {
    private lateinit var preferences: SharedPreferences

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit().clear().commit()
        AppPreference.init(context)
    }

    @Test fun changingSkinsDoesNotRewriteAnyLegacySetting() {
        AppPreference.theme_mode = "custom"
        AppPreference.custom_theme_bg_color = 0xff123456.toInt()
        AppPreference.custom_theme_key_color = 0xff654321.toInt()
        val before = preferences.all.toMap()
        KeyboardSkinId.entries.forEach { skin ->
            AppPreference.keyboardSkin = skin
            assertEquals(skin, AppPreference.keyboardSkin)
            assertEquals(before, preferences.all.filterKeys { it != KeyboardSkinId.PREFERENCE_KEY })
        }
    }

    @Test fun unavailableSkinFallsBackWithoutDestroyingSavedValue() {
        preferences.edit().putString(KeyboardSkinId.PREFERENCE_KEY, "future_skin").commit()
        assertEquals(KeyboardSkinId.DEFAULT, AppPreference.keyboardSkin)
        assertEquals("future_skin", preferences.getString(KeyboardSkinId.PREFERENCE_KEY, null))
    }

    @Test fun legacyInstallKeepsItsAppearance() {
        AppPreference.theme_mode = "custom"
        assertEquals(KeyboardSkinId.DEFAULT, AppPreference.keyboardSkin)
        assertEquals("custom", AppPreference.theme_mode)
    }
}
