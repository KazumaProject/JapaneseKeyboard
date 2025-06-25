package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnDao
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.learning.model.LearnResult
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
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
        Timber.d("upsertLearnedData: $learnData\n $existingData")
        if (existingData == null) {
            learnDao.insert(learnData)
        } else {
            val score =
                if (existingData.score > 0) ((existingData.score - 3000).coerceAtLeast(0)).toShort() else (0).toShort()
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

    /**
     * Calls the DAO to perform a predictive search for entries starting with the given prefix.
     *
     * @param prefix The prefix to search for.
     * @param limit The maximum number of results.
     * @return A list of matching LearnEntity objects.
     */
    suspend fun predictiveSearchByInput(prefix: String, limit: Int): List<LearnEntity> =
        learnDao.predictiveSearchByInput(prefix, limit)

    /**
     * Calls the DAO to find entries that are a common prefix of the given search term.
     *
     * @param searchTerm The term to search with.
     * @return A list of matching LearnEntity objects.
     */
    suspend fun findCommonPrefixes(searchTerm: String): List<LearnEntity> =
        learnDao.findCommonPrefixes(searchTerm)

    fun all(): Flow<List<LearnEntity>> = learnDao.all()

    suspend fun delete(learnData: LearnEntity) = learnDao.delete(learnData)

    suspend fun deleteAll() = learnDao.deleteAll()

    suspend fun deleteByInput(input: String) = learnDao.deleteByInput(input)

    suspend fun deleteByInputAndOutput(input: String, output: String) =
        learnDao.deleteByInputAndOutput(input, output)
}
