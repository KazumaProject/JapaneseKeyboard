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
        assertFalse(settings.showReading)
        assertFalse(settings.usesScreenCoordinates(false))
        assertTrue(settings.showComposing)
        assertTrue(settings.verticalCandidates)
        val saved = ComposingGuidePlacement(.2f, .7f, 320f, 180f)
        settings.save(false, saved)
        settings.textSize = 36f
        settings.visible = false
        val reopened = ComposingGuideSettings(preferences)
        assertFalse(reopened.visible)
        assertEquals(saved, reopened.load(false))
        assertTrue(reopened.usesScreenCoordinates(false))
        assertFalse(reopened.usesScreenCoordinates(true))
        assertEquals(ComposingGuidePlacement(), reopened.load(true))
        assertEquals(36f, reopened.textSize)
    }

    @Test fun explicitHorizontalPreferenceIsPreserved() {
        preferences.edit().putString(ComposingGuideSettings.SCROLL_DIRECTION, "horizontal").commit()
        assertFalse(ComposingGuideSettings(preferences).verticalCandidates)
    }

    @Test fun candidateOptionsPersistAndGeometryResetPreservesThem() {
        preferences.edit().putBoolean(ComposingGuideSettings.SHOW_COMPOSING, false)
            .putBoolean(ComposingGuideSettings.SHOW_READING, true)
            .putString(ComposingGuideSettings.SCROLL_DIRECTION, "vertical").commit()
        val reopened = ComposingGuideSettings(preferences)
        assertFalse(reopened.showComposing)
        assertTrue(reopened.showReading)
        assertTrue(reopened.verticalCandidates)
        ComposingGuideSettings.reset(preferences)
        assertFalse(reopened.showComposing)
        assertTrue(reopened.showReading)
        assertTrue(reopened.verticalCandidates)
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
        assertFalse(settings.usesScreenCoordinates(true))
    }
}
