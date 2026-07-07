package com.kazumaproject.markdownhelperkeyboard.ngram_rule.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NgramRuleDao {
    @Query("SELECT * FROM two_node_rule ORDER BY id DESC")
    fun observeTwoNodeRules(): Flow<List<TwoNodeRuleEntity>>

    @Query("SELECT * FROM three_node_rule ORDER BY id DESC")
    fun observeThreeNodeRules(): Flow<List<ThreeNodeRuleEntity>>

    @Query("SELECT * FROM two_node_rule ORDER BY id DESC")
    suspend fun getAllTwoNodeRules(): List<TwoNodeRuleEntity>

    @Query("SELECT * FROM three_node_rule ORDER BY id DESC")
    suspend fun getAllThreeNodeRules(): List<ThreeNodeRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTwoNodeRule(entity: TwoNodeRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertThreeNodeRule(entity: ThreeNodeRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTwoNodeRules(entities: List<TwoNodeRuleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllThreeNodeRules(entities: List<ThreeNodeRuleEntity>)

    @Query("DELETE FROM two_node_rule WHERE id = :id")
    suspend fun deleteTwoNodeRule(id: Int)

    @Query("DELETE FROM three_node_rule WHERE id = :id")
    suspend fun deleteThreeNodeRule(id: Int)

    @Query("DELETE FROM two_node_rule")
    suspend fun deleteAllTwoNodeRules()

    @Query("DELETE FROM three_node_rule")
    suspend fun deleteAllThreeNodeRules()
}

