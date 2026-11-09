package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingDestinationType
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingSearchIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPreferenceKeyPopupVisibilityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val switches = mapOf(
        AppPreference.TENKEY_SHOW_FLICK_POPUP_KEY to { AppPreference.tenkey_show_flick_popup_preference },
        AppPreference.TENKEY_SHOW_LONG_PRESS_POPUP_KEY to { AppPreference.tenkey_show_long_press_popup_preference },
        AppPreference.SUMIRE_SHOW_FLICK_POPUP_KEY to { AppPreference.sumire_show_flick_popup_preference },
        AppPreference.SUMIRE_SHOW_LONG_PRESS_POPUP_KEY to { AppPreference.sumire_show_long_press_popup_preference }
    )

    @Test
    fun defaultsOnAndEachSwitchCanBeDisabledIndependently() {
        AppPreference.init(context)
        assertTrue(switches.values.all { it() })

        switches.keys.forEach { disabledKey ->
            preferences.edit().putBoolean(disabledKey, false).commit()
            switches.forEach { (key, readValue) ->
                assertEquals(key, key != disabledKey, readValue())
            }
            preferences.edit().remove(disabledKey).commit()
        }
    }

    @Test
    fun bothSettingsInterfacesExposeAllFourSwitches() {
        val modern = SettingSearchIndex.searchable(context).associateBy { it.key }
        val legacy = SettingSearchIndex.legacySearchable(context).associateBy { it.key }
        switches.keys.forEach { key ->
            val tenKey = key.startsWith("tenkey")
            val destination = if (tenKey) R.id.kanaPreferenceFragment else R.id.sumirePreferenceFragment
            val xml = if (tenKey) R.xml.pref_kana else R.xml.pref_sumire

            val modernSwitch = modern.getValue(key).destination as SettingDestinationType.SwitchPreference
            assertEquals(destination, modernSwitch.destinationId)
            assertTrue(modernSwitch.defaultValue)

            val legacySwitch = legacy.getValue(key)
            assertEquals(destination,
                (legacySwitch.destination as SettingDestinationType.NavDestination).destinationId)
            assertEquals(xml, legacySwitch.legacyTarget?.xmlRes)
            assertEquals(key, legacySwitch.legacyTarget?.preferenceKey)
        }
    }
}
