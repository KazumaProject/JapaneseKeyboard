package com.kazumaproject.markdownhelperkeyboard.gemma.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GemmaPromptTemplateDao {

    @Query(
        """
        SELECT * FROM gemma_prompt_template
        ORDER BY sortOrder ASC, updatedAt DESC, id DESC
        """
    )
    fun observeAll(): Flow<List<GemmaPromptTemplate>>

    @Query(
        """
        SELECT * FROM gemma_prompt_template
        WHERE isEnabled = 1 AND inputModality = 'TEXT'
        ORDER BY sortOrder ASC, updatedAt DESC, id DESC
        LIMIT :limit
        """
    )
    suspend fun getEnabledTemplates(limit: Int): List<GemmaPromptTemplate>

    @Query(
        """
        SELECT * FROM gemma_prompt_template
        WHERE inputModality = :modality AND isEnabled = 1 AND showInActionMenu = 1
        ORDER BY sortOrder ASC, updatedAt DESC, id DESC
        """
    )
    suspend fun getEnabledActions(modality: String): List<GemmaPromptTemplate>

    @Query("SELECT builtInKey FROM gemma_prompt_template WHERE builtInKey IS NOT NULL")
    suspend fun getBuiltInKeys(): List<String>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM gemma_prompt_template")
    suspend fun getMaxSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: GemmaPromptTemplate): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(templates: List<GemmaPromptTemplate>)

    @Update
    suspend fun update(template: GemmaPromptTemplate)

    @Delete
    suspend fun delete(template: GemmaPromptTemplate)
}
