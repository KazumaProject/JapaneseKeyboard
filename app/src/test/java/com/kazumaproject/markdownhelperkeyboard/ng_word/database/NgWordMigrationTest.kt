package com.kazumaproject.markdownhelperkeyboard.ng_word.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NgWordMigrationTest {
    @Test
    fun migration45To47PreservesBothTables() = verifyMigration(45)

    @Test
    fun migration46To47PreservesExistingTextInputBehavior() = verifyMigration(46)

    @Test
    fun migrated46DatabasePassesRoomSchemaValidation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "ng-word-room-validation-${System.nanoTime()}.db"
        try {
            // Build the unchanged tables from Room, then reconstruct the historical NG table.
            val initial = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .allowMainThreadQueries().build()
            try {
                val db = initial.openHelper.writableDatabase
                db.execSQL("DROP TABLE ng_word")
                db.execSQL("CREATE TABLE ng_word (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, yomi TEXT NOT NULL, tango TEXT NOT NULL)")
                db.execSQL("CREATE INDEX index_ng_word_yomi ON ng_word (yomi)")
                db.execSQL("INSERT INTO ng_word (yomi, tango) VALUES ('きょう', '今日')")
                db.execSQL("PRAGMA user_version = 46")
            } finally {
                initial.close()
            }
            val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(AppDatabase.MIGRATION_46_47)
                .allowMainThreadQueries().build()
            try {
                val db = migrated.openHelper.writableDatabase
                assertEquals(47, db.version)
                db.query("SELECT matchMode FROM ng_word").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("PARTIAL", cursor.getString(0))
                }
            } finally {
                migrated.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private fun verifyMigration(startVersion: Int) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "ng-word-migration-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(startVersion) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE ng_word (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                yomi TEXT NOT NULL,
                                tango TEXT NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL("CREATE TABLE key_definitions (keyId INTEGER PRIMARY KEY NOT NULL, label TEXT NOT NULL)")
                        if (startVersion == 46) {
                            db.execSQL("ALTER TABLE key_definitions ADD COLUMN textInputBehavior TEXT NOT NULL DEFAULT 'NORMAL'")
                        }
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                })
                .build()
        )
        try {
            val db = helper.writableDatabase
            db.execSQL("INSERT INTO ng_word (yomi, tango) VALUES ('きょう', '今日')")

            db.execSQL("INSERT INTO key_definitions (keyId, label) VALUES (1, 'test')")
            if (startVersion == 45) {
                AppDatabase.MIGRATION_45_46.migrate(db)
            } else {
                db.execSQL("UPDATE key_definitions SET textInputBehavior = 'TOGGLE'")
            }
            AppDatabase.MIGRATION_46_47.migrate(db)

            db.query("SELECT yomi, tango, matchMode FROM ng_word").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("きょう", cursor.getString(0))
                assertEquals("今日", cursor.getString(1))
                assertEquals("PARTIAL", cursor.getString(2))
            }
            db.query("SELECT label, textInputBehavior FROM key_definitions").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("test", cursor.getString(0))
                assertEquals(if (startVersion == 45) "NORMAL" else "TOGGLE", cursor.getString(1))
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }
}
