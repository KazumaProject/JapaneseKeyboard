package com.kazumaproject.markdownhelperkeyboard.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kazumaproject.markdownhelperkeyboard.db.models.EnglishWord

@Database(
    entities = [EnglishWord::class],
    version = 6,
    exportSchema = false
)
abstract class EnglishWordDatabase: RoomDatabase() {
    abstract fun dictionaryEnglishWordDao(): EnglishWordDao
}