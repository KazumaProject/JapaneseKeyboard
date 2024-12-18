package com.kazumaproject.markdownhelperkeyboard.learning.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LearnDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(learnEntity: LearnEntity)
    @Query("SELECT out FROM learn_table WHERE input = :input")
    suspend fun getByInput(input: String): List<String>
}