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

    @Query("SELECT COUNT(*) FROM learn_table WHERE input = :input AND out = :out")
    suspend fun exists(input: String, out: String): Int

    @Query("SELECT id FROM learn_table WHERE input = :input ORDER BY id LIMIT 1")
    suspend fun getFirstId(input: String): Int?

    @Query("UPDATE learn_table SET id = id - 1 WHERE input = :input AND out = :out")
    suspend fun moveOutToPrevious(input: String, out: String)

    @Query("DELETE FROM learn_table WHERE id < 0") // Clean up invalid IDs
    suspend fun cleanUpInvalidIds()
}