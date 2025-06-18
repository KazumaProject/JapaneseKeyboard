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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(userWord: UserWord)

    @Update
    suspend fun update(userWord: UserWord)

    @Query("DELETE FROM user_word WHERE id = :id")
    suspend fun delete(id: Int)
}
