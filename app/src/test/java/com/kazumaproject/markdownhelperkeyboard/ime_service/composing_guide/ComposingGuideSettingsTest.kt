package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ComposingGuideSettingsTest {
    private val preferences get() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("guide-test", Context.MODE_PRIVATE)

    @Before fun clear() { preferences.edit().clear().commit() }

    @Test fun defaultOffAndIndependentOrientationPersistence() {
        val settings = ComposingGuideSettings(preferences)
        assertFalse(settings.enabled)
        val saved = ComposingGuidePlacement(.2f, .7f, 320f, 180f)
        settings.save(false, saved)
        settings.textSize = 36f
        settings.visible = false
        val reopened = ComposingGuideSettings(preferences)
        assertFalse(reopened.visible)
        assertEquals(saved, reopened.load(false))
        assertEquals(ComposingGuidePlacement(), reopened.load(true))
        assertEquals(36f, reopened.textSize)
    }

    @Test fun resetPreservesEnabledAndExistingKeyboardSettings() {
        preferences.edit().putBoolean(ComposingGuideSettings.ENABLED, true)
            .putBoolean("keyboard_floating_preference", true).commit()
        val settings = ComposingGuideSettings(preferences)
        settings.save(true, ComposingGuidePlacement(.1f, .1f, 400f, 200f))
        settings.textSize = 56f
        settings.visible = false
        ComposingGuideSettings.reset(preferences)
        assertTrue(settings.enabled)
        assertFalse(settings.visible)
        assertTrue(preferences.getBoolean("keyboard_floating_preference", false))
        assertEquals(ComposingGuidePlacement(), settings.load(true))
        assertEquals(28f, settings.textSize)
    }
}
