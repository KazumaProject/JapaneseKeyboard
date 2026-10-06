package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPreferenceFlickPreviewTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun existingPreferencesWithoutDelayKeepImmediatePreview() {
        AppPreference.flick_editor_preview_preference = true
        assertEquals(0, AppPreference.flick_editor_preview_delay_ms)
    }

    @Test
    fun delayPersistsAndRestoredValuesAreBounded() {
        AppPreference.flick_editor_preview_delay_ms = 80
        AppPreference.init(context)
        assertEquals(80, AppPreference.flick_editor_preview_delay_ms)
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit().putInt("flick_editor_preview_delay_ms", -1).commit()
        assertEquals(0, AppPreference.flick_editor_preview_delay_ms)
        preferences.edit().putInt("flick_editor_preview_delay_ms", 1000).commit()
        assertEquals(500, AppPreference.flick_editor_preview_delay_ms)
    }
}
