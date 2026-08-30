package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import android.content.res.Configuration
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.utility.AngleMode
import com.kazumaproject.markdownhelperkeyboard.converter.utility.Precision
import com.kazumaproject.markdownhelperkeyboard.converter.utility.RegionalUnitProfile
import com.kazumaproject.markdownhelperkeyboard.converter.utility.UnitCategory
import com.kazumaproject.markdownhelperkeyboard.converter.utility.UnitId
import com.kazumaproject.markdownhelperkeyboard.converter.utility.UnitTargetSetting
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
import java.util.Locale

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
    fun integerPrecisionPersistsForCalculationAndUnitTargets() {
        val defaults = AppPreference.utility_candidate_config
        val updated = defaults.copy(
            calculationPrecision = Precision.Integer,
            unitTargets = defaults.unitTargets +
                (
                    UnitCategory.LENGTH to
                        listOf(
                            UnitTargetSetting(
                                UnitId("length.m"),
                                Precision.Integer,
                            ),
                        )
                ),
        )

        AppPreference.utility_candidate_config = updated

        assertEquals(updated, ImePreferencesSnapshot.from(AppPreference).utilityCandidateConfig)
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        assertEquals(
            "integer",
            preferences.getString(AppPreference.UTILITY_CALCULATION_PRECISION_KEY, null),
        )
        assertTrue(
            preferences.getString(AppPreference.UTILITY_UNIT_TARGETS_JSON_KEY, null)
                ?.contains("\"precision\":\"integer\"") == true,
        )
    }

    @Test
    fun precisionEntriesAreLocalizedOrderedAndMatchStoredValues() {
        val values = context.resources.getStringArray(R.array.utility_precision_values).toList()
        assertEquals(
            listOf("auto", "integer") + (Precision.MIN_DIGITS..Precision.MAX_DIGITS).map(Int::toString),
            values,
        )

        val englishEntries = localizedPrecisionEntries(Locale.ENGLISH)
        val japaneseEntries = localizedPrecisionEntries(Locale.JAPANESE)
        assertEquals(values.size, englishEntries.size)
        assertEquals(values.size, japaneseEntries.size)
        assertEquals("Automatic", englishEntries[0])
        assertEquals("Integer (0 decimal places)", englishEntries[1])
        assertEquals("3 significant digits", englishEntries[2])
        assertEquals("自動", japaneseEntries[0])
        assertEquals("整数（小数点以下0桁）", japaneseEntries[1])
        assertEquals("有効数字 3 桁", japaneseEntries[2])
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
        assertTrue(routeIsInPredictionSourceCategory())
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

    private fun routeIsInPredictionSourceCategory(): Boolean {
        val parser = context.resources.getXml(R.xml.pref_conversion_engine)
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        var currentCategoryTitle: String? = null
        return parser.use {
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "PreferenceCategory" -> {
                            val titleRes = parser.getAttributeResourceValue(
                                androidNamespace,
                                "title",
                                0,
                            )
                            currentCategoryTitle = titleRes.takeIf { it != 0 }?.let(context::getString)
                        }
                        "Preference" -> if (
                            parser.getAttributeValue(androidNamespace, "key") == ROUTE_KEY
                        ) {
                            return@use currentCategoryTitle ==
                                context.getString(R.string.prediction_source_category_title)
                        }
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "PreferenceCategory") {
                        currentCategoryTitle = null
                    }
                }
                parser.next()
            }
            false
        }
    }

    private fun localizedPrecisionEntries(locale: Locale): List<String> {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        return context.createConfigurationContext(configuration)
            .resources.getStringArray(R.array.utility_precision_entries).toList()
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
