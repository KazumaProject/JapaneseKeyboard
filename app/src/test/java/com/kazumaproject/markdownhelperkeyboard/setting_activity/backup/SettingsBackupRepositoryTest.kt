package com.kazumaproject.markdownhelperkeyboard.setting_activity.backup

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import com.kazumaproject.markdownhelperkeyboard.repository.ShortcutRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsBackupRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var preferences: SharedPreferences
    private lateinit var shortcuts: ShortcutRepository
    private lateinit var backups: SettingsBackupRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit().clear().commit()
        AppPreference.init(context)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        shortcuts = ShortcutRepository(database.shortcutDao())
        backups = SettingsBackupRepository(shortcuts)
    }

    @After
    fun tearDown() {
        database.close()
        preferences.edit().clear().commit()
    }

    @Test
    fun roundTripRestoresPreferencesAndToolbarOrderIntoEmptyDatabase() = runTest {
        val selected = listOf(
            ShortcutType.PASTE,
            ShortcutType.SETTINGS,
            ShortcutType.LEARNING_PAUSE,
            ShortcutType.KEYBOARD_FLOATING_TOGGLE
        )
        shortcuts.updateShortcuts(selected)
        preferences.edit().putBoolean("backup_test", true).commit()

        val json = backups.exportAllToJson()
        val payload = Gson().fromJson(json, PrefBackup::class.java)
        assertEquals(2, payload.version)
        assertEquals(selected.map { it.id }, payload.shortcutIds)

        database.shortcutDao().deleteAll()
        preferences.edit().clear().putString("after_export", "remove me").commit()
        backups.importAllFromJson(json)
        shortcuts.initDefaultShortcutsIfNeeded()

        assertTrue(preferences.getBoolean("backup_test", false))
        assertFalse(preferences.contains("after_export"))
        assertEquals(selected, shortcuts.enabledShortcutsFlow.first())
        val restored = database.shortcutDao().getAllShortcuts()
        assertEquals(selected.map { it.id }, restored.map { it.typeId })
        assertEquals(selected.indices.toList(), restored.map { it.sortOrder })
    }

    @Test
    fun importReplacesExistingToolbarRatherThanAppending() = runTest {
        shortcuts.updateShortcuts(listOf(ShortcutType.COPY, ShortcutType.PASTE))
        val json = backups.exportAllToJson()
        shortcuts.updateShortcuts(listOf(ShortcutType.EMOJI, ShortcutType.SETTINGS))

        backups.importAllFromJson(json)

        assertEquals(listOf(ShortcutType.COPY, ShortcutType.PASTE), shortcuts.enabledShortcutsFlow.first())
        assertEquals(2, database.shortcutDao().getAllShortcuts().size)
    }

    @Test
    fun legacyBackupRestoresPreferencesAndKeepsCurrentToolbar() = runTest {
        preferences.edit().putBoolean("backup_test", true).commit()
        val legacyJson = AppPreference.exportAllToJson()
        assertEquals(1, Gson().fromJson(legacyJson, PrefBackup::class.java).version)
        val selected = listOf(ShortcutType.EMOJI, ShortcutType.COPY)
        shortcuts.updateShortcuts(selected)
        preferences.edit().putBoolean("backup_test", false).commit()

        backups.importAllFromJson(legacyJson)

        assertTrue(preferences.getBoolean("backup_test", false))
        assertEquals(selected, shortcuts.enabledShortcutsFlow.first())
    }

    @Test
    fun unknownAndDuplicateIdsAreSkippedWhileKnownOrderIsKept() = runTest {
        backups.importAllFromJson(
            """{"version":2,"entries":[],"shortcutIds":["paste","future_shortcut","copy","paste",null]}"""
        )

        assertEquals(listOf(ShortcutType.PASTE, ShortcutType.COPY), shortcuts.enabledShortcutsFlow.first())
        assertEquals(listOf(0, 1), database.shortcutDao().getAllShortcuts().map { it.sortOrder })
    }

    @Test
    fun emptySelectionRestoresSettingsFallbackAndDoesNotReinitializeDefaults() = runTest {
        shortcuts.updateShortcuts(listOf(ShortcutType.EMOJI))

        backups.importAllFromJson("""{"version":2,"entries":[],"shortcutIds":[]}""")
        shortcuts.initDefaultShortcutsIfNeeded()

        assertEquals(listOf(ShortcutType.SETTINGS), shortcuts.enabledShortcutsFlow.first())
        assertEquals(listOf("settings"), database.shortcutDao().getAllShortcuts().map { it.typeId })
    }

    @Test
    fun entirelyUnknownSelectionRestoresSettingsFallback() = runTest {
        backups.importAllFromJson(
            """{"version":2,"entries":[],"shortcutIds":["future_shortcut"]}"""
        )

        assertEquals(listOf(ShortcutType.SETTINGS), shortcuts.enabledShortcutsFlow.first())
    }

    @Test
    fun malformedToolbarDataFailsBeforePreferencesOrToolbarAreChanged() = runTest {
        preferences.edit().putBoolean("backup_test", true).commit()
        shortcuts.updateShortcuts(listOf(ShortcutType.COPY))

        var failed = false
        try {
            backups.importAllFromJson("""{"version":2,"entries":[],"shortcutIds":{}}""")
        } catch (_: IllegalArgumentException) {
            failed = true
        }

        assertTrue(failed)
        assertTrue(preferences.getBoolean("backup_test", false))
        assertEquals(listOf(ShortcutType.COPY), shortcuts.enabledShortcutsFlow.first())
    }

    @Test
    fun exportBeforeFirstImeLaunchIncludesDefaultsWithoutWritingToDatabase() = runTest {
        val json = backups.exportAllToJson()

        assertEquals(
            shortcuts.defaultShortcuts.map { it.id },
            Gson().fromJson(json, PrefBackup::class.java).shortcutIds
        )
        assertTrue(database.shortcutDao().getAllShortcuts().isEmpty())
    }
}
