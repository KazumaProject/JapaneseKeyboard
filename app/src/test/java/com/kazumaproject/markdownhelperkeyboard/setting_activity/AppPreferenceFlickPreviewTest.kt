package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.ime_service.ImePreferencesSnapshot
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

    @Test
    fun savedDelaysRoundToTheNearestFiveMillisecondsWithinBounds() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val cases = mapOf(
            Int.MIN_VALUE to 0, -1 to 0, 0 to 0, 2 to 0, 3 to 5,
            5 to 5, 7 to 5, 8 to 10, 497 to 495, 498 to 500,
            500 to 500, Int.MAX_VALUE to 500,
        )
        cases.forEach { (raw, expected) ->
            AppPreference.flick_editor_preview_delay_ms = raw
            assertEquals("saved value for $raw", expected,
                preferences.getInt(FlickPreviewDelaySettings.KEY, -1))
            AppPreference.init(context)
            assertEquals("reloaded value for $raw", expected, AppPreference.flick_editor_preview_delay_ms)
        }
    }

    @Test
    fun restoredNonStepDelaysAreNormalizedInTheImeSnapshot() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        mapOf(7 to 5, 8 to 10, 498 to 500).forEach { (raw, expected) ->
            preferences.edit().putInt(FlickPreviewDelaySettings.KEY, raw).commit()
            assertEquals(expected, ImePreferencesSnapshot.from(AppPreference).flickEditorPreviewDelayMillis)
        }
    }
}
