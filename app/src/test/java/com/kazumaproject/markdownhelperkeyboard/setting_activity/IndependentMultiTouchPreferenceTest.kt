package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.app.Application
import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.ImePreferencesSnapshot
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingDestinations
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingDestinationType
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingSearchIndex
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class IndependentMultiTouchPreferenceTest {
    private lateinit var context: Context
    private val key = AppPreference.INDEPENDENT_MULTI_TOUCH_KEY

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test fun existingInstallDefaultsOffAndSnapshotTracksSavedSetting() {
        assertFalse(AppPreference.independent_multi_touch_preference)
        assertFalse(ImePreferencesSnapshot.from(AppPreference).independentMultiTouchEnabled)
        AppPreference.independent_multi_touch_preference = true
        AppPreference.init(context)
        assertTrue(AppPreference.independent_multi_touch_preference)
        assertTrue(ImePreferencesSnapshot.from(AppPreference).independentMultiTouchEnabled)
    }

    @Test fun backupRoundTripKeepsTheSharedSwitch() {
        AppPreference.independent_multi_touch_preference = true
        val backup = AppPreference.exportAllToJson()
        AppPreference.independent_multi_touch_preference = false
        AppPreference.importAllFromJson(backup, replaceAll = true)
        assertTrue(AppPreference.independent_multi_touch_preference)
    }

    @Test fun bothOperationScreensDeclareTheSameDefaultOffSwitch() {
        for (resource in listOf(R.xml.pref_operation_feedback, R.xml.pref_common_legacy)) {
            val parser = context.resources.getXml(resource)
            parser.use {
                var matches = 0
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG &&
                        parser.getAttributeValue("http://schemas.android.com/apk/res/android", "key") == key) {
                        matches++
                        assertEquals("SwitchPreferenceCompat", parser.name)
                        assertEquals("false", parser.getAttributeValue("http://schemas.android.com/apk/res/android", "defaultValue"))
                    }
                    parser.next()
                }
                assertEquals(1, matches)
            }
        }
    }

    @Test fun switchSitsAfterFlickSettingsAndIsAbsentFromNewCommonHome() {
        for (resource in listOf(R.xml.pref_operation_feedback, R.xml.pref_common_legacy)) {
            val keys = mutableListOf<String>()
            context.resources.getXml(resource).use { parser ->
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG) {
                        parser.getAttributeValue("http://schemas.android.com/apk/res/android", "key")?.let(keys::add)
                    }
                    parser.next()
                }
            }
            val index = keys.indexOf(key)
            assertEquals("Immediately after flick settings", keys.indexOf("flick_threshold_shape_preference") + 1, index)
            assertTrue(index < keys.indexOf("long_press_timeout_preference"))
        }
        context.resources.getXml(R.xml.pref_common).use { parser ->
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                assertNotEquals(key, parser.getAttributeValue("http://schemas.android.com/apk/res/android", "key"))
                parser.next()
            }
        }
    }

    @Test fun searchAndFrequentSettingsPointToTheCorrectOperationScreen() {
        val newSetting = SettingSearchIndex.destinationsForKeys(context, listOf(key)).single()
        assertEquals(R.id.operationFeedbackPreferenceFragment, (newSetting.destination as SettingDestinationType.SwitchPreference).destinationId)
        assertFalse((newSetting.destination as SettingDestinationType.SwitchPreference).defaultValue)
        val legacySetting = SettingSearchIndex.legacyDestinationsForKeys(context, listOf(key)).single()
        assertEquals(R.id.legacyCommonPreferenceFragment, SettingDestinations.destinationId(legacySetting.destination))
        assertTrue(SettingDestinations.frequentCandidates(context).any { it.key == key })
    }
}
