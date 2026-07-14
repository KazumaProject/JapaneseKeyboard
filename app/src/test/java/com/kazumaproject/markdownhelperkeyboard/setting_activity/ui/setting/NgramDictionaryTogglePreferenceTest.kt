package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.ImePreferencesSnapshot
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NgramDictionaryTogglePreferenceTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun defaultsToEnabledAndPersistsBothSettings() {
        assertTrue(AppPreference.system_ngram_dictionary_enable_preference)
        assertTrue(AppPreference.custom_ngram_dictionary_enable_preference)
        assertTrue(ImePreferencesSnapshot.from(AppPreference).systemNgramDictionaryEnabled)
        assertTrue(ImePreferencesSnapshot.from(AppPreference).customNgramDictionaryEnabled)

        AppPreference.system_ngram_dictionary_enable_preference = false
        AppPreference.custom_ngram_dictionary_enable_preference = false

        assertFalse(AppPreference.system_ngram_dictionary_enable_preference)
        assertFalse(AppPreference.custom_ngram_dictionary_enable_preference)
        assertFalse(ImePreferencesSnapshot.from(AppPreference).systemNgramDictionaryEnabled)
        assertFalse(ImePreferencesSnapshot.from(AppPreference).customNgramDictionaryEnabled)
    }

    @Test
    fun switchesAreAvailableInNewAndLegacyDictionarySettings() {
        for (key in listOf(SYSTEM_KEY, CUSTOM_KEY)) {
            val newSetting = SettingSearchIndex.searchable(context, SettingSearchScope.NEW_HOME)
                .first { it.key == key }
            val legacySetting = SettingSearchIndex.searchable(context, SettingSearchScope.LEGACY_TABS)
                .first { it.key == key }
            assertTrue(newSetting.destination is SettingDestinationType.SwitchPreference)
            assertTrue((newSetting.destination as SettingDestinationType.SwitchPreference).defaultValue)
            assertTrue(legacySetting.legacyTarget?.tabKey == SettingTabRegistry.TAB_DICTIONARY)
        }
    }

    @Test
    fun ruleEditorRemainsAvailableWhenCustomDictionaryIsDisabled() {
        val parser = context.resources.getXml(R.xml.pref_dictionary)
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val hasIndependentEditor = parser.use {
            while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (
                    parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG &&
                    parser.getAttributeValue(androidNamespace, "key") == "n_gram_rule_preference"
                ) {
                    return@use parser.getAttributeValue(androidNamespace, "dependency") == null
                }
                parser.next()
            }
            false
        }
        assertTrue(hasIndependentEditor)
    }

    private companion object {
        const val SYSTEM_KEY = "system_ngram_dictionary_enable_preference"
        const val CUSTOM_KEY = "custom_ngram_dictionary_enable_preference"
    }
}
