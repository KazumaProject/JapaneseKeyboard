package com.kazumaproject.markdownhelperkeyboard.user_dictionary.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface UserWordDao {
    @Query("SELECT * FROM user_word ORDER BY reading ASC")
    fun getAll(): LiveData<List<UserWord>>

    /**
     * readingの前方一致で単語を検索する (UI表示用)
     */
    @Query("SELECT * FROM user_word WHERE reading LIKE :prefix || '%' ORDER BY reading ASC")
    fun searchByReadingPrefix(prefix: String): LiveData<List<UserWord>>

    /**
     * readingの前方一致で単語を検索する (バックグラウンド処理用)
     * @return 一致した単語のリスト
     */
    @Query("SELECT * FROM user_word WHERE reading LIKE :prefix || '%' ORDER BY reading ASC LIMIT :limit")
    suspend fun searchByReadingPrefixSuspend(prefix: String, limit: Int): List<UserWord>

    /**
     * readingと完全一致で単語を検索する (バックグラウンド処理用)
     * @param reading 検索する読みがな
     * @return 一致した単語のリスト (通常は1件または0件)
     */
    @Query("SELECT * FROM user_word WHERE reading = :reading")
    suspend fun searchByReadingExactSuspend(reading: String): List<UserWord>

    /**
     * 【新規】指定した文字列（inputStr）を前方部分に含む読みを、ユーザー辞書から全て検索する（Common Prefix Search）。
     * 例: inputStrが "とうきょうと" の場合、DBにある "とう" や "とうきょう" がヒットする。
     * @param inputStr 検索対象の文字列
     * @return 一致した単語のリスト
     */
    @Query("SELECT * FROM user_word WHERE :inputStr LIKE reading || '%' ORDER BY LENGTH(reading) DESC")
    suspend fun commonPrefixSearchInUserDict(inputStr: String): List<UserWord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(userWord: UserWord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(words: List<UserWord>)

    @Update
    suspend fun update(userWord: UserWord)

    @Query("DELETE FROM user_word WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM user_word")
    suspend fun deleteAll()
}
