package com.kazumaproject.markdownhelperkeyboard.physical_keyboard

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhysicalToolbarSettingsTest {
    @Test
    fun defaultsKeepTheExistingBottomBar() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear().commit()

        assertEquals(
            PhysicalToolbarSettings(false, true, true, false),
            PhysicalToolbarSettings.read(prefs),
        )
    }

    @Test
    fun corruptEmptySelectionStillShowsModeButton() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear()
            .putString(PhysicalToolbarSettings.STYLE_KEY, "floating")
            .putBoolean(PhysicalToolbarSettings.MODE_KEY, false)
            .putBoolean(PhysicalToolbarSettings.KEYBOARD_KEY, false)
            .putBoolean(PhysicalToolbarSettings.VOICE_KEY, false)
            .commit()

        assertEquals(
            PhysicalToolbarSettings(true, true, false, false),
            PhysicalToolbarSettings.read(prefs),
        )
    }
}
