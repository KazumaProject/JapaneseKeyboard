package com.kazumaproject.markdownhelperkeyboard.learning.repository

import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnDao
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import javax.inject.Inject

class LearnRepository @Inject constructor(
    private val learnDao: LearnDao
) {
    suspend fun insertLearnedData(learnData: LearnEntity) {
        learnDao.insert(learnData)
    }
    suspend fun getLearnedDataByInput(input: String): List<String> {
        return learnDao.getByInput(input)
    }
}