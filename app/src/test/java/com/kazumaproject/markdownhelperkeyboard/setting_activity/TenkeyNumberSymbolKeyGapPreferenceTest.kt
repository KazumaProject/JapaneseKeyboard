package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.ime_service.ImePreferencesSnapshot
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingCategory
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingDestinationType
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingSearchIndex
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TenkeyNumberSymbolKeyGapPreferenceTest {
    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun gapPreferenceDefaultsToFourDpAndPersistsWithinSliderRange() {
        assertEquals(4, AppPreference.tenkey_number_symbol_key_gap_preference)

        AppPreference.tenkey_number_symbol_key_gap_preference = 9
        assertEquals(9, ImePreferencesSnapshot.from(AppPreference).tenkeyNumberSymbolKeyGapDp)

        AppPreference.tenkey_number_symbol_key_gap_preference = -1
        assertEquals(0, AppPreference.tenkey_number_symbol_key_gap_preference)

        AppPreference.tenkey_number_symbol_key_gap_preference = 24
        assertEquals(16, AppPreference.tenkey_number_symbol_key_gap_preference)
    }

    @Test
    fun gapSliderIsSearchableInTheTenkeySettingsCategory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val setting = SettingSearchIndex.searchable(context).first {
            it.key == "tenkey_number_symbol_key_gap_preference"
        }

        assertEquals(SettingCategory.INPUT_METHOD, setting.category)
        val slider = setting.destination as SettingDestinationType.SeekBarPreference
        assertEquals(0, slider.min)
        assertEquals(16, slider.max)
        assertEquals(4, slider.defaultValue)
    }
}
