package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.ImePreferencesSnapshot
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingDestinationType
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingSearchIndex
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPreferenceSumireModeKeyHighlightTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences get() = PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun setUp() {
        clearPreferences()
        AppPreference.init(context)
    }

    @After
    fun clearPreferences() { preferences.edit().clear().commit() }

    @Test
    fun defaultsOffAndPersistsBothValuesInTheImeSnapshot() {
        assertFalse(AppPreference.sumire_mode_key_highlight_preference)
        for (enabled in listOf(true, false)) {
            AppPreference.sumire_mode_key_highlight_preference = enabled
            AppPreference.init(context)
            assertEquals(enabled, AppPreference.sumire_mode_key_highlight_preference)
            assertEquals(enabled, ImePreferencesSnapshot.from(AppPreference).sumireModeKeyHighlight)
        }
    }

    @Test
    fun backupRestoresSelectionAndOlderBackupDefaultsOff() {
        val oldBackup = AppPreference.exportAllToJson()
        AppPreference.sumire_mode_key_highlight_preference = true
        val enabledBackup = AppPreference.exportAllToJson()
        AppPreference.importAllFromJson(oldBackup)
        assertFalse(AppPreference.sumire_mode_key_highlight_preference)
        AppPreference.importAllFromJson(enabledBackup)
        assertTrue(AppPreference.sumire_mode_key_highlight_preference)
    }

    @Test
    fun newAndLegacySettingsExposeTheSameSwitch() {
        val item = SettingSearchIndex.searchable(context)
            .first { it.key == AppPreference.SUMIRE_MODE_KEY_HIGHLIGHT_KEY }
        assertTrue(item.summary.isNotBlank())
        val setting = item.destination as SettingDestinationType.SwitchPreference
        assertEquals(R.id.sumirePreferenceFragment, setting.destinationId)
        assertFalse(setting.defaultValue)

        val legacy = SettingSearchIndex.legacySearchable(context)
            .first { it.key == AppPreference.SUMIRE_MODE_KEY_HIGHLIGHT_KEY }
        assertTrue(legacy.summary.isNotBlank())
        assertEquals(R.id.sumirePreferenceFragment,
            (legacy.destination as SettingDestinationType.NavDestination).destinationId)
        assertEquals(R.xml.pref_sumire, legacy.legacyTarget?.xmlRes)
        assertEquals(AppPreference.SUMIRE_MODE_KEY_HIGHLIGHT_KEY, legacy.legacyTarget?.preferenceKey)
    }
}
