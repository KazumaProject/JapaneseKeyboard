package com.kazumaproject.markdownhelperkeyboard.repository

import androidx.lifecycle.LiveData
import com.kazumaproject.markdownhelperkeyboard.user_template.database.UserTemplate
import com.kazumaproject.markdownhelperkeyboard.user_template.database.UserTemplateDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserTemplateRepository @Inject constructor(
    private val userTemplateDao: UserTemplateDao
) {

    val allTemplates: LiveData<List<UserTemplate>> = userTemplateDao.getAll()

    suspend fun insert(userTemplate: UserTemplate) {
        userTemplateDao.insert(userTemplate)
    }

    suspend fun insertAll(templates: List<UserTemplate>) {
        userTemplateDao.insertAll(templates)
    }

    suspend fun update(userTemplate: UserTemplate) {
        userTemplateDao.update(userTemplate)
    }

    suspend fun delete(id: Int) {
        userTemplateDao.delete(id)
    }

    suspend fun deleteAll() {
        userTemplateDao.deleteAll()
    }

    fun searchByReadingExactUI(reading: String): LiveData<List<UserTemplate>> {
        return userTemplateDao.searchByReadingExact(reading)
    }

    suspend fun searchByReading(reading: String, limit: Int): List<UserTemplate> {
        return userTemplateDao.searchByReadingExactSuspend(reading, limit)
    }
}
