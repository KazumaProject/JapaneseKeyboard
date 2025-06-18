package com.kazumaproject.markdownhelperkeyboard.repository

import androidx.lifecycle.LiveData
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWordDao
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
    val allWords: LiveData<List<UserWord>> = userWordDao.getAll()

    fun searchByReadingPrefix(prefix: String): LiveData<List<UserWord>> {
        return userWordDao.searchByReadingPrefix(prefix)
    }

    suspend fun searchByReadingPrefixSuspend(prefix: String): List<UserWord> {
        return userWordDao.searchByReadingPrefixSuspend(prefix)
    }

    suspend fun insert(userWord: UserWord) {
        userWordDao.insert(userWord)
    }

    suspend fun insertAll(words: List<UserWord>) {
        // インポート機能のために複数の単語を一度に登録する
        words.forEach { userWordDao.insert(it) }
    }

    suspend fun update(userWord: UserWord) {
        userWordDao.update(userWord)
    }

    suspend fun delete(id: Int) {
        userWordDao.delete(id)
    }
}
