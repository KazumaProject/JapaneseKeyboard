package com.kazumaproject.markdownhelperkeyboard.setting_activity

import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.ime_service.ImePreferencesSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppPreferenceTabletTenkeyQwertyInstrumentedTest {
    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun tabletTenkeyEnglishQwertyDefaultIsFalse() {
        assertFalse(AppPreference.tablet_tenkey_qwerty_switch_english_layout)
    }

    @Test
    fun tenkeySwitchNumberToQwertyNumberDefaultIsFalse() {
        assertFalse(AppPreference.tenkey_switch_number_to_qwerty_number_preference)
    }

    @Test
    fun tabletTenkeyEnglishQwertyIsIndependentFromPhoneTenkeyPreference() {
        AppPreference.tablet_tenkey_qwerty_switch_english_layout = true

        assertTrue(AppPreference.tablet_tenkey_qwerty_switch_english_layout)
        assertFalse(AppPreference.tenkey_qwerty_switch_number_layout ?: true)

        AppPreference.tenkey_qwerty_switch_number_layout = true
        AppPreference.tablet_tenkey_qwerty_switch_english_layout = false

        assertTrue(AppPreference.tenkey_qwerty_switch_number_layout ?: false)
        assertFalse(AppPreference.tablet_tenkey_qwerty_switch_english_layout)
    }

    @Test
    fun snapshotKeepsTabletTenkeyEnglishQwertySeparateFromPhoneTenkeyPreference() {
        AppPreference.tablet_tenkey_qwerty_switch_english_layout = true
        AppPreference.tenkey_qwerty_switch_number_layout = false
        AppPreference.tenkey_switch_number_to_qwerty_number_preference = true

        val snapshot = ImePreferencesSnapshot.from(AppPreference)

        assertTrue(snapshot.tabletTenkeyQwertySwitchEnglish)
        assertFalse(snapshot.tenkeyQWERTYSwitchNumber)
        assertTrue(snapshot.tenkeySwitchNumberToQwertyNumberPreference)
    }
}
