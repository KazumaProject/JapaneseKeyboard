package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.ComposingGuideSettings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ZenzFloatingSettingsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun initializePreferences() { AppPreference.init(context) }

    @Test fun newAndLegacySettingsShareTheZenzPreferenceKeys() {
        val modernItems = SettingSearchIndex.searchable(context)
        val legacyItems = SettingSearchIndex.legacySearchable(context)
        for (key in listOf(ComposingGuideSettings.ZENZ_ENABLED, ComposingGuideSettings.ZENZ_LIVE_CONVERSION)) {
            if (!AppVariantConfig.hasZenz) {
                assertFalse(modernItems.any { it.key == key })
                assertFalse(legacyItems.any { it.key == key })
                continue
            }
            val modern = modernItems.first { it.key == key }
            val legacy = legacyItems.first { it.key == key }
            val toggle = modern.destination as SettingDestinationType.SwitchPreference
            assertFalse(toggle.defaultValue)
            assertEquals(R.id.zenzPreferenceFragment, toggle.destinationId)
            assertEquals(key, legacy.legacyTarget?.preferenceKey)
            assertEquals(SettingTabRegistry.TAB_ZENZ, legacy.legacyTarget?.tabKey)
        }
        if (AppVariantConfig.hasZenz) {
            assertEquals(R.xml.pref_zenz, SettingTabRegistry.createTabs().first { it.key == SettingTabRegistry.TAB_ZENZ }.xmlRes)
        }
    }

    @Test fun liveConversionSwitchFollowsAndDependsOnFloatingDisplayWithDefaultOff() {
        val namespace = "http://schemas.android.com/apk/res/android"
        val keys = mutableListOf<String>()
        context.resources.getXml(R.xml.pref_zenz).use { parser ->
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    val key = parser.getAttributeValue(namespace, "key")
                    if (key != null) keys.add(key)
                    if (key == ComposingGuideSettings.ZENZ_LIVE_CONVERSION) {
                        assertEquals("SwitchPreferenceCompat", parser.name)
                        assertFalse(parser.getAttributeBooleanValue(namespace, "defaultValue", true))
                        assertEquals(ComposingGuideSettings.ZENZ_ENABLED,
                            parser.getAttributeValue(namespace, "dependency"))
                    }
                }
                parser.next()
            }
        }
        assertTrue(keys.contains(ComposingGuideSettings.ZENZ_LIVE_CONVERSION))
        assertEquals(keys.indexOf(ComposingGuideSettings.ZENZ_ENABLED) + 1,
            keys.indexOf(ComposingGuideSettings.ZENZ_LIVE_CONVERSION))
    }
    @Test fun obsoletePositionResetIsNotSearchable() {
        val key = "zenz_floating_candidates_reset"
        assertTrue(SettingSearchIndex.destinationsForKeys(context, listOf(key)).isEmpty())
        assertFalse(SettingSearchIndex.legacySearchable(context).any { it.key == key })
    }
}
