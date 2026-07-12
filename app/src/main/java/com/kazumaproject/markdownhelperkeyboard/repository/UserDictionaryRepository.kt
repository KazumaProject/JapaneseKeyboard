package com.kazumaproject.markdownhelperkeyboard.repository

import androidx.lifecycle.LiveData
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWordDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ユーザー辞書データへのアクセスを抽象化するリポジトリ。
 * ViewModelはDAOを直接操作する代わりにこのリポジトリを使用します。
 *
 * @param userWordDao Hiltによって注入されるデータアクセスオブジェクト。
 */
@Singleton
class UserDictionaryRepository @Inject constructor(
    private val userWordDao: UserWordDao
) {
    @Volatile
    private var conversionSnapshot: Map<Char, List<UserWord>>? = null
    @Volatile
    var conversionRevision: Long = 0
        private set
    private val conversionSnapshotMutex = Mutex()

    val allWords: LiveData<List<UserWord>> = userWordDao.getAll()

    fun searchByReadingPrefix(prefix: String): LiveData<List<UserWord>> {
        return userWordDao.searchByReadingPrefix(prefix)
    }

    suspend fun searchByReadingPrefixSuspend(prefix: String, limit: Int): List<UserWord> {
        return userWordDao.searchByReadingPrefixSuspend(prefix, limit)
    }

    suspend fun searchByReadingExactMatchSuspend(prefix: String): List<UserWord> {
        return userWordDao.searchByReadingExactSuspend(prefix)
    }

    suspend fun existsDuplicateForUpdate(word: String, reading: String, excludeId: Int): Boolean {
        return userWordDao.existsDuplicateForUpdate(word, reading, excludeId)
    }

    suspend fun commonPrefixSearchInUserDict(prefix: String): List<UserWord> {
        val firstCharacter = prefix.firstOrNull() ?: return emptyList()
        return getConversionSnapshot()[firstCharacter]
            ?.asSequence()
            ?.filter { prefix.startsWith(it.reading) }
            ?.toList()
            .orEmpty()
    }

    suspend fun allWordsSuspend(): List<UserWord> {
        return userWordDao.getAllSuspend()
    }

    suspend fun insert(userWord: UserWord) {
        userWordDao.insert(userWord)
        invalidateConversionSnapshot()
    }

    suspend fun insertAll(words: List<UserWord>) {
        userWordDao.insertAll(words)
        invalidateConversionSnapshot()
    }

    suspend fun update(userWord: UserWord) {
        userWordDao.update(userWord)
        invalidateConversionSnapshot()
    }

    suspend fun delete(id: Int) {
        userWordDao.delete(id)
        invalidateConversionSnapshot()
    }

    suspend fun deleteAll() {
        userWordDao.deleteAll()
        invalidateConversionSnapshot()
    }

    private suspend fun getConversionSnapshot(): Map<Char, List<UserWord>> {
        conversionSnapshot?.let { return it }
        return conversionSnapshotMutex.withLock {
            conversionSnapshot ?: userWordDao.getAllSuspend()
                .asSequence()
                .filter { it.reading.isNotEmpty() }
                .groupBy { it.reading.first() }
                .mapValues { (_, words) -> words.sortedByDescending { it.reading.length } }
                .also { conversionSnapshot = it }
        }
    }

    private fun invalidateConversionSnapshot() {
        conversionSnapshot = null
        conversionRevision++
    }

}
