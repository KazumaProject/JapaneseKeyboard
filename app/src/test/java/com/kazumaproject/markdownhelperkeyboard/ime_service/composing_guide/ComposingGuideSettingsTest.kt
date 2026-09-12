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

    @Test fun newInstallDefaultsOffAndCombined() {
        val settings = ComposingGuideSettings(preferences)
        assertFalse(settings.enabled)
        assertFalse(settings.textEnabled)
        assertFalse(settings.showReading)
        assertTrue(settings.combined)
        assertTrue(settings.verticalCandidates)
        assertTrue(settings.profiles.isEmpty())
    }

    @Test fun migrationPreservesOldDisplayAndDoesNotRunAgain() {
        for (enabled in listOf(false, true)) for (text in listOf(false, true)) {
            preferences.edit().clear().putBoolean(ComposingGuideSettings.ENABLED, enabled)
                .putBoolean(ComposingGuideSettings.SHOW_COMPOSING, text)
                .putBoolean("composing_guide_visible", false).commit()
            val settings = ComposingGuideSettings(preferences)
            assertEquals(enabled, settings.enabled)
            assertEquals(enabled && text, settings.textEnabled)
            assertTrue(settings.combined)
            preferences.edit().putBoolean(ComposingGuideSettings.TEXT_ENABLED, !text).commit()
            assertEquals(!text, ComposingGuideSettings(preferences).textEnabled)
        }
    }

    @Test fun allConfigurationsResolveAndModeSurvivesToggling() {
        val settings = ComposingGuideSettings(preferences)
        for (combined in listOf(false, true)) for (candidates in listOf(false, true)) for (text in listOf(false, true)) {
            preferences.edit().putBoolean(ComposingGuideSettings.ENABLED, candidates)
                .putBoolean(ComposingGuideSettings.TEXT_ENABLED, text)
                .putString(ComposingGuideSettings.DISPLAY_MODE, if (combined) "integrated" else "separate").commit()
            val expected = if (text && candidates && combined) listOf(GuideProfile.INTEGRATED) else buildList {
                if (candidates) add(GuideProfile.CANDIDATES)
                if (text) add(GuideProfile.TEXT)
            }
            assertEquals(expected, settings.profiles)
            assertEquals(combined, settings.combined)
        }
    }

    @Test fun migrationCopiesCandidateGeometryWithoutChangingIntegratedGeometry() {
        preferences.edit().putFloat("composing_guide_portrait_width", 190f)
            .putFloat("composing_guide_portrait_x", .3f)
            .putBoolean("composing_guide_portrait_screen_coordinates", true).commit()
        val settings = ComposingGuideSettings(preferences)
        assertEquals(settings.load(false), settings.load(false, GuideProfile.CANDIDATES))
        assertTrue(settings.usesScreenCoordinates(false, GuideProfile.CANDIDATES))
        assertFalse(settings.usesScreenCoordinates(false, GuideProfile.TEXT))
    }

    @Test fun firstCandidatePlacementUsesLatestIntegratedPositionAndResetDoesNotReseedIt() {
        val settings = ComposingGuideSettings(preferences)
        val saved = ComposingGuidePlacement(.7f, .2f, 200f, 180f)
        settings.save(false, saved)
        settings.prepareCandidatePlacement(false)
        assertEquals(saved, settings.load(false, GuideProfile.CANDIDATES))
        settings.save(false, saved.copy(xFraction = .1f))
        settings.prepareCandidatePlacement(false)
        assertEquals(saved, settings.load(false, GuideProfile.CANDIDATES))
        ComposingGuideSettings.reset(preferences, GuideProfile.CANDIDATES)
        settings.prepareCandidatePlacement(false)
        assertEquals(ComposingGuidePlacement(), settings.load(false, GuideProfile.CANDIDATES))
    }

    @Test fun profilesAndOrientationsPersistIndependentlyAndResetOnlyGeometry() {
        val settings = ComposingGuideSettings(preferences)
        val expected = mutableMapOf<Pair<GuideProfile, Boolean>, ComposingGuidePlacement>()
        for (profile in GuideProfile.entries) for (landscape in listOf(false, true)) {
            val saved = ComposingGuidePlacement(.1f * (profile.ordinal + 1), if (landscape) .2f else .7f,
                180f + profile.ordinal * 20, if (landscape) 150f else 220f)
            settings.save(landscape, saved, profile)
            expected[profile to landscape] = saved
        }
        settings.textSize = 56f
        preferences.edit().putBoolean(ComposingGuideSettings.SHOW_READING, true)
            .putString(ComposingGuideSettings.SCROLL_DIRECTION, "horizontal").commit()
        ComposingGuideSettings.reset(preferences, GuideProfile.TEXT)
        val reopened = ComposingGuideSettings(preferences)
        for ((key, saved) in expected) {
            assertEquals(if (key.first == GuideProfile.TEXT) ComposingGuidePlacement() else saved, reopened.load(key.second, key.first))
        }
        assertEquals(56f, reopened.textSize)
        assertTrue(reopened.showReading)
        assertFalse(reopened.verticalCandidates)
    }

    @Test fun textInitialPlacementTriesAvailableSidesAndClampsFallback() {
        val area = GuideBounds(0, 0, 500, 800)
        assertEquals(GuideBounds(100, 192, 160, 100), placeTextGuide(GuideBounds(100, 300, 160, 200), area, 100, 8))
        assertEquals(GuideBounds(100, 258, 160, 100), placeTextGuide(GuideBounds(100, 50, 160, 200), area, 100, 8))
        assertEquals(GuideBounds(32, 0, 160, 100), placeTextGuide(GuideBounds(200, 0, 160, 800), area, 100, 8))
        assertEquals(GuideBounds(168, 0, 160, 100), placeTextGuide(GuideBounds(0, 0, 160, 800), area, 100, 8))
        assertEquals(GuideBounds(0, 0, 160, 100), placeTextGuide(GuideBounds(0, 0, 160, 200), GuideBounds(0, 0, 160, 200), 100, 8))
    }
}
