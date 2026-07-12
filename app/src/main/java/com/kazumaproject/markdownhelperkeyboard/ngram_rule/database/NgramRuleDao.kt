package com.kazumaproject.markdownhelperkeyboard.ngram_rule.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NgramRuleDao {
    @Query("SELECT * FROM ngram_rule ORDER BY nodeCount, id DESC")
    fun observeRules(): Flow<List<NgramRuleEntity>>

    @Query("SELECT * FROM ngram_rule ORDER BY nodeCount, id DESC")
    suspend fun getAllRules(): List<NgramRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(entity: NgramRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllRules(entities: List<NgramRuleEntity>)

    @Query("DELETE FROM ngram_rule WHERE id = :id")
    suspend fun deleteRule(id: Int)

    @Query("DELETE FROM ngram_rule")
    suspend fun deleteAllRules()

    @Transaction
    suspend fun replaceAll(entities: List<NgramRuleEntity>) {
        deleteAllRules()
        insertAllRules(entities)
    }
}
