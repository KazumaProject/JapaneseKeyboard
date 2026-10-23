package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OrientationKeyboardSettingsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences get() = PreferenceManager.getDefaultSharedPreferences(context)

    @Before fun setup() {
        preferences.edit().clear().commit()
        AppPreference.init(context)
    }

    @Test fun defaultsInheritAndIgnoreObsoleteQwertyFlags() {
        preferences.edit().putBoolean("landscape_force_qwerty_preference", true)
            .putBoolean("landscape_force_qwerty_romaji_preference", true).commit()
        for (landscape in listOf(false, true)) {
            val settings = AppPreference.orientationSettings(landscape)
            assertNull(settings.keyboardType(KeyboardType.entries))
            assertEquals("shared-image", settings.image("shared-image"))
            assertEquals("shared-video", settings.video("shared-video"))
        }
    }

    @Test fun orientationsHaveIndependentKeyboardsAndBackgrounds() {
        preferences.edit()
            .putString(OrientationKeyboardSettings.key(false, "keyboard"), "SUMIRE")
            .putString(OrientationKeyboardSettings.key(true, "keyboard"), "QWERTY").commit()
        AppPreference.setOrientationBackground(false, "image", "content://portrait")
        AppPreference.setOrientationBackground(true, "video", "content://landscape")
        val portrait = AppPreference.orientationSettings(false)
        val landscape = AppPreference.orientationSettings(true)
        assertEquals(KeyboardType.SUMIRE, portrait.keyboardType(KeyboardType.entries))
        assertEquals(KeyboardType.QWERTY, landscape.keyboardType(KeyboardType.entries))
        assertEquals("content://portrait", portrait.image("shared"))
        assertEquals("", portrait.video("shared"))
        assertEquals("", landscape.image("shared"))
        assertEquals("content://landscape", landscape.video("shared"))
    }

    @Test fun missingOrInvalidKeyboardFallsBackToNormalSelection() {
        assertNull(OrientationKeyboardSettings(keyboard = "SUMIRE").keyboardType(listOf(KeyboardType.QWERTY)))
        assertNull(OrientationKeyboardSettings(keyboard = "unknown").keyboardType(KeyboardType.entries))
    }

    @Test fun noneAndCommonCanBeSelectedWithoutLosingStoredMedia() {
        AppPreference.setOrientationBackground(true, "image", "content://shared")
        AppPreference.setOrientationBackground(true, "none")
        val none = AppPreference.orientationSettings(true)
        assertEquals("", none.image("image"))
        assertEquals("", none.video("video"))
        assertTrue(AppPreference.isBackgroundUriUsedByOrientation("content://shared"))
        AppPreference.setOrientationBackground(true, "common")
        assertEquals("image", AppPreference.orientationSettings(true).image("image"))
        assertEquals("video", AppPreference.orientationSettings(true).video("video"))
    }

    @Test fun backupRoundTripPreservesOrientationSettings() {
        AppPreference.setOrientationBackground(false, "image", "content://portrait")
        AppPreference.setOrientationBackground(true, "none")
        val json = AppPreference.exportAllToJson()
        preferences.edit().clear().commit()
        AppPreference.importAllFromJson(json)
        assertEquals("content://portrait", AppPreference.orientationSettings(false).image("shared"))
        assertEquals("", AppPreference.orientationSettings(true).video("shared"))
    }
}
