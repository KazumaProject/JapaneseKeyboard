package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaPromptTemplate
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaPromptTemplateDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GemmaPromptTemplateRepository @Inject constructor(
    private val gemmaPromptTemplateDao: GemmaPromptTemplateDao
) {

    fun observeAll(): Flow<List<GemmaPromptTemplate>> = gemmaPromptTemplateDao.observeAll()

    suspend fun getEnabledTemplates(limit: Int): List<GemmaPromptTemplate> {
        return gemmaPromptTemplateDao.getEnabledTemplates(limit)
    }

    suspend fun insert(template: GemmaPromptTemplate): Long {
        return gemmaPromptTemplateDao.insert(template)
    }

    suspend fun update(template: GemmaPromptTemplate) {
        gemmaPromptTemplateDao.update(template)
    }

    suspend fun delete(template: GemmaPromptTemplate) {
        gemmaPromptTemplateDao.delete(template)
    }

    suspend fun nextSortOrder(): Int {
        return gemmaPromptTemplateDao.getMaxSortOrder() + 1
    }
}
