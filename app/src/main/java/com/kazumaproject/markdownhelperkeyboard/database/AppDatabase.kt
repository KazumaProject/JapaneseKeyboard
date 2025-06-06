package com.kazumaproject.markdownhelperkeyboard.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kazumaproject.markdownhelperkeyboard.clicked_symbol.database.ClickedSymbol
import com.kazumaproject.markdownhelperkeyboard.clicked_symbol.database.ClickedSymbolDao
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnDao
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity

@Database(
    entities = [
        LearnEntity::class,
        ClickedSymbol::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun learnDao(): LearnDao
    abstract fun clickedSymbolDao(): ClickedSymbolDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // clicked_symbol_history テーブルを追加
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
    }
}
