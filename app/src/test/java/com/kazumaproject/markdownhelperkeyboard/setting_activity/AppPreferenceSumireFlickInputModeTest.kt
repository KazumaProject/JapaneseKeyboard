package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.content.res.Configuration
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.SumireFlickInputMode
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.ImePreferencesSnapshot
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingDestinationType
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
class AppPreferenceSumireFlickInputModeTest {
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
    fun missingAndUnknownPreferencesKeepAllExistingCharacters() {
        assertEquals(SumireFlickInputMode.ALL, AppPreference.sumire_flick_input_mode_preference)
        preferences.edit().putString(AppPreference.SUMIRE_FLICK_INPUT_MODE_KEY, "unknown").commit()
        assertEquals(SumireFlickInputMode.ALL, AppPreference.sumire_flick_input_mode_preference)
        assertEquals("all", ImePreferencesSnapshot.from(AppPreference).sumireFlickInputModePreferenceValue)
    }

    @Test
    fun everyInputModePersistsAndReachesTheImeSnapshot() {
        for (mode in SumireFlickInputMode.entries) {
            AppPreference.sumire_flick_input_mode_preference = mode
            AppPreference.init(context)
            assertEquals(mode, AppPreference.sumire_flick_input_mode_preference)
            assertEquals(mode.preferenceValue,
                preferences.getString(AppPreference.SUMIRE_FLICK_INPUT_MODE_KEY, null))
            assertEquals(mode.preferenceValue,
                ImePreferencesSnapshot.from(AppPreference).sumireFlickInputModePreferenceValue)
        }
    }

    @Test
    fun backupRestoresInputModeAndOlderBackupsKeepAllExistingCharacters() {
        val olderBackup = AppPreference.exportAllToJson()
        AppPreference.sumire_flick_input_mode_preference = SumireFlickInputMode.CONTRACTED
        val contractedBackup = AppPreference.exportAllToJson()
        AppPreference.importAllFromJson(olderBackup)
        assertEquals(SumireFlickInputMode.ALL, AppPreference.sumire_flick_input_mode_preference)
        AppPreference.importAllFromJson(contractedBackup)
        assertEquals(SumireFlickInputMode.CONTRACTED, AppPreference.sumire_flick_input_mode_preference)
    }

    @Test
    fun searchableSettingUsesDescriptiveJapaneseAndEnglishChoices() {
        val item = SettingSearchIndex.searchable(context)
            .first { it.key == AppPreference.SUMIRE_FLICK_INPUT_MODE_KEY }
        assertTrue(item.summary.isNotBlank())
        assertFalse("Search results must not expose summary placeholders", item.summary.contains("%"))
        val setting = item.destination as SettingDestinationType.ListPreference
        assertEquals(R.id.sumirePreferenceFragment, setting.destinationId)
        assertEquals("all", setting.defaultValue)
        assertEquals(listOf("contracted", "voiced", "all"),
            context.resources.getStringArray(setting.entryValuesResId).toList())
        val japanese = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.JAPANESE) }
        )
        val english = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        )
        assertEquals(listOf("拗音・拗長音", "濁音・半濁音", "すべて（標準）"),
            japanese.resources.getStringArray(setting.entriesResId).toList())
        assertEquals(listOf("Contracted sounds and long vowels", "Voiced and semi-voiced kana", "All (default)"),
            english.resources.getStringArray(setting.entriesResId).toList())
    }
}
