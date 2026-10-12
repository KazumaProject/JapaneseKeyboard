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
class ZenzFloatingPlacementTest {
    private val preferences get() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("zenz-placement-test", Context.MODE_PRIVATE)
    @Before fun clear() { preferences.edit().clear().commit() }

    @Test fun defaultPlacementIsIndependentOfCandidateGuide() {
        val settings = ComposingGuideSettings(preferences)
        settings.save(false, ComposingGuidePlacement(.1f, .2f, 400f, 300f), GuideProfile.CANDIDATES)
        assertEquals(ComposingGuidePlacement(.5f, 1f, 280f, 160f), settings.load(false, GuideProfile.ZENZ))
        assertFalse(settings.hasPlacement(false, GuideProfile.ZENZ))
        assertTrue(settings.usesScreenCoordinates(false, GuideProfile.ZENZ))
        assertFalse(GuideProfile.ZENZ in settings.profiles)
    }
    @Test fun orientationSpecificGeometrySurvivesReloadAndResetPreservesOtherPanels() {
        val settings = ComposingGuideSettings(preferences)
        val portrait = ComposingGuidePlacement(.2f, .3f, 320f, 180f)
        val landscape = ComposingGuidePlacement(.7f, .8f, 400f, 140f)
        settings.save(false, portrait, GuideProfile.ZENZ)
        settings.save(true, landscape, GuideProfile.ZENZ)
        settings.save(false, portrait, GuideProfile.CANDIDATES)
        val reloaded = ComposingGuideSettings(preferences)
        assertTrue(reloaded.hasPlacement(false, GuideProfile.ZENZ))
        assertEquals(portrait, reloaded.load(false, GuideProfile.ZENZ))
        assertEquals(landscape, reloaded.load(true, GuideProfile.ZENZ))
        ComposingGuideSettings.reset(preferences, GuideProfile.ZENZ)
        assertFalse(reloaded.hasPlacement(false, GuideProfile.ZENZ))
        assertEquals(160f, reloaded.load(false, GuideProfile.ZENZ).heightDp, .001f)
        assertEquals(160f, reloaded.load(true, GuideProfile.ZENZ).heightDp, .001f)
        assertEquals(portrait, reloaded.load(false, GuideProfile.CANDIDATES))
    }
    @Test fun initialZenzPanelKeepsItsOwnWidthWhenAvoidingAWiderCandidatePanel() {
        val area = GuideBounds(0, 0, 600, 800)
        val candidatePanel = GuideBounds(100, 400, 400, 250)
        assertEquals(GuideBounds(100, 212, 280, 180),
            placeTextGuide(candidatePanel, area, 180, 8, requestedWidth = 280))
    }
    @Test fun savedPanelIsClampedAfterScreenShrinks() {
        val bounds = ComposingGuidePlacement(.9f, 1f, 600f, 400f).resolve(12, 24, 200, 180, 1f)
        assertEquals(GuideBounds(12, 24, 200, 180), bounds)
    }
}
