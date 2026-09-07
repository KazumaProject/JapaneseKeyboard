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
    @Test
    fun batchedSnapshotsAreCompleteForImmediateReadersAndListeners() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            var notifications = 0
            val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key?.startsWith("tenkey_last_") == true) {
                    assertEquals("english", AppPreference.tenkey_last_input_mode_preference)
                    assertEquals("qwerty", AppPreference.tenkey_last_input_mode_presentation_preference)
                    assertEquals("english", AppPreference.tenkey_last_qwerty_number_return_target_preference)
                    assertEquals(123L, AppPreference.tenkey_last_input_mode_saved_at_epoch_millis_preference)
                    notifications++
                }
                if (key?.startsWith("sumire_last_") == true) {
                    assertEquals("number", AppPreference.sumire_last_input_mode_preference)
                    assertEquals("native", AppPreference.sumire_last_input_mode_presentation_preference)
                    assertEquals(456L, AppPreference.sumire_last_input_mode_saved_at_epoch_millis_preference)
                    notifications++
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            try {
                AppPreference.saveTenkeyRestartInputMode("english", "qwerty", "english", 123L)
                assertEquals(123L, AppPreference.tenkey_last_input_mode_saved_at_epoch_millis_preference)
                AppPreference.saveSumireRestartInputMode("number", "native", 456L)
                assertEquals(456L, AppPreference.sumire_last_input_mode_saved_at_epoch_millis_preference)
                assertEquals(7, notifications)
            } finally {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
    }

}
