package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DoubleTapMigrationTest {
    @Test
    fun migration41To42_addsColumnsAndConfiguresExistingShiftKeys() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "double-tap-migration-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE key_definitions (keyId INTEGER PRIMARY KEY NOT NULL, action TEXT)"
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )
        try {
            val db = helper.writableDatabase
            db.execSQL("INSERT INTO key_definitions VALUES (1, 'ShiftKeyPressed')")
            db.execSQL("INSERT INTO key_definitions VALUES (2, 'Delete')")

            AppDatabase.MIGRATION_41_42.migrate(db)

            db.query(
                "SELECT doubleTapAction, doubleTapPolicy FROM key_definitions WHERE keyId = 1"
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("CapLockKey", cursor.getString(0))
                assertEquals("PROMOTE", cursor.getString(1))
            }
            db.query(
                "SELECT doubleTapAction, doubleTapPolicy FROM key_definitions WHERE keyId = 2"
            ).use { cursor ->
                cursor.moveToFirst()
                assertNull(cursor.getString(0))
                assertNull(cursor.getString(1))
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration42To43_removesNormalKeyBindingsAndNormalizesSpecialKeyPolicies() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "double-tap-capability-migration-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE key_definitions (
                                keyId INTEGER PRIMARY KEY NOT NULL,
                                isSpecialKey INTEGER NOT NULL,
                                action TEXT,
                                doubleTapAction TEXT,
                                doubleTapPolicy TEXT
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )
        try {
            val db = helper.writableDatabase
            db.execSQL("INSERT INTO key_definitions VALUES (1, 0, 'Text:a', 'Copy', 'PROMOTE')")
            db.execSQL(
                "INSERT INTO key_definitions VALUES (2, 1, 'ShiftKeyPressed', 'CapLockKey', 'EXCLUSIVE')"
            )
            db.execSQL("INSERT INTO key_definitions VALUES (3, 1, 'SelectAll', 'Copy', 'PROMOTE')")
            db.execSQL("INSERT INTO key_definitions VALUES (4, 1, 'Delete', NULL, 'PROMOTE')")

            AppDatabase.MIGRATION_42_43.migrate(db)

            db.query(
                "SELECT doubleTapAction, doubleTapPolicy FROM key_definitions ORDER BY keyId"
            ).use { cursor ->
                cursor.moveToNext()
                assertNull(cursor.getString(0))
                assertNull(cursor.getString(1))

                cursor.moveToNext()
                assertEquals("CapLockKey", cursor.getString(0))
                assertEquals("PROMOTE", cursor.getString(1))

                cursor.moveToNext()
                assertEquals("Copy", cursor.getString(0))
                assertEquals("EXCLUSIVE", cursor.getString(1))

                cursor.moveToNext()
                assertNull(cursor.getString(0))
                assertNull(cursor.getString(1))
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }
}
