package com.kazumaproject.markdownhelperkeyboard.learning.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("SELECT EXISTS(SELECT 1 FROM learn_table WHERE input = :input AND out = :output AND id != :excludeId)")
    suspend fun existsDuplicateForUpdate(input: String, output: String, excludeId: Int): Boolean

    @Query("SELECT * FROM learn_table ORDER BY score ASC")
    fun all(): Flow<List<LearnEntity>>

    @Query("SELECT * FROM learn_table ORDER BY score ASC")
    suspend fun getAllSuspend(): List<LearnEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(learnDataList: List<LearnEntity>)

    /**
     * Predictive Search: Finds entries where the 'input' column starts with a given prefix.
     * This is useful for autocompleting user input.
     * Lower scores are preferred by the conversion engine, so the most likely candidates are
     * selected before applying the limit.
     *
     * @param prefix The prefix string to search for.
     * @param limit The maximum number of results to return.
     * @return A list of matching LearnEntity objects.
     */
    @Query(
        """
        SELECT * FROM learn_table
        WHERE input >= :prefix AND input < :prefixUpperBound
        ORDER BY CASE WHEN input = :prefix THEN 0 ELSE 1 END,
                 score ASC,
                 lastUsedAt DESC,
                 LENGTH(input) ASC,
                 id DESC
        LIMIT :limit
        """
    )
    suspend fun predictiveSearchByInput(
        prefix: String,
        prefixUpperBound: String,
        limit: Int,
    ): List<LearnEntity>

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

    @Query(
        """
        SELECT * FROM learn_table
        WHERE input >= :prefix AND input < :prefixUpperBound
        ORDER BY LENGTH(input) DESC
        """
    )
    suspend fun findByInputPrefix(
        prefix: String,
        prefixUpperBound: String,
    ): List<LearnEntity>

    @Update
    suspend fun updateLearnedData(learnData: LearnEntity)

    /**
     * A conversion can produce both segment entries and one complete phrase entry. Room generates
     * the transaction boundary for this default DAO method, so readers never observe a partial
     * learning session.
     */
    @Transaction
    suspend fun upsertLearningEntries(learnDataList: List<LearnEntity>) {
        learnDataList.forEach { incoming ->
            val existing = findByInputAndOutput(incoming.input, incoming.out)
            if (existing == null) {
                insert(incoming)
            } else {
                updateLearnedData(
                    existing.copy(
                        score = LearningScorePolicy.reinforce(
                            existingScore = existing.score,
                            incomingScore = incoming.score,
                        ),
                        leftId = incoming.leftId ?: existing.leftId,
                        rightId = incoming.rightId ?: existing.rightId,
                        usageCount = (
                            existing.usageCount.toLong() +
                                incoming.usageCount.coerceAtLeast(1).toLong()
                            ).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        lastUsedAt = maxOf(existing.lastUsedAt, incoming.lastUsedAt),
                        isPhrase = existing.isPhrase || incoming.isPhrase,
                    )
                )
            }
        }
    }

    @Delete
    suspend fun delete(learnData: LearnEntity)

    @Query("DELETE FROM learn_table")
    suspend fun deleteAll()

    @Query("DELETE FROM learn_table WHERE input = :input")
    suspend fun deleteByInput(input: String): Int

    @Query("DELETE FROM learn_table WHERE input = :input AND out = :output")
    suspend fun deleteByInputAndOutput(input: String, output: String): Int

}
