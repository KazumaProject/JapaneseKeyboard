package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.number.NumberCandidateConfig
import com.kazumaproject.markdownhelperkeyboard.converter.number.NumberCandidateFormat
import com.kazumaproject.markdownhelperkeyboard.ime_service.ImePreferencesSnapshot
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NumberCandidatePreferenceTest {
    private lateinit var context: Context
    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }
    @Test fun defaultsAreEnabledForExistingUsersAndReachTheIme() {
        assertEquals(NumberCandidateConfig(), ImePreferencesSnapshot.from(AppPreference).numberCandidateConfig)
    }
    @Test fun toggleAndOrderSurviveReloadAndSnapshot() {
        val config = NumberCandidateConfig(false, NumberCandidateFormat.entries.reversed())
        AppPreference.number_candidate_config = config
        AppPreference.init(context)
        assertEquals(config, AppPreference.number_candidate_config)
        assertEquals(config, ImePreferencesSnapshot.from(AppPreference).numberCandidateConfig)
    }
    @Test fun incompleteUnknownAndDuplicateOrderValuesKeepKnownPriority() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(AppPreference.NUMBER_CANDIDATE_ORDER_KEY, "kanji,unknown,kanji").commit()
        assertEquals(listOf(NumberCandidateFormat.KANJI, NumberCandidateFormat.HALF_WIDTH, NumberCandidateFormat.FULL_WIDTH),
            AppPreference.number_candidate_config.order)
    }
    @Test fun bothControlsAreSearchableUnderConversionEngine() {
        assertEquals(R.id.numberCandidateSettingsFragment, SettingDestinations.routeDestinationId("number_candidate_order_preference"))
        val results = SettingSearchIndex.searchable(context)
        listOf(AppPreference.NUMBER_COUNTER_CANDIDATES_ENABLED_KEY, "number_candidate_order_preference").forEach { key ->
            assertEquals(SettingCategory.CONVERSION_ENGINE, results.single { it.key == key }.category)
        }
        val result = results.single { it.key == "number_candidate_order_preference" }
        assertEquals(R.id.numberCandidateSettingsFragment, SettingDestinations.destinationId(result.destination))
    }
}
