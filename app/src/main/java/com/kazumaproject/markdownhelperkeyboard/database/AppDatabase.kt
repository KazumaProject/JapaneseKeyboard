package com.kazumaproject.markdownhelperkeyboard.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kazumaproject.data.clicked_symbol.ClickedSymbol
import com.kazumaproject.markdownhelperkeyboard.clicked_symbol.database.ClickedSymbolDao
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnDao
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWordDao

@Database(
    entities = [LearnEntity::class, ClickedSymbol::class, UserWord::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun learnDao(): LearnDao
    abstract fun clickedSymbolDao(): ClickedSymbolDao
    abstract fun userWordDao(): UserWordDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `clicked_symbol_history` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `mode` TEXT NOT NULL,
                      `symbol` TEXT NOT NULL,
                      `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_word` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `word` TEXT NOT NULL,
                      `reading` TEXT NOT NULL,
                      `posIndex` INTEGER NOT NULL,
                      `posScore` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * The new migration from version 3 to 4.
         * This migration adds an index to the `reading` column of the `user_word` table
         * to significantly improve query performance for prefix searches.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQL command to create an index on the 'reading' column
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_word_reading` ON `user_word`(`reading`)")
            }
        }

    }
}
