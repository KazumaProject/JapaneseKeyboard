package com.kazumaproject.markdownhelperkeyboard.learning.repository

import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnDao
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LearnRepository @Inject constructor(
    private val learnDao: LearnDao
) {
    suspend fun insertLearnedData(learnData: LearnEntity) = learnDao.insert(learnData)

    suspend fun findLearnDataByInput(input: String): List<String> = learnDao.findByInput(input)

    suspend fun findLearnDataByInputAndOutput(input: String, output: String): LearnEntity? =
        learnDao.findByInputAndOutput(input, output)

    suspend fun upsertLearnedData(learnData: LearnEntity) {
        val existingData = learnDao.findByInputAndOutput(learnData.input, learnData.out)
        println("learn data exist? $existingData $learnData")
        if (existingData == null) {
            learnDao.insert(learnData)
        } else {
            val score =
                if (existingData.score > 0) (existingData.score - 1).toShort() else (0).toShort()
            println("learn data exist and score? $score")
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
}