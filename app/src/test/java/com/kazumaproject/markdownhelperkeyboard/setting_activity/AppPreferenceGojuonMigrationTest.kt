package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPreferenceGojuonMigrationTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    @Config(qualifiers = "sw600dp")
    fun legacyTabletSettingOnMigratesOrderAndSelectedPositionOnce() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit()
            .clear()
            .putString("keyboard_order_preference", "[\"QWERTY\",\"TENKEY\",\"SUMIRE\"]")
            .putBoolean("tablet_use_gojuon_layout_preference", true)
            .putInt("save_last_used_keyboard_int", 1)
            .commit()

        AppPreference.init(context)

        assertEquals(
            listOf(KeyboardType.QWERTY, KeyboardType.GOJUON, KeyboardType.SUMIRE),
            AppPreference.keyboard_order,
        )
        assertEquals(1, AppPreference.save_last_used_keyboard_position_preference)
        assertTrue(preferences.getBoolean(AppPreference.GOJUON_KEYBOARD_TYPE_MIGRATION_KEY, false))
        assertFalse(preferences.contains("tablet_use_gojuon_layout_preference"))

        AppPreference.migrateGojuonKeyboardTypeIfNeeded(context)
        assertEquals(
            listOf(KeyboardType.QWERTY, KeyboardType.GOJUON, KeyboardType.SUMIRE),
            AppPreference.keyboard_order,
        )
        assertEquals(1, AppPreference.save_last_used_keyboard_position_preference)
    }

    @Test
    @Config(qualifiers = "sw600dp")
    fun legacyTabletSettingOffKeepsTenkey() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit()
            .clear()
            .putString("keyboard_order_preference", "[\"TENKEY\",\"QWERTY\"]")
            .putBoolean("tablet_use_gojuon_layout_preference", false)
            .commit()

        AppPreference.init(context)

        assertEquals(
            listOf(KeyboardType.TENKEY, KeyboardType.QWERTY),
            AppPreference.keyboard_order,
        )
    }

    @Test
    fun phoneDoesNotMarkTabletMigrationComplete() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit()
            .clear()
            .putBoolean(AppPreference.GOJUON_KEYBOARD_TYPE_MIGRATION_KEY, true)
            .commit()

        AppPreference.init(context)

        assertEquals(
            listOf(KeyboardType.TENKEY, KeyboardType.QWERTY),
            AppPreference.keyboard_order,
        )
        assertFalse(preferences.getBoolean(AppPreference.GOJUON_KEYBOARD_TYPE_MIGRATION_KEY, false))
    }

    @Test
    @Config(qualifiers = "sw600dp")
    fun legacyOrderWithoutTenkeyDoesNotGainGojuon() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit()
            .clear()
            .putString("keyboard_order_preference", "[\"QWERTY\",\"SUMIRE\"]")
            .putBoolean("tablet_use_gojuon_layout_preference", true)
            .commit()

        AppPreference.init(context)

        assertEquals(
            listOf(KeyboardType.QWERTY, KeyboardType.SUMIRE),
            AppPreference.keyboard_order,
        )
    }

    @Test
    @Config(qualifiers = "sw600dp")
    fun emptyLegacyOrderRemainsEmpty() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit()
            .clear()
            .putString("keyboard_order_preference", "[]")
            .putBoolean("tablet_use_gojuon_layout_preference", true)
            .commit()

        AppPreference.init(context)

        assertEquals(emptyList<KeyboardType>(), AppPreference.keyboard_order)
    }

    @Test
    @Config(qualifiers = "sw600dp")
    fun importingLegacyPhoneBackupRunsTabletMigration() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit().clear().commit()
        AppPreference.init(context)

        val backup = """
            {
              "version": 1,
              "entries": [
                {
                  "key": "keyboard_order_preference",
                  "type": "string",
                  "value": "[\"TENKEY\",\"QWERTY\"]"
                },
                {
                  "key": "tablet_use_gojuon_layout_preference",
                  "type": "boolean",
                  "value": true
                },
                {
                  "key": "save_last_used_keyboard_int",
                  "type": "int",
                  "value": 0
                }
              ]
            }
        """.trimIndent()

        AppPreference.importAllFromJson(backup)

        assertEquals(
            listOf(KeyboardType.GOJUON, KeyboardType.QWERTY),
            AppPreference.keyboard_order,
        )
        assertEquals(0, AppPreference.save_last_used_keyboard_position_preference)
        assertTrue(preferences.getBoolean(AppPreference.GOJUON_KEYBOARD_TYPE_MIGRATION_KEY, false))
    }
}
