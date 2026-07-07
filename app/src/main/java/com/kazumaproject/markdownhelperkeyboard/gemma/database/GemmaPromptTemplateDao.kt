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
        WHERE isEnabled = 1
        ORDER BY sortOrder ASC, updatedAt DESC, id DESC
        LIMIT :limit
        """
    )
    suspend fun getEnabledTemplates(limit: Int): List<GemmaPromptTemplate>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM gemma_prompt_template")
    suspend fun getMaxSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: GemmaPromptTemplate): Long

    @Update
    suspend fun update(template: GemmaPromptTemplate)

    @Delete
    suspend fun delete(template: GemmaPromptTemplate)
}
