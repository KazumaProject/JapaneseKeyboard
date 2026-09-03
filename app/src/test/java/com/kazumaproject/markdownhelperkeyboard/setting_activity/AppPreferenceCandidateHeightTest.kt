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
class AppPreferenceCandidateHeightTest {

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun emptyHeightDefaultsUseFactoryValueForBothOrientations() {
        assertEquals(110, AppPreference.getCandidateDefaultEmptyHeightDp(isLandscape = false))
        assertEquals(110, AppPreference.getCandidateDefaultEmptyHeightDp(isLandscape = true))
    }

    @Test
    fun emptyHeightDefaultsAreClampedToCandidateHeightRange() {
        AppPreference.setCandidateDefaultEmptyHeightDp(isLandscape = false, heightDp = 10)
        AppPreference.setCandidateDefaultEmptyHeightDp(isLandscape = true, heightDp = 999)

        assertEquals(30, AppPreference.getCandidateDefaultEmptyHeightDp(isLandscape = false))
        assertEquals(300, AppPreference.getCandidateDefaultEmptyHeightDp(isLandscape = true))
    }

    @Test
    fun copyingCurrentHeightsCopiesEmptyHeightDefaultsForBothOrientations() {
        AppPreference.candidate_view_empty_height_dp = 215
        AppPreference.candidate_view_empty_height_dp_landscape = 225

        AppPreference.copyCandidateHeightSettingsToUserDefaults(isLandscape = false)
        AppPreference.copyCandidateHeightSettingsToUserDefaults(isLandscape = true)

        assertEquals(215, AppPreference.getCandidateDefaultEmptyHeightDp(isLandscape = false))
        assertEquals(225, AppPreference.getCandidateDefaultEmptyHeightDp(isLandscape = true))
    }

    @Test
    fun resettingCurrentHeightsUsesEmptyHeightDefaultsForBothOrientations() {
        AppPreference.candidate_view_empty_height_dp = 215
        AppPreference.candidate_view_empty_height_dp_landscape = 225
        AppPreference.setCandidateDefaultEmptyHeightDp(isLandscape = false, heightDp = 145)
        AppPreference.setCandidateDefaultEmptyHeightDp(isLandscape = true, heightDp = 155)

        AppPreference.resetCandidateHeightSettingsToUserDefaults(isLandscape = false)
        AppPreference.resetCandidateHeightSettingsToUserDefaults(isLandscape = true)

        assertEquals(145, AppPreference.candidate_view_empty_height_dp)
        assertEquals(155, AppPreference.candidate_view_empty_height_dp_landscape)
    }

    @Test
    fun restoringFactoryDefaultsChangesOnlyEmptyHeightDefault() {
        AppPreference.candidate_view_empty_height_dp = 205
        AppPreference.candidate_view_empty_height_dp_landscape = 215
        AppPreference.setCandidateDefaultEmptyHeightDp(isLandscape = false, heightDp = 175)
        AppPreference.setCandidateDefaultEmptyHeightDp(isLandscape = true, heightDp = 185)

        AppPreference.resetCandidateHeightDefaultsToFactoryDefaults(isLandscape = false)
        AppPreference.resetCandidateHeightDefaultsToFactoryDefaults(isLandscape = true)

        assertEquals(110, AppPreference.getCandidateDefaultEmptyHeightDp(isLandscape = false))
        assertEquals(110, AppPreference.getCandidateDefaultEmptyHeightDp(isLandscape = true))
        assertEquals(205, AppPreference.candidate_view_empty_height_dp)
        assertEquals(215, AppPreference.candidate_view_empty_height_dp_landscape)
    }
}
