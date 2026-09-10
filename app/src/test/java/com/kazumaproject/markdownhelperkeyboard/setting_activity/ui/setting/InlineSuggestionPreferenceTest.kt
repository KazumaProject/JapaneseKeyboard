package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InlineSuggestionPreferenceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @Test
    fun settingExistsInNewAndLegacyCommonSettings() {
        assertTrue(AppPreference.INLINE_SUGGESTION_ENABLED_KEY in preferenceKeys(R.xml.pref_common))
        assertTrue(
            AppPreference.INLINE_SUGGESTION_ENABLED_KEY in
                preferenceKeys(R.xml.pref_common_legacy)
        )
        assertEquals(
            "true",
            preferenceDefaultValue(R.xml.pref_common, AppPreference.INLINE_SUGGESTION_ENABLED_KEY)
        )
        assertEquals(
            "true",
            preferenceDefaultValue(
                R.xml.pref_common_legacy,
                AppPreference.INLINE_SUGGESTION_ENABLED_KEY,
            )
        )
    }

    @Test
    fun appPreferenceDefaultsToEnabledAndPersistsDisabledState() {
        AppPreference.init(context)
        assertTrue(AppPreference.inline_suggestion_enabled_preference)

        AppPreference.inline_suggestion_enabled_preference = false

        assertFalse(AppPreference.inline_suggestion_enabled_preference)
    }

    @Test
    fun descriptionExplainsAndroidApiAndSupportRequirements() {
        val summary = context.getString(R.string.inline_suggestion_enabled_summary_on)

        assertTrue(summary.contains("Android 11"))
        assertTrue(summary.contains("API 30"))
        assertTrue(summary.contains("autofill service"))
        assertTrue(summary.contains("target input field"))
    }

    private fun preferenceKeys(xmlRes: Int): Set<String> {
        val parser = context.resources.getXml(xmlRes)
        return try {
            buildSet {
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType != XmlPullParser.START_TAG) continue
                    parser.getAttributeValue(ANDROID_NS, "key")?.let(::add)
                }
            }
        } finally {
            parser.close()
        }
    }

    private fun preferenceDefaultValue(xmlRes: Int, key: String): String? {
        val parser = context.resources.getXml(xmlRes)
        return try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                if (parser.getAttributeValue(ANDROID_NS, "key") != key) continue
                return parser.getAttributeValue(ANDROID_NS, "defaultValue")
            }
            null
        } finally {
            parser.close()
        }
    }

    private companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
