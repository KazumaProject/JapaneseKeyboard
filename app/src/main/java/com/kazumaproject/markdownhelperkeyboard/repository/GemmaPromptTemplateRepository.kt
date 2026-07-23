package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaPromptTemplate
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaPromptTemplateDao
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaBuiltInActions
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaInputModality
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GemmaPromptTemplateRepository @Inject constructor(
    private val gemmaPromptTemplateDao: GemmaPromptTemplateDao
) {

    fun observeAll(): Flow<List<GemmaPromptTemplate>> = gemmaPromptTemplateDao.observeAll()

    suspend fun getEnabledTemplates(limit: Int): List<GemmaPromptTemplate> {
        ensureBuiltIns()
        return gemmaPromptTemplateDao.getEnabledTemplates(limit)
    }

    suspend fun getEnabledActions(modality: GemmaInputModality): List<GemmaPromptTemplate> {
        ensureBuiltIns()
        return gemmaPromptTemplateDao.getEnabledActions(modality.name)
    }

    suspend fun ensureBuiltIns() {
        val existing = gemmaPromptTemplateDao.getBuiltInKeys().toSet()
        val missing = GemmaBuiltInActions.all().filter { it.builtInKey !in existing }
        if (missing.isNotEmpty()) gemmaPromptTemplateDao.insertAll(missing)
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
