package com.kazumaproject.markdownhelperkeyboard.text_macro

import android.content.Context
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
class TextMacroMigrationTest {
    @Test
    fun migration44To45CreatesMacroTableWithoutChangingLegacyTemplates() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "text-macro-migration-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE user_template (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                word TEXT NOT NULL,
                                reading TEXT NOT NULL,
                                posIndex INTEGER NOT NULL,
                                posScore INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
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
            db.execSQL(
                "INSERT INTO user_template (word, reading, posIndex, posScore) VALUES ('{date}', 'date', 0, 3000)"
            )

            AppDatabase.MIGRATION_44_45.migrate(db)

            db.query("SELECT word, reading FROM user_template").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("{date}", cursor.getString(0))
                assertEquals("date", cursor.getString(1))
            }
            db.execSQL(
                "INSERT INTO text_macro (name, reading, body, enabled) VALUES ('Today', 'today', '{date}', 1)"
            )
            db.query("SELECT name, reading, body, enabled FROM text_macro").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Today", cursor.getString(0))
                assertEquals("today", cursor.getString(1))
                assertEquals("{date}", cursor.getString(2))
                assertEquals(1, cursor.getInt(3))
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }
}

