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
    fun freshInstallUsesStandardCandidateHeightDefaults() {
        assertEquals(60, AppPreference.candidate_view_height_dp)
        assertEquals(60, AppPreference.candidate_view_empty_height_dp)
        assertEquals(60, AppPreference.candidate_view_height_dp_landscape)
        assertEquals(60, AppPreference.candidate_view_empty_height_dp_landscape)

        assertEquals(
            listOf(60, 80, 100),
            listOf("1", "2", "3").map {
                AppPreference.getCandidateDefaultVisibleHeightDp(isLandscape = false, column = it)
            }
        )
        assertEquals(
            listOf(60, 80, 100),
            listOf("1", "2", "3").map {
                AppPreference.getCandidateDefaultVisibleHeightDp(isLandscape = true, column = it)
            }
        )
    }

    @Test
    fun emptyHeightDefaultsUseFactoryValueForBothOrientations() {
        assertEquals(60, AppPreference.getCandidateDefaultEmptyHeightDp(isLandscape = false))
        assertEquals(60, AppPreference.getCandidateDefaultEmptyHeightDp(isLandscape = true))
    }

    @Test
    fun firstColumnMigrationUsesStandardActiveHeights() {
        assertEquals(60, AppPreference.getCandidateVisibleHeightDp(false, "1"))
        assertEquals(80, AppPreference.getCandidateVisibleHeightDp(false, "2"))
        assertEquals(100, AppPreference.getCandidateVisibleHeightDp(false, "3"))
        assertEquals(60, AppPreference.getCandidateVisibleHeightDp(true, "1"))
        assertEquals(80, AppPreference.getCandidateVisibleHeightDp(true, "2"))
        assertEquals(100, AppPreference.getCandidateVisibleHeightDp(true, "3"))
    }

    @Test
    fun firstColumnMigrationPreservesSavedActiveHeights() {
        AppPreference.candidate_view_height_dp = 215
        AppPreference.candidate_view_height_dp_landscape = 225

        assertEquals(215, AppPreference.getCandidateVisibleHeightDp(false, "1"))
        assertEquals(225, AppPreference.getCandidateVisibleHeightDp(true, "1"))
        assertEquals(80, AppPreference.getCandidateVisibleHeightDp(false, "2"))
        assertEquals(80, AppPreference.getCandidateVisibleHeightDp(true, "2"))
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

        assertEquals(60, AppPreference.getCandidateDefaultEmptyHeightDp(isLandscape = false))
        assertEquals(60, AppPreference.getCandidateDefaultEmptyHeightDp(isLandscape = true))
        assertEquals(205, AppPreference.candidate_view_empty_height_dp)
        assertEquals(215, AppPreference.candidate_view_empty_height_dp_landscape)
    }

    @Test
    fun restoringFactoryDefaultsUsesStandardPerColumnHeights() {
        listOf("1", "2", "3").forEach { column ->
            AppPreference.setCandidateDefaultVisibleHeightDp(false, column, 200)
            AppPreference.setCandidateDefaultVisibleHeightDp(true, column, 200)
        }
        AppPreference.setCandidateDefaultEmptyHeightDp(false, 200)
        AppPreference.setCandidateDefaultEmptyHeightDp(true, 200)

        AppPreference.resetCandidateHeightDefaultsToFactoryDefaults(isLandscape = false)
        AppPreference.resetCandidateHeightDefaultsToFactoryDefaults(isLandscape = true)

        assertEquals(
            listOf(60, 80, 100),
            listOf("1", "2", "3").map {
                AppPreference.getCandidateDefaultVisibleHeightDp(false, it)
            }
        )
        assertEquals(
            listOf(60, 80, 100),
            listOf("1", "2", "3").map {
                AppPreference.getCandidateDefaultVisibleHeightDp(true, it)
            }
        )
        assertEquals(60, AppPreference.getCandidateDefaultEmptyHeightDp(false))
        assertEquals(60, AppPreference.getCandidateDefaultEmptyHeightDp(true))
    }
}
