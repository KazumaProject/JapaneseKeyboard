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
class DictionaryCandidateLabelPreferenceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun dictionaryCandidateLabelsDefaultToOffAndPersist() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)

        assertFalse(AppPreference.show_dictionary_candidate_labels_preference)
        assertFalse(ImePreferencesSnapshot.from(AppPreference).showDictionaryCandidateLabels)

        AppPreference.show_dictionary_candidate_labels_preference = true

        assertTrue(
            preferences.getBoolean(SHOW_DICTIONARY_CANDIDATE_LABELS_KEY, false)
        )
        assertTrue(AppPreference.show_dictionary_candidate_labels_preference)
        assertTrue(ImePreferencesSnapshot.from(AppPreference).showDictionaryCandidateLabels)
    }

    @Test
    fun switchIsAvailableFromNewAndLegacyDictionarySettings() {
        assertTrue(dictionaryXmlContainsDefaultOffSwitch())

        val newSettings = SettingSearchIndex.searchable(context, SettingSearchScope.NEW_HOME)
            .first { it.key == SHOW_DICTIONARY_CANDIDATE_LABELS_KEY }
        val legacySettings = SettingSearchIndex.searchable(context, SettingSearchScope.LEGACY_TABS)
            .first { it.key == SHOW_DICTIONARY_CANDIDATE_LABELS_KEY }

        assertTrue(newSettings.destination is SettingDestinationType.SwitchPreference)
        assertFalse(
            (newSettings.destination as SettingDestinationType.SwitchPreference).defaultValue
        )
        val legacyTarget = requireNotNull(legacySettings.legacyTarget)
        assertEquals(SettingTabRegistry.TAB_DICTIONARY, legacyTarget.tabKey)
        assertEquals(SHOW_DICTIONARY_CANDIDATE_LABELS_KEY, legacyTarget.preferenceKey)
    }

    private fun dictionaryXmlContainsDefaultOffSwitch(): Boolean {
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val parser = context.resources.getXml(R.xml.pref_dictionary)
        return parser.use {
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (
                    parser.eventType == XmlPullParser.START_TAG &&
                    parser.getAttributeValue(androidNamespace, "key") ==
                    SHOW_DICTIONARY_CANDIDATE_LABELS_KEY
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
        const val SHOW_DICTIONARY_CANDIDATE_LABELS_KEY =
            "show_dictionary_candidate_labels_preference"
    }
}
