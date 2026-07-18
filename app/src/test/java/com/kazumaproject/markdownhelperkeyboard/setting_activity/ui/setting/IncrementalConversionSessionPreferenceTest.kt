package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.session.ConversionBackend
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
class IncrementalConversionSessionPreferenceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun defaultsToLegacyAndPersistsSessionBackend() {
        assertFalse(AppPreference.incremental_conversion_session_preference)
        assertEquals(
            ConversionBackend.LEGACY,
            ImePreferencesSnapshot.from(AppPreference).conversionBackend,
        )

        AppPreference.incremental_conversion_session_preference = true

        assertTrue(AppPreference.incremental_conversion_session_preference)
        assertEquals(
            ConversionBackend.INCREMENTAL_SESSION,
            ImePreferencesSnapshot.from(AppPreference).conversionBackend,
        )
    }

    @Test
    fun switchIsSharedByNewAndLegacyDictionarySettings() {
        assertTrue(dictionaryXmlContainsDefaultOffSwitch())

        val newSettings = SettingSearchIndex.searchable(context, SettingSearchScope.NEW_HOME)
            .first { it.key == KEY }
        val legacySettings = SettingSearchIndex.searchable(context, SettingSearchScope.LEGACY_TABS)
            .first { it.key == KEY }

        assertTrue(newSettings.destination is SettingDestinationType.SwitchPreference)
        assertFalse(
            (newSettings.destination as SettingDestinationType.SwitchPreference).defaultValue
        )
        val legacyTarget = requireNotNull(legacySettings.legacyTarget)
        assertEquals(SettingTabRegistry.TAB_DICTIONARY, legacyTarget.tabKey)
        assertEquals(KEY, legacyTarget.preferenceKey)
    }

    private fun dictionaryXmlContainsDefaultOffSwitch(): Boolean {
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val parser = context.resources.getXml(R.xml.pref_dictionary)
        return parser.use {
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (
                    parser.eventType == XmlPullParser.START_TAG &&
                    parser.getAttributeValue(androidNamespace, "key") == KEY
                ) {
                    return@use parser.name.endsWith("SwitchPreferenceCompat") &&
                        !parser.getAttributeBooleanValue(androidNamespace, "defaultValue", true)
                }
                parser.next()
            }
            false
        }
    }

    private companion object {
        const val KEY = "incremental_conversion_session_preference"
    }
}
