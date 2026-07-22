package com.kazumaproject.markdownhelperkeyboard.repository

import android.database.sqlite.SQLiteConstraintException
import com.kazumaproject.markdownhelperkeyboard.learning.LearningEligibilityPolicy
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnDao
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.learning.model.LearnResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LearnRepository @Inject constructor(
    private val learnDao: LearnDao
) {
    @Volatile
    private var conversionSnapshot: Map<Char, List<LearnEntity>> = emptyMap()
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

    suspend fun upsertLearnedData(
        learnData: LearnEntity,
        allowJapaneseWithSymbolsAndNumbers: Boolean = true,
    ) = upsertLearnedDataBatch(
        learnDataList = listOf(learnData),
        allowJapaneseWithSymbolsAndNumbers = allowJapaneseWithSymbolsAndNumbers,
    )

    suspend fun upsertLearnedDataBatch(
        learnDataList: List<LearnEntity>,
        allowJapaneseWithSymbolsAndNumbers: Boolean,
    ) {
        val eligibleEntries = learnDataList.filter { entry ->
            LearningEligibilityPolicy.isEligible(
                input = entry.input,
                output = entry.out,
                allowJapaneseWithSymbolsAndNumbers = allowJapaneseWithSymbolsAndNumbers,
            )
        }
        if (eligibleEntries.isEmpty()) return
        learnDao.upsertLearningEntries(eligibleEntries)
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
        learnDao.predictiveSearchByInput(prefix, prefix.upperBound(), limit)

    /**
     * Calls the DAO to find entries that are a common prefix of the given search term.
     *
     * @param searchTerm The term to search with.
     * @return A list of matching LearnEntity objects.
     */
    suspend fun findCommonPrefixes(searchTerm: String): List<LearnEntity> {
        val firstCharacter = searchTerm.firstOrNull() ?: return emptyList()
        return getConversionBucket(firstCharacter)
            .asSequence()
            .filter { searchTerm.startsWith(it.input) }
            .toList()
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

    private suspend fun getConversionBucket(firstCharacter: Char): List<LearnEntity> {
        conversionSnapshot[firstCharacter]?.let { return it }
        return conversionSnapshotMutex.withLock {
            conversionSnapshot[firstCharacter] ?: learnDao.findByInputPrefix(
                firstCharacter.toString(),
                firstCharacter.toString().upperBound(),
            ).also { words ->
                conversionSnapshot = conversionSnapshot + (firstCharacter to words)
            }
        }
    }

    private fun invalidateConversionSnapshot() {
        conversionSnapshot = emptyMap()
        conversionRevision++
    }
}

private fun String.upperBound(): String = this + '\uFFFF'

enum class LearnUpdateResult {
    Updated,
    Duplicate,
    Error,
}
