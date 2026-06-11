package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TypeNullInputBehaviorPreferenceTest {

    @Test
    fun legacyAndNewSettingsUseSamePreferenceKey() {
        assertEquals(
            TYPE_NULL_INPUT_BEHAVIOR_KEY,
            readTypeNullPreference(R.xml.pref_common_legacy).key
        )
        assertEquals(
            TYPE_NULL_INPUT_BEHAVIOR_KEY,
            readTypeNullPreference(R.xml.pref_input_method).key
        )
    }

    @Test
    fun legacyAndNewSettingsUseDefaultValue() {
        assertEquals("default", readTypeNullPreference(R.xml.pref_common_legacy).defaultValue)
        assertEquals("default", readTypeNullPreference(R.xml.pref_input_method).defaultValue)
    }

    @Test
    fun typeNullInputBehaviorEntriesAndValuesMatchContract() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertArrayEquals(
            arrayOf("default", "direct_commit", "composing_text"),
            context.resources.getStringArray(R.array.type_null_input_behavior_values)
        )
    }

    @Test
    fun sharedPreferencesStoreOverrideWithSameKey() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit().clear().commit()

        preferences.edit()
            .putString(TYPE_NULL_INPUT_BEHAVIOR_KEY, "composing_text")
            .commit()

        assertEquals(
            "composing_text",
            preferences.getString(TYPE_NULL_INPUT_BEHAVIOR_KEY, "default")
        )
    }

    @Test
    fun selectingDefaultStoresDefaultValue() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit().clear().commit()

        preferences.edit()
            .putString(TYPE_NULL_INPUT_BEHAVIOR_KEY, "default")
            .commit()

        assertEquals(
            "default",
            preferences.getString(TYPE_NULL_INPUT_BEHAVIOR_KEY, "direct_commit")
        )
    }

    private fun readTypeNullPreference(xmlRes: Int): PreferenceDeclaration {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parser = context.resources.getXml(xmlRes)
        try {
            while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != org.xmlpull.v1.XmlPullParser.START_TAG) continue
                if (parser.name != "ListPreference") continue
                val key = parser.getAttributeValue(ANDROID_NS, "key")
                if (key != TYPE_NULL_INPUT_BEHAVIOR_KEY) continue
                return PreferenceDeclaration(
                    key = key,
                    defaultValue = parser.getAttributeValue(ANDROID_NS, "defaultValue"),
                )
            }
        } finally {
            parser.close()
        }
        error("TYPE_NULL input behavior preference not found in $xmlRes")
    }

    private data class PreferenceDeclaration(
        val key: String,
        val defaultValue: String?,
    )

    private companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private const val TYPE_NULL_INPUT_BEHAVIOR_KEY = "type_null_input_behavior_preference"
    }
}
