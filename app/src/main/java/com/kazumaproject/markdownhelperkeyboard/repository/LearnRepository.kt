package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnDao
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.learning.model.LearnResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LearnRepository @Inject constructor(
    private val learnDao: LearnDao
) {
    suspend fun insertLearnedData(learnData: LearnEntity) = learnDao.insert(learnData)

    suspend fun findLearnDataByInput(input: String): List<LearnResult>? =
        learnDao.findByInput(input)?.sortedBy { it.score }

    suspend fun findLearnDataByInputAndOutput(input: String, output: String): LearnEntity? =
        learnDao.findByInputAndOutput(input, output)

    suspend fun upsertLearnedData(learnData: LearnEntity) {
        val existingData = learnDao.findByInputAndOutput(learnData.input, learnData.out)
        if (existingData == null) {
            learnDao.insert(learnData)
        } else {
            val score =
                if (existingData.score > 0) (existingData.score - 1).toShort() else (0).toShort()
            learnDao.updateLearnedData(
                learnData.copy(
                    input = learnData.input,
                    out = learnData.out,
                    score = score,
                    id = existingData.id
                )
            )
        }
    }

    fun all(): Flow<List<LearnEntity>> = learnDao.all()

    suspend fun delete(learnData: LearnEntity) = learnDao.delete(learnData)

    suspend fun deleteAll() = learnDao.deleteAll()

    suspend fun deleteByInput(input: String) = learnDao.deleteByInput(input)

    suspend fun deleteByInputAndOutput(input: String, output: String) =
        learnDao.deleteByInputAndOutput(input, output)
}
