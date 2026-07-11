package com.kazumaproject.markdownhelperkeyboard.ngram_rule.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NgramRuleMigrationTest {
    @Test
    fun migration37To38_preservesTwoAndThreeNodeRules() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "ngram-migration-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) = createLegacyTables(db)
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        try {
            val db = helper.writableDatabase
            db.execSQL("INSERT INTO two_node_rule VALUES (1, '布', 1, 2, 'で', 3, 4, -300)")
            db.execSQL("INSERT INTO three_node_rule VALUES (1, '布', 1, 2, 'で', 3, 4, '拭く', 5, 6, -1200)")

            AppDatabase.MIGRATION_37_38.migrate(db)

            db.query("SELECT nodeCount, node1Word, node3Word, adjustment FROM ngram_rule ORDER BY nodeCount").use { cursor ->
                cursor.moveToFirst()
                assertEquals(2, cursor.getInt(0))
                assertEquals("布", cursor.getString(1))
                assertEquals("", cursor.getString(2))
                assertEquals(-300, cursor.getInt(3))
                cursor.moveToNext()
                assertEquals(3, cursor.getInt(0))
                assertEquals("拭く", cursor.getString(2))
                assertEquals(-1200, cursor.getInt(3))
            }
            assertFalse(tableExists(db, "two_node_rule"))
            assertFalse(tableExists(db, "three_node_rule"))
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    private fun createLegacyTables(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE two_node_rule (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, prevWord TEXT NOT NULL, prevLeftId INTEGER NOT NULL, prevRightId INTEGER NOT NULL, currentWord TEXT NOT NULL, currentLeftId INTEGER NOT NULL, currentRightId INTEGER NOT NULL, adjustment INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE three_node_rule (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, firstWord TEXT NOT NULL, firstLeftId INTEGER NOT NULL, firstRightId INTEGER NOT NULL, secondWord TEXT NOT NULL, secondLeftId INTEGER NOT NULL, secondRightId INTEGER NOT NULL, thirdWord TEXT NOT NULL, thirdLeftId INTEGER NOT NULL, thirdRightId INTEGER NOT NULL, adjustment INTEGER NOT NULL)")
    }

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean =
        db.query("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { it.moveToFirst() }
}
