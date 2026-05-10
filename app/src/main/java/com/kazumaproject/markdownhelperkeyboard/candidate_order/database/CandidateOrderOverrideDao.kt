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
        WHERE input = :input
        ORDER BY rank ASC
        """
    )
    suspend fun findByInput(input: String): List<CandidateOrderOverrideEntity>

    @Query(
        """
        SELECT * FROM candidate_order_override
        ORDER BY input ASC, rank ASC
        """
    )
    fun observeAll(): Flow<List<CandidateOrderOverrideEntity>>

    @Query(
        """
        DELETE FROM candidate_order_override
        WHERE input = :input
        """
    )
    suspend fun deleteByInput(input: String)

    @Query("DELETE FROM candidate_order_override")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CandidateOrderOverrideEntity>)

    @Transaction
    suspend fun replaceForInput(input: String, entities: List<CandidateOrderOverrideEntity>) {
        deleteByInput(input)
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
