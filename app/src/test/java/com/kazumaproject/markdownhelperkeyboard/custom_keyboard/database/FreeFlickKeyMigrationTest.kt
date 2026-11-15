package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.KeyDefinition
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FreeFlickKeyMigrationTest {
    @Test fun removesRetiredColumnAndPreservesEveryMappingTableAndKeyIdentity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "free-flick-migration-${System.nanoTime()}.db"
        try {
            val initial = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
            val tables = listOf("flick_mappings", "circular_flick_mappings", "two_step_flick_mappings",
                "long_press_flick_mappings", "two_step_long_press_mappings")
            try {
                val db = initial.openHelper.writableDatabase
                db.execSQL("INSERT INTO keyboard_layouts (layoutId, name, columnCount, rowCount, isRomaji, isDirectMode, createdAt, sortOrder, stableId, isFlexiblePlacementLayout, usageMode) VALUES (1, 'Original', 2, 1, 0, 0, 0, 0, 'original', 0, 'Normal')")
                runBlocking { initial.keyboardLayoutDao().insertKeys(listOf(
                    KeyDefinition(keyId = 10, ownerLayoutId = 1, label = "Retired", row = 0, column = 0,
                        keyType = KeyType.NORMAL, keyIdentifier = "old"),
                    KeyDefinition(keyId = 20, ownerLayoutId = 1, label = "Existing", row = 0, column = 1,
                        keyType = KeyType.TWO_STEP_FLICK, keyIdentifier = "existing"))) }
                db.execSQL("INSERT INTO flick_mappings (ownerKeyId, stateIndex, flickDirection, actionType, actionValue) VALUES (20, 0, 'TAP', 'INPUT_TEXT', 'a')")
                db.execSQL("INSERT INTO circular_flick_mappings (ownerKeyId, stateIndex, circularDirection, actionType, actionValue) VALUES (20, 0, 'TAP', 'INPUT_TEXT', 'b')")
                db.execSQL("INSERT INTO two_step_flick_mappings VALUES (20, 'LEFT', 'UP', 'c')")
                db.execSQL("INSERT INTO long_press_flick_mappings VALUES (20, 'UP', 'd')")
                db.execSQL("INSERT INTO two_step_long_press_mappings VALUES (20, 'LEFT', 'UP', 'e')")
                db.execSQL("ALTER TABLE key_definitions DROP COLUMN popupMapConfigJson")
                db.execSQL("ALTER TABLE key_definitions ADD COLUMN hierarchicalFlickJson TEXT")
                db.execSQL("UPDATE key_definitions SET keyType = 'HIERARCHICAL_FLICK', hierarchicalFlickJson = '{\"retired\":true}' WHERE keyId = 10")
                db.execSQL("PRAGMA user_version = 50")
            } finally { initial.close() }
            val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(AppDatabase.MIGRATION_50_51).allowMainThreadQueries().build()
            try {
                val db = migrated.openHelper.writableDatabase
                assertEquals(51, db.version)
                db.query("SELECT keyId, keyIdentifier, keyType, popupMapConfigJson FROM key_definitions ORDER BY keyId").use {
                    assertTrue(it.moveToFirst()); assertEquals(10, it.getInt(0)); assertEquals("old", it.getString(1))
                    assertEquals("NORMAL", it.getString(2)); assertTrue(it.isNull(3))
                    assertTrue(it.moveToNext()); assertEquals(20, it.getInt(0)); assertEquals("TWO_STEP_FLICK", it.getString(2))
                }
                tables.forEach { table -> db.query("SELECT ownerKeyId FROM `$table`").use {
                    assertTrue("$table retained", it.moveToFirst()); assertEquals(20, it.getInt(0))
                } }
                db.query("PRAGMA table_info(key_definitions)").use {
                    while (it.moveToNext()) assertNotEquals("hierarchicalFlickJson", it.getString(1))
                }
                db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                // AUTOINCREMENT must still advance beyond the preserved IDs.
                runBlocking {
                    assertTrue(migrated.keyboardLayoutDao().insertKeys(listOf(KeyDefinition(ownerLayoutId = 1,
                        label = "New", row = 0, column = 0, keyType = KeyType.NORMAL, keyIdentifier = "new"))).single() > 20)
                }
            } finally { migrated.close() }
        } finally { context.deleteDatabase(name) }
    }
}
