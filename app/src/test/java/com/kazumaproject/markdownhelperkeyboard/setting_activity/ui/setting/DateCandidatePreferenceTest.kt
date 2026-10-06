package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.date.DateCandidateConfig
import com.kazumaproject.markdownhelperkeyboard.converter.date.DateCandidateFormat
import com.kazumaproject.markdownhelperkeyboard.ime_service.ImePreferencesSnapshot
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DateCandidatePreferenceTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun existingUsersReceiveJapaneseDateFormatsWithYearMonthDayFirst() {
        val config = ImePreferencesSnapshot.from(AppPreference).dateCandidateConfig
        assertEquals(DateCandidateConfig(), config)
        assertEquals(DateCandidateFormat.YEAR_MONTH_DAY, config.normalizedOrder.first())
        assertTrue(DateCandidateFormat.WEEKDAY_SHORT in config.enabledFormats)
        assertTrue(DateCandidateFormat.WEEKDAY_LONG in config.enabledFormats)
    }

    @Test
    fun priorityAndHiddenFormatsPersistIntoImeSnapshot() {
        val order = DateCandidateFormat.entries.reversed()
        val config = DateCandidateConfig(
            order = order,
            enabledFormats = setOf(DateCandidateFormat.WEEKDAY_LONG, DateCandidateFormat.MONTH_DAY),
        )
        AppPreference.date_candidate_config = config

        assertEquals(config, ImePreferencesSnapshot.from(AppPreference).dateCandidateConfig)
    }

    @Test
    fun disablingAllFormatsRemainsDisabledWhenPreferencesAreReadAgain() {
        AppPreference.date_candidate_config = DateCandidateConfig(enabledFormats = emptySet())
        AppPreference.init(context)

        assertTrue(AppPreference.date_candidate_config.enabledFormats.isEmpty())
    }

    @Test
    fun unknownAndRepeatedFormatIdsDoNotLoseKnownPriorityOrHideNewFormatsByDefault() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val first = DateCandidateFormat.WEEKDAY_LONG.preferenceValue
        preferences.edit().putString(AppPreference.DATE_CANDIDATE_ORDER_KEY, "$first,unknown,$first").commit()

        val config = AppPreference.date_candidate_config
        assertEquals(DateCandidateFormat.WEEKDAY_LONG, config.order.first())
        assertEquals(DateCandidateFormat.entries.size, config.order.size)
        assertEquals(DateCandidateFormat.entries.toSet(), config.enabledFormats)
    }

    @Test
    fun dateSettingsAreReachableFromCandidateSettingsAndSearch() {
        assertEquals(
            R.id.dateCandidateSettingsFragment,
            SettingDestinations.routeDestinationId("date_candidate_settings_preference"),
        )
        val result = SettingSearchIndex.searchable(context)
            .single { it.key == "date_candidate_settings_preference" }
        assertEquals(SettingCategory.CANDIDATE_CONVERSION, result.category)
        assertEquals(R.id.dateCandidateSettingsFragment, SettingDestinations.destinationId(result.destination))
    }
}
