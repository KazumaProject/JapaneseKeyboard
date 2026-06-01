package com.kazumaproject.markdownhelperkeyboard.setting_activity

import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class AppPreferenceRestartInputModeInstrumentedTest {

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun restoreInputModePreferencesUseDefaults() {
        assertFalse(AppPreference.tenkey_restore_input_mode_on_restart_preference)
        assertFalse(AppPreference.sumire_restore_input_mode_on_restart_preference)
        assertEquals("japanese", AppPreference.tenkey_last_input_mode_preference)
        assertEquals("japanese", AppPreference.sumire_last_input_mode_preference)
    }

    @Test
    fun tenkeyLastInputModeCanStoreEnglishAndNumber() {
        AppPreference.tenkey_last_input_mode_preference = "english"
        assertEquals("english", AppPreference.tenkey_last_input_mode_preference)

        AppPreference.tenkey_last_input_mode_preference = "number"
        assertEquals("number", AppPreference.tenkey_last_input_mode_preference)
    }

    @Test
    fun sumireLastInputModeCanStoreEnglishAndNumber() {
        AppPreference.sumire_last_input_mode_preference = "english"
        assertEquals("english", AppPreference.sumire_last_input_mode_preference)

        AppPreference.sumire_last_input_mode_preference = "number"
        assertEquals("number", AppPreference.sumire_last_input_mode_preference)
    }
}
