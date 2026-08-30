package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.utility.AngleMode
import com.kazumaproject.markdownhelperkeyboard.converter.utility.Precision
import com.kazumaproject.markdownhelperkeyboard.converter.utility.RegionalUnitProfile
import com.kazumaproject.markdownhelperkeyboard.converter.utility.UtilityCandidateConfig
import com.kazumaproject.markdownhelperkeyboard.ime_service.ImePreferencesSnapshot
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UtilityCandidatePreferenceTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun defaultsAreEnabledForExistingUsersAndIncludedInImeSnapshot() {
        val config = ImePreferencesSnapshot.from(AppPreference).utilityCandidateConfig

        assertTrue(config.calculationEnabled)
        assertTrue(config.unitConversionEnabled)
        assertTrue(config.includeExpressionCandidate)
        assertEquals(AngleMode.DEGREES, config.angleMode)
        assertEquals(Precision.Auto, config.calculationPrecision)
        assertEquals(RegionalUnitProfile.JAPAN, config.regionalUnitProfile)
        assertTrue(config.unitTargets.values.all { it.size <= 4 })
        assertEquals(8, UtilityCandidateConfig.MAX_TARGETS_PER_CATEGORY)
        assertEquals(
            "You can select up to 8 targets.",
            context.getString(
                R.string.utility_target_limit,
                UtilityCandidateConfig.MAX_TARGETS_PER_CATEGORY,
            ),
        )
    }

    @Test
    fun persistedConfigIsReadIntoImeSnapshot() {
        val updated = AppPreference.utility_candidate_config.copy(
            calculationEnabled = false,
            includeExpressionCandidate = false,
            angleMode = AngleMode.RADIANS,
            calculationPrecision = Precision.SignificantDigits(9),
            regionalUnitProfile = RegionalUnitProfile.UNITED_KINGDOM,
        )
        AppPreference.utility_candidate_config = updated

        assertEquals(updated, ImePreferencesSnapshot.from(AppPreference).utilityCandidateConfig)
    }

    @Test
    fun screenInflatesAndSearchTargetsItsHighlightableDestination() {
        val screen = PreferenceManager(context).inflateFromResource(
            context,
            R.xml.pref_utility_candidate,
            null,
        )
        assertTrue(screen.findPreference<androidx.preference.Preference>(CALCULATION_KEY) != null)
        TARGET_KEYS.forEach { key ->
            assertTrue(key, screen.findPreference<androidx.preference.Preference>(key) != null)
        }

        val result = SettingSearchIndex.searchable(context, SettingSearchScope.NEW_HOME)
            .first { it.key == CALCULATION_KEY }
        assertEquals(SettingCategory.CONVERSION_ENGINE, result.category)
        assertEquals(
            R.id.utilityCandidatePreferenceFragment,
            SettingDestinations.destinationId(result.destination),
        )
        assertEquals(
            CALCULATION_KEY,
            SettingDestinations.highlightPreferenceKey(result.destination),
        )
    }

    @Test
    fun bothSettingsExperiencesExposeTheSharedConversionEngineRoute() {
        val newRoute = SettingSearchIndex.searchable(context, SettingSearchScope.NEW_HOME)
            .first { it.key == ROUTE_KEY }
        val legacyTab = SettingTabRegistry.createTabs()
            .first { it.key == SettingTabRegistry.TAB_CONVERSION_ENGINE }

        assertEquals(
            R.id.utilityCandidatePreferenceFragment,
            SettingDestinations.destinationId(newRoute.destination),
        )
        assertEquals(R.xml.pref_conversion_engine, legacyTab.xmlRes)
        assertTrue(conversionEngineXmlContainsRoute())
    }

    private fun conversionEngineXmlContainsRoute(): Boolean {
        val parser = context.resources.getXml(R.xml.pref_conversion_engine)
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        return parser.use {
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (
                    parser.eventType == XmlPullParser.START_TAG &&
                    parser.getAttributeValue(androidNamespace, "key") == ROUTE_KEY
                ) return@use true
                parser.next()
            }
            false
        }
    }

    private companion object {
        const val CALCULATION_KEY = "utility_calculation_enabled"
        const val ROUTE_KEY = "setting_route_utility_candidates"
        val TARGET_KEYS = listOf(
            "utility_targets_length",
            "utility_targets_area",
            "utility_targets_volume",
            "utility_targets_mass",
            "utility_targets_temperature",
            "utility_targets_speed",
            "utility_targets_pressure",
            "utility_targets_energy",
            "utility_targets_power",
            "utility_targets_time",
            "utility_targets_data",
        )
    }
}
