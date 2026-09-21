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
    fun legacyFactoryValuesMigrateToStandardHeights() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit()
            .putInt("candidate_view_height_dp_preference", 110)
            .putInt("candidate_view_empty_height_dp_preference", 110)
            .putInt("candidate_view_height_dp_landscape_preference", 60)
            .putInt("candidate_view_empty_height_dp_landscape_preference", 110)
            .putInt("candidate_view_height_portrait_column_1_dp_preference", 110)
            .putInt("candidate_view_height_portrait_column_2_dp_preference", 120)
            .putInt("candidate_view_height_portrait_column_3_dp_preference", 160)
            .putInt("candidate_view_height_landscape_column_1_dp_preference", 60)
            .putInt("candidate_view_height_landscape_column_2_dp_preference", 90)
            .putInt("candidate_view_height_landscape_column_3_dp_preference", 120)
            .putInt("candidate_default_height_portrait_column_1_dp_preference", 110)
            .putInt("candidate_default_height_portrait_column_2_dp_preference", 120)
            .putInt("candidate_default_height_portrait_column_3_dp_preference", 160)
            .putInt("candidate_default_height_landscape_column_1_dp_preference", 60)
            .putInt("candidate_default_height_landscape_column_2_dp_preference", 90)
            .putInt("candidate_default_height_landscape_column_3_dp_preference", 120)
            .putInt("candidate_default_empty_height_dp_preference", 110)
            .putInt("candidate_default_empty_height_dp_landscape_preference", 110)
            .putBoolean("candidate_height_per_column_migrated_preference", true)
            .remove(AppPreference.CANDIDATE_HEIGHT_DEFAULTS_MIGRATION_VERSION_KEY)
            .commit()

        AppPreference.init(context)

        assertEquals(60, AppPreference.candidate_view_height_dp)
        assertEquals(60, AppPreference.candidate_view_empty_height_dp)
        assertEquals(60, AppPreference.candidate_view_height_dp_landscape)
        assertEquals(60, AppPreference.candidate_view_empty_height_dp_landscape)
        assertEquals(
            listOf(60, 80, 100),
            listOf("1", "2", "3").map {
                AppPreference.getCandidateVisibleHeightDp(false, it)
            }
        )
        assertEquals(
            listOf(60, 80, 100),
            listOf("1", "2", "3").map {
                AppPreference.getCandidateVisibleHeightDp(true, it)
            }
        )
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
        assertEquals(
            1,
            preferences.getInt(AppPreference.CANDIDATE_HEIGHT_DEFAULTS_MIGRATION_VERSION_KEY, 0)
        )
    }

    @Test
    fun legacyGenericActiveDefaultsUseSelectedColumnStandardHeights() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit()
            .putString("candidate_column_preference", "2")
            .putString("candidate_column_landscape_preference", "3")
            .putInt("candidate_view_height_dp_preference", 110)
            .putInt("candidate_view_height_dp_landscape_preference", 60)
            .remove(AppPreference.CANDIDATE_HEIGHT_DEFAULTS_MIGRATION_VERSION_KEY)
            .remove("candidate_height_per_column_migrated_preference")
            .commit()

        AppPreference.init(context)

        assertEquals(80, AppPreference.candidate_view_height_dp)
        assertEquals(100, AppPreference.candidate_view_height_dp_landscape)
        assertEquals(80, AppPreference.getCandidateVisibleHeightDp(false, "2"))
        assertEquals(100, AppPreference.getCandidateVisibleHeightDp(true, "3"))
    }

    @Test
    fun migrationStandardizesAllSavedHeights() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit()
            .putString("candidate_column_preference", "2")
            .putString("candidate_column_landscape_preference", "3")
            .putInt("candidate_view_height_dp_preference", 215)
            .putInt("candidate_view_empty_height_dp_preference", 145)
            .putInt("candidate_view_height_dp_landscape_preference", 225)
            .putInt("candidate_view_empty_height_dp_landscape_preference", 155)
            .putInt("candidate_view_height_portrait_column_1_dp_preference", 71)
            .putInt("candidate_view_height_portrait_column_2_dp_preference", 81)
            .putInt("candidate_view_height_portrait_column_3_dp_preference", 91)
            .putInt("candidate_view_height_landscape_column_1_dp_preference", 101)
            .putInt("candidate_view_height_landscape_column_2_dp_preference", 111)
            .putInt("candidate_view_height_landscape_column_3_dp_preference", 121)
            .putInt("candidate_default_height_portrait_column_1_dp_preference", 131)
            .putInt("candidate_default_height_portrait_column_2_dp_preference", 141)
            .putInt("candidate_default_height_portrait_column_3_dp_preference", 151)
            .putInt("candidate_default_height_landscape_column_1_dp_preference", 161)
            .putInt("candidate_default_height_landscape_column_2_dp_preference", 171)
            .putInt("candidate_default_height_landscape_column_3_dp_preference", 181)
            .putInt("candidate_default_empty_height_dp_preference", 135)
            .putInt("candidate_default_empty_height_dp_landscape_preference", 145)
            .putBoolean("candidate_height_per_column_migrated_preference", true)
            .remove(AppPreference.CANDIDATE_HEIGHT_DEFAULTS_MIGRATION_VERSION_KEY)
            .commit()

        AppPreference.init(context)

        assertEquals("2", AppPreference.getCandidateColumn(isLandscape = false))
        assertEquals("3", AppPreference.getCandidateColumn(isLandscape = true))
        assertEquals(80, AppPreference.candidate_view_height_dp)
        assertEquals(60, AppPreference.candidate_view_empty_height_dp)
        assertEquals(100, AppPreference.candidate_view_height_dp_landscape)
        assertEquals(60, AppPreference.candidate_view_empty_height_dp_landscape)
        assertEquals(
            listOf(60, 80, 100),
            listOf("1", "2", "3").map {
                AppPreference.getCandidateVisibleHeightDp(false, it)
            }
        )
        assertEquals(
            listOf(60, 80, 100),
            listOf("1", "2", "3").map {
                AppPreference.getCandidateVisibleHeightDp(true, it)
            }
        )
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
        assertEquals(
            true,
            preferences.getBoolean("candidate_height_per_column_migrated_preference", false)
        )
        assertEquals(
            1,
            preferences.getInt(AppPreference.CANDIDATE_HEIGHT_DEFAULTS_MIGRATION_VERSION_KEY, 0)
        )
    }

    @Test
    fun migrationRunsOnlyOnce() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit()
            .putInt("candidate_view_height_dp_preference", 110)
            .remove(AppPreference.CANDIDATE_HEIGHT_DEFAULTS_MIGRATION_VERSION_KEY)
            .commit()

        AppPreference.init(context)
        assertEquals(60, AppPreference.candidate_view_height_dp)

        AppPreference.candidate_view_height_dp = 110
        AppPreference.migrateCandidateHeightDefaultsIfNeeded()

        assertEquals(110, AppPreference.candidate_view_height_dp)
    }

    @Test
    fun emptyHeightDefaultsUseFactoryValueForBothOrientations() {
        assertEquals(60, AppPreference.getCandidateDefaultEmptyHeightDp(isLandscape = false))
        assertEquals(60, AppPreference.getCandidateDefaultEmptyHeightDp(isLandscape = true))
    }

    @Test
    fun perColumnValuesUseStandardHeightsAfterInitialization() {
        assertEquals(60, AppPreference.getCandidateVisibleHeightDp(false, "1"))
        assertEquals(80, AppPreference.getCandidateVisibleHeightDp(false, "2"))
        assertEquals(100, AppPreference.getCandidateVisibleHeightDp(false, "3"))
        assertEquals(60, AppPreference.getCandidateVisibleHeightDp(true, "1"))
        assertEquals(80, AppPreference.getCandidateVisibleHeightDp(true, "2"))
        assertEquals(100, AppPreference.getCandidateVisibleHeightDp(true, "3"))
    }

    @Test
    fun changingColumnsUsesTheirStandardHeights() {
        AppPreference.setCandidateColumnAndSyncHeight(isLandscape = false, column = "2")
        AppPreference.setCandidateColumnAndSyncHeight(isLandscape = true, column = "3")

        assertEquals(80, AppPreference.getCandidateVisibleHeightDp(false, "2"))
        assertEquals(100, AppPreference.getCandidateVisibleHeightDp(true, "3"))
        assertEquals(80, AppPreference.candidate_view_height_dp)
        assertEquals(100, AppPreference.candidate_view_height_dp_landscape)
    }

    @Test
    fun editingSelectedColumnHeightUpdatesActiveHeight() {
        AppPreference.setCandidateColumnAndSyncHeight(isLandscape = false, column = "1")
        AppPreference.setCandidateColumnAndSyncHeight(isLandscape = true, column = "1")
        AppPreference.setCandidateVisibleHeightDp(isLandscape = false, column = "1", heightDp = 215)
        AppPreference.setCandidateVisibleHeightDp(isLandscape = true, column = "1", heightDp = 225)

        assertEquals(215, AppPreference.getCandidateVisibleHeightDp(false, "1"))
        assertEquals(225, AppPreference.getCandidateVisibleHeightDp(true, "1"))
        assertEquals(215, AppPreference.candidate_view_height_dp)
        assertEquals(225, AppPreference.candidate_view_height_dp_landscape)
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
