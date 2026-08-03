package com.kazumaproject.markdownhelperkeyboard.candidate_order.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CandidateOrderOverrideDao {

    @Query(
        """
        SELECT * FROM candidate_order_override
        WHERE input = :input AND scope = :scope
        ORDER BY rank ASC
        """
    )
    suspend fun findByRule(input: String, scope: String): List<CandidateOrderOverrideEntity>

    @Query(
        """
        SELECT * FROM candidate_order_override
        WHERE input IN (:inputs)
        ORDER BY input ASC, scope ASC, rank ASC
        """
    )
    suspend fun findByInputs(inputs: List<String>): List<CandidateOrderOverrideEntity>

    @Query(
        """
        SELECT * FROM candidate_order_override
        ORDER BY input ASC, scope ASC, rank ASC
        """
    )
    fun observeAll(): Flow<List<CandidateOrderOverrideEntity>>

    @Query(
        """
        SELECT * FROM candidate_order_override
        ORDER BY input ASC, scope ASC, rank ASC
        """
    )
    suspend fun getAll(): List<CandidateOrderOverrideEntity>

    @Query(
        """
        DELETE FROM candidate_order_override
        WHERE input = :input
        """
    )
    suspend fun deleteByInput(input: String)

    @Query(
        """
        DELETE FROM candidate_order_override
        WHERE input = :input AND scope = :scope
        """
    )
    suspend fun deleteByRule(input: String, scope: String)

    @Query("DELETE FROM candidate_order_override")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CandidateOrderOverrideEntity>)

    @Transaction
    suspend fun replaceForRule(
        input: String,
        scope: String,
        entities: List<CandidateOrderOverrideEntity>,
    ) {
        deleteByRule(input, scope)
        insertAll(entities)
    }

    @Query(
        """
        DELETE FROM candidate_order_override
        WHERE id = :id
        """
    )
    suspend fun deleteById(id: Int)
}
