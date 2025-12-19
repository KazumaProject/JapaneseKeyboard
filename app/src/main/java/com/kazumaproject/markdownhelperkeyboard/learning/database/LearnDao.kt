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
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(learnData: LearnEntity)

    @Query("SELECT out, score FROM learn_table WHERE input = :input")
    suspend fun findByInput(input: String): List<LearnResult>?

    @Query("SELECT * FROM learn_table WHERE input = :input AND out = :output LIMIT 1")
    suspend fun findByInputAndOutput(input: String, output: String): LearnEntity?

    @Query("SELECT * FROM learn_table ORDER BY score ASC")
    fun all(): Flow<List<LearnEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(learnDataList: List<LearnEntity>)

    /**
     * Predictive Search: Finds entries where the 'input' column starts with a given prefix.
     * This is useful for autocompleting user input.
     * The results are ordered by score in descending order to show the most likely candidates first.
     *
     * @param prefix The prefix string to search for.
     * @param limit The maximum number of results to return.
     * @return A list of matching LearnEntity objects.
     */
    @Query("SELECT * FROM learn_table WHERE input LIKE :prefix || '%' ORDER BY score DESC LIMIT :limit")
    suspend fun predictiveSearchByInput(prefix: String, limit: Int): List<LearnEntity>

    /**
     * Common Prefix Search: Finds entries where the 'input' value is a prefix of the given searchTerm.
     * For example, if the table contains an entry with input="he", and the searchTerm is "hello", this entry will be matched.
     * This is useful for finding the most specific matching command or keyword from user input.
     * Results are ordered by the length of the input string in descending order to prioritize longer (more specific) matches.
     *
     * @param searchTerm The full string to check against the stored prefixes.
     * @return A list of matching LearnEntity objects.
     */
    @Query("SELECT * FROM learn_table WHERE :searchTerm LIKE input || '%' ORDER BY LENGTH(input) DESC")
    suspend fun findCommonPrefixes(searchTerm: String): List<LearnEntity>

    @Update
    suspend fun updateLearnedData(learnData: LearnEntity)

    @Delete
    suspend fun delete(learnData: LearnEntity)

    @Query("DELETE FROM learn_table")
    suspend fun deleteAll()

    @Query("DELETE FROM learn_table WHERE input = :input")
    suspend fun deleteByInput(input: String): Int

    @Query("DELETE FROM learn_table WHERE input = :input AND out = :output")
    suspend fun deleteByInputAndOutput(input: String, output: String): Int

}
