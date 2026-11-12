package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.KeyDefinition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QwertyKeyMigrationTest {
    @Test fun existingKeysRemainAndMigratedSchemaPassesRoomValidation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "qwerty-migration-${System.nanoTime()}.db"
        try {
            val initial = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
            try {
                val db = initial.openHelper.writableDatabase
                db.execSQL("INSERT INTO keyboard_layouts (layoutId, name, columnCount, rowCount, isRomaji, isDirectMode, createdAt, sortOrder, stableId, isFlexiblePlacementLayout, usageMode) VALUES (1, 'Original', 1, 1, 0, 0, 0, 0, 'original', 0, 'Normal')")
                runBlocking {
                    initial.keyboardLayoutDao().insertKeys(listOf(KeyDefinition(ownerLayoutId = 1,
                        label = "Existing", row = 0, column = 0, keyType = KeyType.NORMAL,
                        keyIdentifier = "existing")))
                }
                db.execSQL("ALTER TABLE key_definitions DROP COLUMN qwertyConfigJson")
                db.execSQL("PRAGMA user_version = 49")
            } finally { initial.close() }
            val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(AppDatabase.MIGRATION_49_50).allowMainThreadQueries().build()
            try {
                val db = migrated.openHelper.writableDatabase
                assertEquals(50, db.version)
                db.query("SELECT name FROM keyboard_layouts WHERE layoutId = 1").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("Original", it.getString(0))
                }
                db.query("SELECT label, keyType, qwertyConfigJson FROM key_definitions").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("Existing", it.getString(0))
                    assertEquals("NORMAL", it.getString(1))
                    assertTrue(it.isNull(2))
                }
            } finally { migrated.close() }
        } finally { context.deleteDatabase(name) }
    }
}
