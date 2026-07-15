package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.ImePreferencesSnapshot
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.containsHentaigana
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
class HentaiganaCandidateSuppressionPreferenceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun hentaiganaCandidateSuppressionDefaultsToOffAndPersists() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)

        assertFalse(AppPreference.suppress_hentaigana_candidates_preference)
        assertFalse(ImePreferencesSnapshot.from(AppPreference).suppressHentaiganaCandidates)

        AppPreference.suppress_hentaigana_candidates_preference = true

        assertTrue(
            preferences.getBoolean(SUPPRESS_HENTAIGANA_CANDIDATES_KEY, false)
        )
        assertTrue(AppPreference.suppress_hentaigana_candidates_preference)
        assertTrue(ImePreferencesSnapshot.from(AppPreference).suppressHentaiganaCandidates)
    }

    @Test
    fun switchIsAvailableFromNewAndLegacyDictionarySettings() {
        assertTrue(dictionaryXmlContainsDefaultOffSwitch())

        val newSettings = SettingSearchIndex.searchable(context, SettingSearchScope.NEW_HOME)
            .first { it.key == SUPPRESS_HENTAIGANA_CANDIDATES_KEY }
        val legacySettings = SettingSearchIndex.searchable(context, SettingSearchScope.LEGACY_TABS)
            .first { it.key == SUPPRESS_HENTAIGANA_CANDIDATES_KEY }

        assertTrue(newSettings.destination is SettingDestinationType.SwitchPreference)
        assertFalse(
            (newSettings.destination as SettingDestinationType.SwitchPreference).defaultValue
        )
        val legacyTarget = requireNotNull(legacySettings.legacyTarget)
        assertEquals(SettingTabRegistry.TAB_DICTIONARY, legacyTarget.tabKey)
        assertEquals(SUPPRESS_HENTAIGANA_CANDIDATES_KEY, legacyTarget.preferenceKey)
    }

    @Test
    fun hentaiganaUnicodeRangeIsDetected() {
        val hentaigana = String(Character.toChars(0x1B000))

        assertTrue(hentaigana.containsHentaigana())
        assertTrue("候補$hentaigana".containsHentaigana())
        assertFalse("かな候補".containsHentaigana())
    }

    private fun dictionaryXmlContainsDefaultOffSwitch(): Boolean {
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val parser = context.resources.getXml(R.xml.pref_dictionary)
        return parser.use {
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (
                    parser.eventType == XmlPullParser.START_TAG &&
                    parser.getAttributeValue(androidNamespace, "key") ==
                    SUPPRESS_HENTAIGANA_CANDIDATES_KEY
                ) {
                    return@use parser.name.endsWith("SwitchPreferenceCompat") &&
                        parser.getAttributeBooleanValue(
                            androidNamespace,
                            "defaultValue",
                            true,
                        ).not()
                }
                parser.next()
            }
            false
        }
    }

    private companion object {
        const val SUPPRESS_HENTAIGANA_CANDIDATES_KEY =
            "suppress_hentaigana_candidates_preference"
    }
}
