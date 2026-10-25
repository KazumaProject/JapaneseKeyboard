package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HierarchicalFlickMigrationTest {
    @Test fun migratedDatabasePassesRoomValidation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "hierarchical-room-validation-${System.nanoTime()}.db"
        try {
            val initial = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .allowMainThreadQueries().build()
            try {
                val db = initial.openHelper.writableDatabase
                db.execSQL("ALTER TABLE key_definitions DROP COLUMN hierarchicalFlickJson")
                db.execSQL("PRAGMA user_version = 48")
            } finally { initial.close() }
            val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(AppDatabase.MIGRATION_48_49).allowMainThreadQueries().build()
            try { assertEquals(49, migrated.openHelper.writableDatabase.version) }
            finally { migrated.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun migration48To49PreservesKeysWithNoHierarchy() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "hierarchical-migration-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(48) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE key_definitions (keyId INTEGER PRIMARY KEY NOT NULL, label TEXT NOT NULL)")
                        db.execSQL("INSERT INTO key_definitions VALUES (1, '既存キー')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build())
        try {
            val db = helper.writableDatabase
            AppDatabase.MIGRATION_48_49.migrate(db)
            db.query("SELECT label, hierarchicalFlickJson FROM key_definitions WHERE keyId = 1").use {
                assertTrue(it.moveToFirst())
                assertEquals("既存キー", it.getString(0))
                assertTrue(it.isNull(1))
            }
        } finally { helper.close(); context.deleteDatabase(name) }
    }
}
