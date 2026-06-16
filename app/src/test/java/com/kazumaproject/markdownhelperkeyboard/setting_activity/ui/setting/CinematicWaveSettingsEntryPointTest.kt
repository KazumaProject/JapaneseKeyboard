package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CinematicWaveSettingsEntryPointTest {

    @Test
    fun cinematicWaveSettingsExistInNewAndLegacyPreferenceXml() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val newKeys = preferenceKeys(context, R.xml.pref_keyboard_display)
        val legacyKeys = preferenceKeys(context, R.xml.pref_common_legacy)

        requiredCinematicWaveKeys.forEach { key ->
            assertTrue("Missing $key in pref_keyboard_display.xml", key in newKeys)
            assertTrue("Missing $key in pref_common_legacy.xml", key in legacyKeys)
        }
    }

    @Test
    fun cinematicWaveSettingsCanBeAddedFromNewFrequentSettingsScreen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val candidateKeys = SettingDestinations.frequentCandidates(context)
            .map { it.key }
            .toSet()

        requiredCinematicWaveKeys.forEach { key ->
            assertTrue("Missing $key from frequent setting candidates", key in candidateKeys)
        }
    }

    @Test
    fun cinematicWaveLegacySearchKeepsParentControlsVisible() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val legacyDestinations = SettingSearchIndex.legacySearchable(context)
            .associateBy { it.key }

        val colorMode = legacyDestinations[
            "keyboard_touch_effect_cinematic_wave_color_mode_preference"
        ] ?: error("Missing Cinematic Wave color mode from legacy search")
        val waveType = legacyDestinations[
            "keyboard_touch_effect_cinematic_wave_type_preference"
        ] ?: error("Missing Cinematic Wave type from legacy search")
        val waveTypeLegacyTarget = waveType.legacyTarget
            ?: error("Cinematic Wave type should use a legacy target")

        assertEquals(SettingSearchScope.LEGACY_TABS, colorMode.searchScope)
        assertEquals(SettingSearchScope.LEGACY_TABS, waveType.searchScope)
        assertTrue(
            "Cinematic Wave search result should keep effect type visible",
            "keyboard_touch_effect_type_preference" in waveTypeLegacyTarget.relatedPreferenceKeys
        )
        assertTrue(
            "Custom-color search result should keep color mode visible",
            "keyboard_touch_effect_cinematic_wave_color_mode_preference" in
                waveTypeLegacyTarget.relatedPreferenceKeys
        )
    }

    private fun preferenceKeys(context: Context, xmlRes: Int): Set<String> {
        val parser = context.resources.getXml(xmlRes)
        try {
            return buildSet {
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType != XmlPullParser.START_TAG) continue
                    parser.getAttributeValue(ANDROID_NS, "key")?.let(::add)
                }
            }
        } finally {
            parser.close()
        }
    }

    private companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

        private val requiredCinematicWaveKeys = listOf(
            "keyboard_touch_effect_cinematic_wave_color_mode_preference",
            "keyboard_touch_effect_cinematic_wave_type_preference",
            "keyboard_touch_effect_cinematic_wave_primary_color_preference",
            "keyboard_touch_effect_cinematic_wave_secondary_color_auto_preference",
            "keyboard_touch_effect_cinematic_wave_secondary_color_preference",
            "keyboard_touch_effect_cinematic_wave_opacity_percent_preference",
            "keyboard_touch_effect_cinematic_wave_intensity_percent_preference",
            "keyboard_touch_effect_cinematic_wave_motion_preference",
            "keyboard_touch_effect_cinematic_wave_touch_response_preference",
            "keyboard_touch_effect_cinematic_wave_quality_preference"
        )
    }
}
