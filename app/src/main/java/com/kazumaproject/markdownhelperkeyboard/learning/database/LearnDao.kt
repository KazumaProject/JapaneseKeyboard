package com.kazumaproject.markdownhelperkeyboard.learning.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kazumaproject.markdownhelperkeyboard.learning.model.LearnResult
import kotlinx.coroutines.flow.Flow

@Dao
interface LearnDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(learnData: LearnEntity)

    @Query("SELECT out, score FROM learn_table WHERE input = :input")
    suspend fun findByInput(input: String): List<LearnResult>?

    @Query("SELECT * FROM learn_table WHERE input = :input AND out = :output LIMIT 1")
    suspend fun findByInputAndOutput(input: String, output: String): LearnEntity?

    @Query("SELECT * FROM learn_table ORDER BY score ASC")
    fun all(): Flow<List<LearnEntity>>

    @Update
    suspend fun updateLearnedData(learnData: LearnEntity)

    @Delete
    suspend fun delete(learnData: LearnEntity)

}