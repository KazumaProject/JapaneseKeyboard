package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionAggressiveness
import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig
import com.kazumaproject.markdownhelperkeyboard.ime_service.ImePreferencesSnapshot
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PredictionPreferenceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun defaultsAreSeparatedFromNBestAndMatchTheSettingsScreen() {
        val snapshot = ImePreferencesSnapshot.from(AppPreference)

        assertEquals(PredictionConfig(), snapshot.predictionConfig)
        assertEquals(4, snapshot.nBest)
        assertEquals(4, snapshot.userDictionaryPredictionCandidateLimit)
        assertEquals(4, snapshot.learnDictionaryPredictionCandidateLimit)
        assertEquals(4, snapshot.learnPredictionPreference)
    }

    @Test
    fun predictionSettingsArePersistedInTheImeSnapshot() {
        AppPreference.japanese_prediction_enable_preference = false
        AppPreference.english_prediction_enable_preference = false
        AppPreference.system_dictionary_prediction_enable_preference = false
        AppPreference.prediction_minimum_input_length_preference = 8
        AppPreference.system_prediction_candidate_limit_preference = 16
        AppPreference.prediction_lookahead_character_count_preference = 6
        AppPreference.prediction_aggressiveness_preference = "aggressive"
        AppPreference.system_user_dictionary_prediction_enable_preference = false
        AppPreference.reading_correction_prediction_enable_preference = false
        AppPreference.proverb_prediction_enable_preference = false
        AppPreference.external_mozc_prediction_enable_preference = false
        AppPreference.symbol_emoji_prediction_enable_preference = false
        AppPreference.user_dictionary_prediction_candidate_limit_preference = 2
        AppPreference.learn_dictionary_prediction_candidate_limit_preference = 7

        val snapshot = ImePreferencesSnapshot.from(AppPreference)
        val prediction = snapshot.predictionConfig

        assertFalse(prediction.japanesePredictionEnabled)
        assertFalse(prediction.englishPredictionEnabled)
        assertFalse(prediction.systemDictionaryEnabled)
        assertEquals(8, prediction.minimumInputLength)
        assertEquals(16, prediction.systemCandidateLimit)
        assertEquals(6, prediction.lookaheadCharacterCount)
        assertEquals(PredictionAggressiveness.AGGRESSIVE, prediction.aggressiveness)
        assertFalse(prediction.systemUserDictionaryEnabled)
        assertFalse(prediction.readingCorrectionEnabled)
        assertFalse(prediction.proverbEnabled)
        assertFalse(prediction.externalMozcEnabled)
        assertFalse(prediction.symbolEmojiEnabled)
        assertEquals(2, snapshot.userDictionaryPredictionCandidateLimit)
        assertEquals(7, snapshot.learnDictionaryPredictionCandidateLimit)
    }

    @Test
    fun numericPreferencesAreClampedToSupportedRanges() {
        AppPreference.prediction_minimum_input_length_preference = 1
        AppPreference.system_prediction_candidate_limit_preference = 99
        AppPreference.prediction_lookahead_character_count_preference = 99
        AppPreference.user_dictionary_prediction_candidate_limit_preference = 0
        AppPreference.learn_dictionary_prediction_candidate_limit_preference = 99

        assertEquals(3, AppPreference.prediction_minimum_input_length_preference)
        assertEquals(16, AppPreference.system_prediction_candidate_limit_preference)
        assertEquals(6, AppPreference.prediction_lookahead_character_count_preference)
        assertEquals(1, AppPreference.user_dictionary_prediction_candidate_limit_preference)
        assertEquals(8, AppPreference.learn_dictionary_prediction_candidate_limit_preference)
    }

    @Test
    fun legacyLookaheadProfilesAreMigratedToPersistedIntegers() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val cases = mapOf("short" to 1, "standard" to 3, "long" to 6)

        cases.forEach { (legacyValue, expectedCount) ->
            preferences.edit()
                .putString("prediction_lookahead_preference", legacyValue)
                .commit()

            AppPreference.migratePredictionLookaheadPreferenceIfNeeded()

            assertEquals(expectedCount, AppPreference.prediction_lookahead_character_count_preference)
            assertEquals(expectedCount, preferences.all["prediction_lookahead_preference"])
        }
    }

    @Test
    fun settingsAreIndexedUnderTheirOwningCategories() {
        val newSettings = SettingSearchIndex.searchable(context, SettingSearchScope.NEW_HOME)

        CONVERSION_KEYS.forEach { key ->
            assertEquals(
                SettingCategory.CONVERSION_ENGINE,
                newSettings.first { it.key == key }.category,
            )
        }
        DICTIONARY_KEYS.forEach { key ->
            assertEquals(
                SettingCategory.DICTIONARY,
                newSettings.first { it.key == key }.category,
            )
        }
        assertTrue(CONVERSION_KEYS.none { it in DICTIONARY_KEYS })
    }

    @Test
    fun conversionEnginePreferenceScreenInflatesWithResolvedDependencies() {
        val screen = PreferenceManager(context).inflateFromResource(
            context,
            R.xml.pref_conversion_engine,
            null,
        )

        CONVERSION_KEYS.forEach { key ->
            assertEquals(key, screen.findPreference<androidx.preference.Preference>(key)?.key)
        }
        val lookahead = screen.findPreference<androidx.preference.SeekBarPreference>(
            "prediction_lookahead_preference",
        )
        assertEquals(1, lookahead?.min)
        assertEquals(6, lookahead?.max)
        assertTrue(lookahead?.showSeekBarValue == true)
    }

    private companion object {
        val CONVERSION_KEYS = listOf(
            "japanese_prediction_enable_preference",
            "english_prediction_enable_preference",
            "system_dictionary_prediction_enable_preference",
            "prediction_minimum_input_length_preference",
            "system_prediction_candidate_limit_preference",
            "prediction_lookahead_preference",
            "prediction_aggressiveness_preference",
            "system_user_dictionary_prediction_enable_preference",
            "reading_correction_prediction_enable_preference",
            "proverb_prediction_enable_preference",
            "external_mozc_prediction_enable_preference",
            "symbol_emoji_prediction_enable_preference",
        )
        val DICTIONARY_KEYS = listOf(
            "user_dictionary_prediction_candidate_limit_preference",
            "learn_dictionary_prediction_candidate_limit_preference",
        )
    }
}
