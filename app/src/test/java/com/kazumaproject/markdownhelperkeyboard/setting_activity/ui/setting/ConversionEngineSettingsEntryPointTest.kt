package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
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
class ConversionEngineSettingsEntryPointTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun newHomeHasConversionEngineCategory() {
        val destination = SettingDestinations.categories(context)
            .single { it.key == "setting_route_conversion_engine" }

        assertEquals(SettingCategory.CONVERSION_ENGINE, destination.category)
        assertEquals(R.id.conversionEnginePreferenceFragment, SettingDestinations.destinationId(destination.destination))
    }

    @Test
    fun legacyTabsContainConversionEngineTab() {
        val tab = SettingTabRegistry.createTabs()
            .single { it.key == SettingTabRegistry.TAB_CONVERSION_ENGINE }

        assertEquals(R.xml.pref_conversion_engine, tab.xmlRes)
        assertEquals(R.id.conversionEnginePreferenceFragment, tab.destinationId)
    }

    @Test
    fun conversionEnginePreferenceIsSearchableInNewAndLegacySettings() {
        val newHomeKeys = SettingSearchIndex.searchable(context, SettingSearchScope.NEW_HOME)
            .associateBy { it.key }
        val legacyKeys = SettingSearchIndex.searchable(context, SettingSearchScope.LEGACY_TABS)
            .associateBy { it.key }

        assertEquals(
            SettingCategory.CONVERSION_ENGINE,
            newHomeKeys.getValue(CONVERSION_ENGINE_KEY).category
        )
        assertEquals(
            SettingCategory.CONVERSION_ENGINE,
            legacyKeys.getValue(CONVERSION_ENGINE_KEY).category
        )
    }

    @Test
    fun conversionEngineKeyDoesNotLiveInOtherConversionOrDictionaryXml() {
        assertTrue(CONVERSION_ENGINE_KEY in preferenceKeys(R.xml.pref_conversion_engine))
        assertFalse(CONVERSION_ENGINE_KEY in preferenceKeys(R.xml.pref_candidate_conversion))
        assertFalse(CONVERSION_ENGINE_KEY in preferenceKeys(R.xml.pref_dictionary))
        assertFalse(CONVERSION_ENGINE_KEY in preferenceKeys(R.xml.pref_ai_conversion))
    }

    private fun preferenceKeys(xmlRes: Int): Set<String> {
        val parser = context.resources.getXml(xmlRes)
        return parser.use {
            buildSet {
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG) {
                        parser.getAttributeValue(ANDROID_NS, "key")?.let(::add)
                    }
                    parser.next()
                }
            }
        }
    }

    private companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private const val CONVERSION_ENGINE_KEY = "conversion_engine_preference"
    }
}
