package com.kazumaproject.markdownhelperkeyboard.setting_activity.keyboard_size

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.preference.PreferenceManager
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IndependentKeyboardSizePreferencesTest {
    @Test fun migrationSnapshotsEachOrientationAndKeepsTheNewSlotsIndependent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("independent_sizes", Context.MODE_PRIVATE)
        prefs.edit().clear()
            .putInt("keyboard_height_preference", 251)
            .putInt("keyboard_width_preference", 71)
            .putInt("keyboard_vertical_margin_bottom_preference", 73)
            .putInt("keyboard_margin_start_dp_preference", 12)
            .putInt("keyboard_margin_end_dp_preference", 13)
            .putBoolean("keyboard_position_preference", false)
            .putInt("keyboard_height_landscape_preference", 181)
            .putInt("keyboard_width_landscape_preference", 83)
            .commit()
        IndependentKeyboardSizePreferences.migrate(prefs)
        val store = IndependentKeyboardSizePreferences(prefs)
        val portrait = IndependentKeyboardSize(251, 71, 73, 12, 13, false)
        val landscape = IndependentKeyboardSize(181, 83, 0, 0, 0, true)
        for (target in IndependentKeyboardSizeTarget.entries) {
            assertEquals(portrait, store.read(target, false))
            assertEquals(landscape, store.read(target, true))
        }
        prefs.edit().putInt("keyboard_height_preference", 300).commit()
        val custom = portrait.copy(heightDp = 155)
        store.save(IndependentKeyboardSizeTarget.CUSTOM, false, custom)
        IndependentKeyboardSizePreferences.migrate(prefs)
        assertEquals(custom, store.read(IndependentKeyboardSizeTarget.CUSTOM, false))
        assertEquals(portrait, store.read(IndependentKeyboardSizeTarget.GOJUON, false))
        assertEquals(landscape, store.read(IndependentKeyboardSizeTarget.CUSTOM, true))
    }

    @Test fun migrationDoesNotOverwriteAnExistingIndependentValue() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("independent_sizes_partial", Context.MODE_PRIVATE)
        prefs.edit().clear().putInt("keyboard_height_preference", 240)
            .putInt("independent_keyboard_size_gojuon_portrait_height", 310).commit()
        IndependentKeyboardSizePreferences.migrate(prefs)
        val store = IndependentKeyboardSizePreferences(prefs)
        assertEquals(310, store.read(IndependentKeyboardSizeTarget.GOJUON, false).heightDp)
        assertEquals(240, store.read(IndependentKeyboardSizeTarget.CUSTOM, false).heightDp)
        assertEquals(100, store.read(IndependentKeyboardSizeTarget.CUSTOM, false).widthPercent)
    }

    @Test fun restoringAnOldBackupMigratesItAndNewBackupsKeepIndependentSizes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppPreference.init(context)
        val original = AppPreference.exportAllToJson()
        val store = IndependentKeyboardSizePreferences(PreferenceManager.getDefaultSharedPreferences(context))
        try {
            AppPreference.importAllFromJson("""{"version":1,"entries":[
                {"key":"keyboard_height_preference","type":"int","value":246},
                {"key":"keyboard_vertical_margin_bottom_preference","type":"int","value":73},
                {"key":"keyboard_height_landscape_preference","type":"int","value":177}
            ]}""")
            assertEquals(246, store.read(IndependentKeyboardSizeTarget.GOJUON, false).heightDp)
            assertEquals(73, store.read(IndependentKeyboardSizeTarget.CUSTOM, false).bottomMarginPx)
            assertEquals(177, store.read(IndependentKeyboardSizeTarget.GOJUON, true).heightDp)
            val custom = store.read(IndependentKeyboardSizeTarget.CUSTOM, false).copy(heightDp = 310)
            store.save(IndependentKeyboardSizeTarget.CUSTOM, false, custom)
            val backup = AppPreference.exportAllToJson()
            store.save(IndependentKeyboardSizeTarget.CUSTOM, false, custom.copy(heightDp = 100))
            AppPreference.importAllFromJson(backup)
            assertEquals(custom, store.read(IndependentKeyboardSizeTarget.CUSTOM, false))
            assertEquals(246, store.read(IndependentKeyboardSizeTarget.GOJUON, false).heightDp)
        } finally {
            AppPreference.importAllFromJson(original)
        }
    }
}
