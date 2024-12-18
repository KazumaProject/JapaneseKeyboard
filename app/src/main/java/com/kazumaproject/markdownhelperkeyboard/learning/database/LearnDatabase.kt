package com.kazumaproject.markdownhelperkeyboard.learning.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [LearnEntity::class],
    version = 1,
    exportSchema = false
)
abstract class LearnDatabase : RoomDatabase() {
    abstract fun learnDao(): LearnDao
}