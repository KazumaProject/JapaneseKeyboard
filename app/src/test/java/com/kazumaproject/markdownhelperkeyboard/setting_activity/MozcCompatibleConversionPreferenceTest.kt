package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.util.Xml
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MozcCompatibleConversionPreferenceTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun preferenceDefaultsFalseAndPersistsValues() {
        assertFalse(AppPreference.enable_mozc_compatible_conversion_preference)

        AppPreference.enable_mozc_compatible_conversion_preference = true
        assertTrue(AppPreference.enable_mozc_compatible_conversion_preference)

        AppPreference.enable_mozc_compatible_conversion_preference = false
        assertFalse(AppPreference.enable_mozc_compatible_conversion_preference)
    }

    @Test
    fun newAndLegacyPreferenceScreensUseSameKeyAndDefault() {
        val key = AppPreference.ENABLE_MOZC_COMPATIBLE_CONVERSION_PREFERENCE

        val newScreen = findSwitchPreference(R.xml.pref_candidate_conversion, key)
        val legacyScreen = findSwitchPreference(R.xml.pref_common_legacy, key)

        listOf(newScreen, legacyScreen).forEach { attrs ->
            assertNotNull(attrs)
            assertEquals("false", attrs!!.defaultValue)
            assertEquals(R.string.pref_mozc_compatible_conversion_title, attrs.titleResId)
            assertEquals(R.string.pref_mozc_compatible_conversion_summary, attrs.summaryResId)
        }
        assertEquals(
            1,
            countPreferenceKey(R.xml.pref_candidate_conversion, key),
        )
        assertEquals(
            1,
            countPreferenceKey(R.xml.pref_common_legacy, key),
        )
    }

    private fun findSwitchPreference(resId: Int, key: String): SwitchAttrs? {
        val parser = context.resources.getXml(resId)
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG &&
                parser.name.substringAfterLast('.') == "SwitchPreferenceCompat" &&
                parser.androidAttr("key") == key
            ) {
                return SwitchAttrs(
                    defaultValue = parser.androidAttr("defaultValue"),
                    titleResId = parser.androidAttrResId("title"),
                    summaryResId = parser.androidAttrResId("summary"),
                )
            }
            parser.next()
        }
        return null
    }

    private fun countPreferenceKey(resId: Int, key: String): Int {
        val parser = context.resources.getXml(resId)
        var count = 0
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.androidAttr("key") == key) {
                count++
            }
            parser.next()
        }
        return count
    }

    private fun XmlPullParser.androidAttr(name: String): String? =
        getAttributeValue("http://schemas.android.com/apk/res/android", name)

    private fun XmlPullParser.androidAttrResId(name: String): Int =
        Xml.asAttributeSet(this).getAttributeResourceValue(
            "http://schemas.android.com/apk/res/android",
            name,
            0,
        )

    private data class SwitchAttrs(
        val defaultValue: String?,
        val titleResId: Int,
        val summaryResId: Int,
    )
}
