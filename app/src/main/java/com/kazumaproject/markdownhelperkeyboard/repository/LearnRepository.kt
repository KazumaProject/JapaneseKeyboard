package com.kazumaproject.markdownhelperkeyboard.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Transaction
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.containsSymbolNumberOrEmoji
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isAllHiragana
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnDao
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.learning.model.LearnResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LearnRepository @Inject constructor(
    private val learnDao: LearnDao
) {
    @Volatile
    private var conversionSnapshot: Map<Char, List<LearnEntity>>? = null
    @Volatile
    var conversionRevision: Long = 0
        private set
    private val conversionSnapshotMutex = Mutex()

    suspend fun insertLearnedData(learnData: LearnEntity) {
        learnDao.insert(learnData)
        invalidateConversionSnapshot()
    }

    suspend fun insertAll(learnDataList: List<LearnEntity>) {
        learnDao.insertAll(learnDataList)
        invalidateConversionSnapshot()
    }

    suspend fun findLearnDataByInput(input: String): List<LearnResult>? =
        learnDao.findByInput(input)?.sortedBy { it.score }

    suspend fun findLearnDataByInputAndOutput(input: String, output: String): LearnEntity? =
        learnDao.findByInputAndOutput(input, output)

    suspend fun existsDuplicateForUpdate(input: String, output: String, excludeId: Int): Boolean =
        learnDao.existsDuplicateForUpdate(input, output, excludeId)

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
        Timber.d("upsertLearnedData: ${learnData.input} ${learnData.out} ${learnData.input.isAllHiragana()}")
        if (learnData.out.containsSymbolNumberOrEmoji()) return
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
        invalidateConversionSnapshot()
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
    suspend fun findCommonPrefixes(searchTerm: String): List<LearnEntity> {
        val firstCharacter = searchTerm.firstOrNull() ?: return emptyList()
        return getConversionSnapshot()[firstCharacter]
            ?.asSequence()
            ?.filter { searchTerm.startsWith(it.input) }
            ?.toList()
            .orEmpty()
    }

    fun all(): Flow<List<LearnEntity>> = learnDao.all()

    suspend fun allSuspend(): List<LearnEntity> = learnDao.getAllSuspend()

    suspend fun delete(learnData: LearnEntity) {
        learnDao.delete(learnData)
        invalidateConversionSnapshot()
    }

    suspend fun deleteAll() {
        learnDao.deleteAll()
        invalidateConversionSnapshot()
    }

    suspend fun deleteByInput(input: String): Int {
        return learnDao.deleteByInput(input).also { invalidateConversionSnapshot() }
    }

    suspend fun deleteByInputAndOutput(input: String, output: String): Int {
        return learnDao.deleteByInputAndOutput(input, output)
            .also { invalidateConversionSnapshot() }
    }

    suspend fun update(learnData: LearnEntity) {
        learnDao.updateLearnedData(learnData)
        invalidateConversionSnapshot()
    }

    suspend fun updateSafely(learnData: LearnEntity): LearnUpdateResult {
        return try {
            val id = learnData.id ?: return LearnUpdateResult.Error
            if (existsDuplicateForUpdate(learnData.input, learnData.out, id)) {
                return LearnUpdateResult.Duplicate
            }
            update(learnData)
            LearnUpdateResult.Updated
        } catch (e: CancellationException) {
            throw e
        } catch (_: SQLiteConstraintException) {
            LearnUpdateResult.Duplicate
        } catch (_: Exception) {
            LearnUpdateResult.Error
        }
    }

    private suspend fun getConversionSnapshot(): Map<Char, List<LearnEntity>> {
        conversionSnapshot?.let { return it }
        return conversionSnapshotMutex.withLock {
            conversionSnapshot ?: learnDao.getAllSuspend()
                .asSequence()
                .filter { it.input.isNotEmpty() }
                .groupBy { it.input.first() }
                .mapValues { (_, words) -> words.sortedByDescending { it.input.length } }
                .also { conversionSnapshot = it }
        }
    }

    private fun invalidateConversionSnapshot() {
        conversionSnapshot = null
        conversionRevision++
    }
}

enum class LearnUpdateResult {
    Updated,
    Duplicate,
    Error,
}
