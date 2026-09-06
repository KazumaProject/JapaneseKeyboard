package com.kazumaproject.markdownhelperkeyboard.ng_word.database

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
class NgWordMigrationTest {
    @Test
    fun migration45To46AddsPartialModeToExistingRows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "ng-word-migration-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(45) {
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

            AppDatabase.MIGRATION_45_46.migrate(db)

            db.query("SELECT matchMode FROM ng_word").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("PARTIAL", cursor.getString(0))
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }
}
