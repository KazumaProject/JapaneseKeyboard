package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.content.res.Configuration
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.TfbiFlickRotation
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
class AppPreferenceSumireFlickRotationTest {
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
    fun missingAndUnknownPreferencesKeepTheCurrentLayout() {
        assertEquals(TfbiFlickRotation.DEFAULT, AppPreference.sumire_flick_rotation_preference)
        preferences.edit().putString(AppPreference.SUMIRE_FLICK_ROTATION_KEY, "unknown").commit()
        assertEquals(TfbiFlickRotation.DEFAULT, AppPreference.sumire_flick_rotation_preference)
        assertEquals("default", ImePreferencesSnapshot.from(AppPreference).tfbiFlickRotationPreferenceValue)
    }

    @Test
    fun everyRotationPersistsAndReachesTheImeSnapshot() {
        for (rotation in TfbiFlickRotation.entries) {
            AppPreference.sumire_flick_rotation_preference = rotation
            AppPreference.init(context)
            assertEquals(rotation, AppPreference.sumire_flick_rotation_preference)
            assertEquals(rotation.preferenceValue,
                preferences.getString(AppPreference.SUMIRE_FLICK_ROTATION_KEY, null))
            assertEquals(rotation.preferenceValue,
                ImePreferencesSnapshot.from(AppPreference).tfbiFlickRotationPreferenceValue)
        }
    }

    @Test
    fun backupRestoresRotationAndOlderBackupsKeepTheCurrentLayout() {
        val olderBackup = AppPreference.exportAllToJson()
        AppPreference.sumire_flick_rotation_preference = TfbiFlickRotation.COUNTERCLOCKWISE
        val rotatedBackup = AppPreference.exportAllToJson()
        AppPreference.importAllFromJson(olderBackup)
        assertEquals(TfbiFlickRotation.DEFAULT, AppPreference.sumire_flick_rotation_preference)
        AppPreference.importAllFromJson(rotatedBackup)
        assertEquals(TfbiFlickRotation.COUNTERCLOCKWISE, AppPreference.sumire_flick_rotation_preference)
    }

    @Test
    fun searchableSettingOffersTheSameValuesWithJapaneseAndEnglishLabels() {
        val item = SettingSearchIndex.searchable(context)
            .first { it.key == AppPreference.SUMIRE_FLICK_ROTATION_KEY }
        assertTrue(item.summary.isNotBlank())
        assertFalse("Search results must not expose summary placeholders", item.summary.contains("%"))
        val setting = item.destination as SettingDestinationType.ListPreference
        assertEquals(R.id.sumirePreferenceFragment, setting.destinationId)
        assertEquals("default", setting.defaultValue)
        assertEquals(listOf("default", "clockwise", "counterclockwise"),
            context.resources.getStringArray(setting.entryValuesResId).toList())

        val japanese = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.JAPANESE) }
        )
        val english = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        )
        assertEquals(listOf("標準（従来の配置）", "時計回り", "反時計回り"),
            japanese.resources.getStringArray(setting.entriesResId).toList())
        assertEquals(listOf("Default (current layout)", "Clockwise", "Counterclockwise"),
            english.resources.getStringArray(setting.entriesResId).toList())
    }
}
