package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ToggleInputMigrationTest {
    @Test
    fun migration45To46_defaultsExistingKeysToNormalInput() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "toggle-input-migration-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE key_definitions (keyId INTEGER PRIMARY KEY NOT NULL)"
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
            db.execSQL("INSERT INTO key_definitions VALUES (1)")

            AppDatabase.MIGRATION_45_46.migrate(db)

            db.query("SELECT textInputBehavior FROM key_definitions WHERE keyId = 1").use {
                it.moveToFirst()
                assertEquals("NORMAL", it.getString(0))
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }
}
