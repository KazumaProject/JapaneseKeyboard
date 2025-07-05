package com.kazumaproject.markdownhelperkeyboard.repository

import androidx.room.Transaction
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

    suspend fun insertAll(learnDataList: List<LearnEntity>) = learnDao.insertAll(learnDataList)

    suspend fun findLearnDataByInput(input: String): List<LearnResult>? =
        learnDao.findByInput(input)?.sortedBy { it.score }

    suspend fun findLearnDataByInputAndOutput(input: String, output: String): LearnEntity? =
        learnDao.findByInputAndOutput(input, output)

    /**
     * 学習データをアトミックにupsert（更新または挿入）します。
     * この関数は、指定された単語が既に存在するかどうかを確認し、
     * 存在しない場合は挿入、存在する場合はスコアを加算して更新します。
     * @Transactionアノテーションにより、一連の処理が単一のトランザクションとして実行され、競合状態を防ぎます。
     *
     * @param learnData 学習させたいデータ。
     * @param scoreIncrement スコアを加算する量。
     */
    @Transaction
    suspend fun upsertLearnedData(learnData: LearnEntity) {
        val existingData = learnDao.findByInputAndOutput(learnData.input, learnData.out)
        if (existingData == null) {
            learnDao.insert(learnData)
        } else {
            val score =
                if (existingData.score > 0) ((existingData.score - 1500).coerceAtLeast(0)).toShort() else (0).toShort()
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

    suspend fun update(learnData: LearnEntity) = learnDao.updateLearnedData(learnData)
}
