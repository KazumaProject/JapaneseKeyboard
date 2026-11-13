package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingDestinationType
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingDestinations
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingSearchIndex
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CustomQwertyPopupPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    @Before fun clear() {
        AppPreference.init(context)
        preferences.edit().clear().commit()
    }

    @Test fun migrationSnapshotsSharedSettingsAndBothSurfacesRemainIndependent() {
        preferences.edit()
            .putInt("qwerty_key_preview_popup_size_scale_percent_preference", 125)
            .putFloat("qwerty_key_preview_popup_text_size_sp_preference", 23f)
            .putInt("qwerty_variation_popup_size_scale_percent_preference", 160)
            .putFloat("qwerty_variation_popup_text_size_sp_preference", 35f).commit()
        AppPreference.init(context)
        val custom = AppPreference.custom_qwerty_popup_preferences
        assertEquals(PopupViewStyle(125, 23f), custom.keyPreview)
        assertEquals(PopupViewStyle(160, 35f), custom.variation)
        AppPreference.qwerty_key_preview_popup_size_scale_percent = 200
        AppPreference.qwerty_variation_popup_text_size_sp = 48f
        CustomQwertyPopupPreferences.migrate(preferences)
        assertEquals(PopupViewStyle(125, 23f), custom.keyPreview)
        assertEquals(PopupViewStyle(160, 35f), custom.variation)
        custom.keyPreview = PopupViewStyle(80, 18f)
        assertEquals(PopupViewStyle(160, 35f), custom.variation)
        assertEquals(200, AppPreference.qwerty_key_preview_popup_size_scale_percent)
        custom.variation = PopupViewStyle(140, 32f)
        assertEquals(48f, AppPreference.qwerty_variation_popup_text_size_sp)
        assertEquals(PopupViewStyle(80, 18f), CustomQwertyPopupPreferences(preferences).keyPreview)
    }

    @Test fun defaultsBoundsAndPartialMigrationPreserveExistingCustomValues() {
        val custom = CustomQwertyPopupPreferences(preferences)
        assertEquals(PopupViewStyle(100, 28f), custom.keyPreview)
        assertEquals(PopupViewStyle(100, 28f), custom.variation)
        custom.keyPreview = PopupViewStyle(1, 99f)
        custom.variation = PopupViewStyle(999, 1f)
        assertEquals(PopupViewStyle(50, 48f), custom.keyPreview)
        assertEquals(PopupViewStyle(200, 8f), custom.variation)
        preferences.edit().remove(CustomQwertyPopupPreferences.VARIATION_SIZE)
            .putInt("qwerty_variation_popup_size_scale_percent_preference", 120).commit()
        CustomQwertyPopupPreferences.migrate(preferences)
        assertEquals(PopupViewStyle(50, 48f), custom.keyPreview)
        assertEquals(PopupViewStyle(120, 8f), custom.variation)
    }

    @Test fun backupRoundTripAndLegacyRestoreKeepTheIndependentSettings() {
        AppPreference.init(context)
        val custom = AppPreference.custom_qwerty_popup_preferences
        custom.keyPreview = PopupViewStyle(80, 18f)
        custom.variation = PopupViewStyle(140, 32f)
        val backup = AppPreference.exportAllToJson()
        AppPreference.importAllFromJson(backup)
        assertEquals(PopupViewStyle(80, 18f), custom.keyPreview)
        assertEquals(PopupViewStyle(140, 32f), custom.variation)
        AppPreference.importAllFromJson("""{"version":1,"entries":[{"key":"qwerty_key_preview_popup_size_scale_percent_preference","type":"int","value":150}]}""")
        assertEquals(PopupViewStyle(150, 28f), custom.keyPreview)
        assertEquals(PopupViewStyle(100, 28f), custom.variation)
    }

    @Test fun bothSettingsInterfacesExposeCustomPopupSettings() {
        AppPreference.init(context)
        val modern = SettingSearchIndex.searchable(context).single { it.key == "custom_qwerty_popup_style" }
        assertTrue(modern.destination is SettingDestinationType.NavDestination)
        assertEquals(R.id.customQwertyPopupStyleSettingFragment, SettingDestinations.destinationId(modern.destination))
        val legacy = SettingSearchIndex.legacySearchable(context).single { it.key == "custom_qwerty_popup_style" }
        assertEquals(R.xml.pref_custom, legacy.legacyTarget!!.xmlRes)
        assertEquals(R.id.customKeyboardPreferenceFragment, legacy.legacyTarget!!.destinationId)
    }
}
