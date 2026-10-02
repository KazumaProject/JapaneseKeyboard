package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
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
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CandidateHeightStabilizationSettingsTest {
    private lateinit var context: Context
    private val key = AppPreference.STABILIZE_CANDIDATE_STRIP_HEIGHT_KEY

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun defaultsToOffAndSnapshotReadsTheSharedSetting() {
        assertFalse(AppPreference.stabilize_candidate_strip_height_preference)
        assertFalse(ImePreferencesSnapshot.from(AppPreference).stabilizeCandidateStripHeightPreference)
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(key, true).commit()
        assertTrue(AppPreference.stabilize_candidate_strip_height_preference)
        assertTrue(ImePreferencesSnapshot.from(AppPreference).stabilizeCandidateStripHeightPreference)
        AppPreference.stabilize_candidate_strip_height_preference = false
        assertFalse(PreferenceManager.getDefaultSharedPreferences(context).getBoolean(key, true))
    }

    @Test
    fun candidateConversionScreensPlaceTheSwitchBesideCandidateSettings() {
        for (xml in listOf(R.xml.pref_candidate_conversion, R.xml.pref_common_legacy)) {
            val parser = context.resources.getXml(xml)
            parser.use {
                val keys = mutableListOf<String>()
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG) {
                        val preferenceKey = parser.getAttributeValue(ANDROID_NS, "key")
                        if (preferenceKey != null) keys += preferenceKey
                        if (preferenceKey == key) {
                            assertEquals("SwitchPreferenceCompat", parser.name)
                            assertEquals("false", parser.getAttributeValue(ANDROID_NS, "defaultValue"))
                        }
                    }
                    parser.next()
                }
                val settingIndex = keys.indexOf(key)
                assertTrue("Missing switch in candidate settings screen $xml", settingIndex >= 0)
                assertTrue("Switch should follow candidate tab settings in $xml", keys[settingIndex - 1] in setOf(
                    "candidate_tab_order_preference",
                    "candidate_column_landscape_preference",
                ))
                assertEquals("hide_candidate_password_preference", keys[settingIndex + 1])
            }
        }
        val commonParser = context.resources.getXml(R.xml.pref_common)
        commonParser.use {
            while (commonParser.eventType != XmlPullParser.END_DOCUMENT) {
                if (commonParser.eventType == XmlPullParser.START_TAG) {
                    assertTrue(
                        "Candidate height stabilization belongs in candidate conversion settings",
                        commonParser.getAttributeValue(ANDROID_NS, "key") != key,
                    )
                }
                commonParser.next()
            }
        }
    }

    @Test
    fun searchableFromBothSettingsAndAvailableForFrequentSettings() {
        for (scope in listOf(SettingSearchScope.NEW_HOME, SettingSearchScope.LEGACY_TABS)) {
            assertTrue(SettingSearchIndex.searchable(context, scope).any { it.key == key })
        }
        assertTrue(SettingDestinations.frequentCandidates(context).any { it.key == key })
    }

    @Test
    fun backupRestoresTheSettingAndOlderBackupsKeepTheDefault() {
        AppPreference.stabilize_candidate_strip_height_preference = true
        val backup = AppPreference.exportAllToJson()
        AppPreference.stabilize_candidate_strip_height_preference = false
        AppPreference.importAllFromJson(backup)
        assertTrue(AppPreference.stabilize_candidate_strip_height_preference)
        AppPreference.importAllFromJson("{\"entries\":[]}")
        assertFalse(AppPreference.stabilize_candidate_strip_height_preference)
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
