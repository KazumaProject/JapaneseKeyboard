package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.content.res.Configuration
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.ImePreferencesSnapshot
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingDestinationType
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingDestinations
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingSearchIndex
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPreferenceCustomDirectInputCompositionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences get() = PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun setUp() {
        clearPreferences()
        AppPreference.init(context)
    }

    @After
    fun clearPreferences() {
        preferences.edit().clear().commit()
    }

    @Test
    fun defaultsOffAndPersistsBothValues() {
        assertFalse(AppPreference.custom_direct_input_replace_composing_preference)
        assertFalse(ImePreferencesSnapshot.from(AppPreference).customDirectInputReplaceComposingPreference)
        AppPreference.custom_direct_input_replace_composing_preference = true
        AppPreference.init(context)
        assertTrue(AppPreference.custom_direct_input_replace_composing_preference)
        assertTrue(ImePreferencesSnapshot.from(AppPreference).customDirectInputReplaceComposingPreference)
        AppPreference.custom_direct_input_replace_composing_preference = false
        assertFalse(AppPreference.custom_direct_input_replace_composing_preference)
    }

    @Test
    fun backupRestoresValueAndOlderBackupsDefaultOff() {
        val oldBackup = AppPreference.exportAllToJson()
        AppPreference.custom_direct_input_replace_composing_preference = true
        val enabledBackup = AppPreference.exportAllToJson()
        AppPreference.importAllFromJson(oldBackup)
        assertFalse(AppPreference.custom_direct_input_replace_composing_preference)
        AppPreference.importAllFromJson(enabledBackup)
        assertTrue(AppPreference.custom_direct_input_replace_composing_preference)
    }

    @Test
    fun appearsInNewAndLegacyCustomSettingsWithDefaultOff() {
        val key = AppPreference.CUSTOM_DIRECT_INPUT_REPLACE_COMPOSING_KEY
        val newItem = SettingSearchIndex.searchable(context).first { it.key == key }
        assertFalse((newItem.destination as SettingDestinationType.SwitchPreference).defaultValue)
        for (items in listOf(SettingSearchIndex.searchable(context), SettingSearchIndex.legacySearchable(context))) {
            val item = items.first { it.key == key }
            assertEquals(R.id.customKeyboardPreferenceFragment, SettingDestinations.destinationId(item.destination))
            val destination = item.destination
            if (destination is SettingDestinationType.SwitchPreference) {
                assertFalse(destination.defaultValue)
            }
        }
    }

    @Test
    fun descriptionsExplainCommitAndDeferredReplacement() {
        val japanese = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.JAPANESE) }
        )
        assertEquals("直接入力で未確定文字を置き換える", japanese.getString(R.string.custom_direct_input_replace_composing_title))
        assertEquals("直接入力に切り替えると、未確定文字を確定します。", japanese.getString(R.string.custom_direct_input_replace_composing_summary_off))
        assertEquals("直接入力に切り替えた後、最初の入力で未確定文字を置き換えます。", japanese.getString(R.string.custom_direct_input_replace_composing_summary_on))
        assertTrue(context.getString(R.string.custom_direct_input_replace_composing_summary_off).contains("commits"))
        assertTrue(context.getString(R.string.custom_direct_input_replace_composing_summary_on).contains("first input"))
    }
}
