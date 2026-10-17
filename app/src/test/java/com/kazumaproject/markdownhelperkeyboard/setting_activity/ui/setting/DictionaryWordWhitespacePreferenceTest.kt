package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DictionaryWordWhitespacePreferenceTest {
    private lateinit var context: Context
    private val key = AppPreference.PRESERVE_DICTIONARY_WORD_WHITESPACE_KEY

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test fun defaultsToOffAndSharesThePersistedValueWithPreferenceWidgets() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        assertFalse(AppPreference.preserve_dictionary_word_whitespace_preference)
        AppPreference.preserve_dictionary_word_whitespace_preference = true
        assertTrue(preferences.getBoolean(key, false))
        AppPreference.init(context)
        assertTrue(AppPreference.preserve_dictionary_word_whitespace_preference)
        preferences.edit().putBoolean(key, false).commit()
        assertFalse(AppPreference.preserve_dictionary_word_whitespace_preference)
    }

    @Test fun newAndLegacySettingsExposeTheSameDictionarySwitch() {
        val newSetting = SettingSearchIndex.searchable(context, SettingSearchScope.NEW_HOME)
            .single { it.key == key }
        assertEquals(SettingCategory.DICTIONARY, newSetting.category)
        val newSwitch = newSetting.destination as SettingDestinationType.SwitchPreference
        assertEquals(key, newSwitch.preferenceKey)
        assertFalse(newSwitch.defaultValue)
        val legacySetting = SettingSearchIndex.searchable(context, SettingSearchScope.LEGACY_TABS)
            .single { it.key == key }
        val legacyTarget = requireNotNull(legacySetting.legacyTarget)
        assertEquals(SettingTabRegistry.TAB_DICTIONARY, legacyTarget.tabKey)
        assertEquals(key, legacyTarget.preferenceKey)
        assertEquals(R.xml.pref_dictionary, legacyTarget.xmlRes)
    }

    @Test fun dictionaryXmlDefinesADefaultOffSwitch() {
        val namespace = "http://schemas.android.com/apk/res/android"
        context.resources.getXml(R.xml.pref_dictionary).use { parser ->
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG &&
                    parser.getAttributeValue(namespace, "key") == key) {
                    assertTrue(parser.name.endsWith("SwitchPreferenceCompat"))
                    assertFalse(parser.getAttributeBooleanValue(namespace, "defaultValue", true))
                    return
                }
                parser.next()
            }
        }
        fail("Dictionary whitespace switch is missing")
    }
}
