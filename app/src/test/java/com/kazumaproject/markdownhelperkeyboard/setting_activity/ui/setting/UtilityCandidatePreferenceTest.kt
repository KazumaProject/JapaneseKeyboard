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
    fun decimalPrecisionPersistsForCalculationAndUnitTargets() {
        val defaults = AppPreference.utility_candidate_config
        val updated = defaults.copy(
            calculationPrecision = Precision.DecimalPlaces(2),
            unitTargets = defaults.unitTargets +
                (
                    UnitCategory.LENGTH to
                        listOf(
                            UnitTargetSetting(
                                UnitId("length.m"),
                                Precision.DecimalPlaces(2),
                            ),
                        )
                ),
        )

        AppPreference.utility_candidate_config = updated

        assertEquals(updated, ImePreferencesSnapshot.from(AppPreference).utilityCandidateConfig)
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        assertEquals(
            "decimal:2",
            preferences.getString(AppPreference.UTILITY_CALCULATION_PRECISION_KEY, null),
        )
        assertTrue(
            preferences.getString(AppPreference.UTILITY_UNIT_TARGETS_JSON_KEY, null)
                ?.contains("\"precision\":\"decimal:2\"") == true,
        )
    }

    @Test
    fun legacyIntegerPreferenceMigratesToDecimalPlacesZero() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit()
            .putString(AppPreference.UTILITY_CALCULATION_PRECISION_KEY, "integer")
            .putString(
                AppPreference.UTILITY_UNIT_TARGETS_JSON_KEY,
                """{"version":1,"categories":{"length":[
                    {"unitId":"length.m","precision":"integer"}
                ]}}""".trimIndent(),
            )
            .commit()

        val config = ImePreferencesSnapshot.from(AppPreference).utilityCandidateConfig
        assertEquals(Precision.DecimalPlaces(0), config.calculationPrecision)
        assertEquals(
            Precision.DecimalPlaces(0),
            config.unitTargets.getValue(UnitCategory.LENGTH).single().precision,
        )
    }

    @Test
    fun invalidDecimalCalculationPreferenceFallsBackToAutomatic() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(AppPreference.UTILITY_CALCULATION_PRECISION_KEY, "decimal:16")
            .commit()

        assertEquals(
            Precision.Auto,
            ImePreferencesSnapshot.from(AppPreference).utilityCandidateConfig.calculationPrecision,
        )
    }

    @Test
    fun precisionLabelsAreLocalized() {
        val english = localizedContext(Locale.ENGLISH)
        val japanese = localizedContext(Locale.JAPANESE)

        assertEquals("Automatic", english.utilityPrecisionLabel(Precision.Auto))
        assertEquals("0 decimal places", english.utilityPrecisionLabel(Precision.DecimalPlaces(0)))
        assertEquals("1 decimal place", english.utilityPrecisionLabel(Precision.DecimalPlaces(1)))
        assertEquals("2 decimal places", english.utilityPrecisionLabel(Precision.DecimalPlaces(2)))
        assertEquals("1 significant digit", english.utilityPrecisionLabel(Precision.SignificantDigits(1)))
        assertEquals("2 significant digits", english.utilityPrecisionLabel(Precision.SignificantDigits(2)))
        assertEquals("自動", japanese.utilityPrecisionLabel(Precision.Auto))
        assertEquals("小数点以下 0 桁", japanese.utilityPrecisionLabel(Precision.DecimalPlaces(0)))
        assertEquals("小数点以下 2 桁", japanese.utilityPrecisionLabel(Precision.DecimalPlaces(2)))
        assertEquals("有効数字 1 桁", japanese.utilityPrecisionLabel(Precision.SignificantDigits(1)))
        assertEquals("有効数字 2 桁", japanese.utilityPrecisionLabel(Precision.SignificantDigits(2)))
    }

    @Test
    fun screenInflatesAndSearchTargetsItsHighlightableDestination() {
        val screen = PreferenceManager(context).inflateFromResource(
            context,
            R.xml.pref_utility_candidate,
            null,
        )
        assertTrue(screen.findPreference<androidx.preference.Preference>(CALCULATION_KEY) != null)
        assertTrue(screen.findPreference<androidx.preference.Preference>(PRECISION_KEY) != null)
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

        val precisionResult = SettingSearchIndex.searchable(context, SettingSearchScope.NEW_HOME)
            .first { it.key == PRECISION_KEY }
        assertTrue(precisionResult.destination is SettingDestinationType.NavDestination)
        assertEquals(
            R.id.utilityCandidatePreferenceFragment,
            SettingDestinations.destinationId(precisionResult.destination),
        )
        assertEquals(
            PRECISION_KEY,
            SettingDestinations.highlightPreferenceKey(precisionResult.destination),
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

    private fun localizedContext(locale: Locale): Context {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        return context.createConfigurationContext(configuration)
    }

    private companion object {
        const val CALCULATION_KEY = "utility_calculation_enabled"
        const val PRECISION_KEY = "utility_calculation_precision"
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
