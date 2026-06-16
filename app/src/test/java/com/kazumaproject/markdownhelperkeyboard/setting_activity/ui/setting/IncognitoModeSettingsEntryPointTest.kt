package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.ime_service.ImePreferencesSnapshot
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IncognitoModeSettingsEntryPointTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun incognitoSettingsDefaultToEnabled() {
        assertTrue(AppPreference.incognito_mode_detection_preference)
        assertTrue(AppPreference.show_learned_candidates_in_incognito_preference)

        val snapshot = ImePreferencesSnapshot.from(AppPreference)

        assertTrue(snapshot.incognitoModeDetectionPreference)
        assertTrue(snapshot.showLearnedCandidatesInIncognitoPreference)
    }

    @Test
    fun incognitoSettingsUseDefaultSharedPreferencesKeys() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)

        preferences.edit()
            .putBoolean("incognito_mode_detection_preference", false)
            .putBoolean("show_learned_candidates_in_incognito_preference", false)
            .commit()

        assertFalse(AppPreference.incognito_mode_detection_preference)
        assertFalse(AppPreference.show_learned_candidates_in_incognito_preference)

        AppPreference.incognito_mode_detection_preference = true
        AppPreference.show_learned_candidates_in_incognito_preference = true

        assertTrue(preferences.getBoolean("incognito_mode_detection_preference", false))
        assertTrue(
            preferences.getBoolean("show_learned_candidates_in_incognito_preference", false)
        )
    }

    @Test
    fun incognitoSettingsAreSearchableInNewAndLegacySettings() {
        val newHomeKeys = SettingSearchIndex.searchable(context, SettingSearchScope.NEW_HOME)
            .map { it.key }
            .toSet()
        val legacyKeys = SettingSearchIndex.searchable(context, SettingSearchScope.LEGACY_TABS)
            .map { it.key }
            .toSet()

        requiredIncognitoSettingKeys.forEach { key ->
            assertTrue("Missing $key from new settings search", key in newHomeKeys)
            assertTrue("Missing $key from legacy settings search", key in legacyKeys)
        }
    }

    @Test
    fun incognitoSettingsCanBeAddedFromFrequentSettingsScreen() {
        val candidateKeys = SettingDestinations.frequentCandidates(context)
            .map { it.key }
            .toSet()

        requiredIncognitoSettingKeys.forEach { key ->
            assertTrue("Missing $key from frequent setting candidates", key in candidateKeys)
        }
    }

    private companion object {
        val requiredIncognitoSettingKeys = setOf(
            "incognito_mode_detection_preference",
            "show_learned_candidates_in_incognito_preference",
        )
    }
}
