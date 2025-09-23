package com.kazumaproject.markdownhelperkeyboard.user_template.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface UserTemplateDao {
    @Query("SELECT * FROM user_template ORDER BY reading ASC")
    fun getAll(): LiveData<List<UserTemplate>>

    @Query("SELECT * FROM user_template ORDER BY reading ASC")
    suspend fun getAllSuspend(): List<UserTemplate>

    /**
     * readingの完全一致で定型文を検索する (UI表示用)
     */
    @Query("SELECT * FROM user_template WHERE reading = :reading ORDER BY id DESC")
    fun searchByReadingExact(reading: String): LiveData<List<UserTemplate>>

    /**
     * readingの完全一致で定型文を検索する (バックグラウンド処理用)
     * @return 一致した定型文のリスト
     */
    @Query("SELECT * FROM user_template WHERE reading = :reading ORDER BY id DESC LIMIT :limit")
    suspend fun searchByReadingExactSuspend(reading: String, limit: Int): List<UserTemplate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(userTemplate: UserTemplate)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(templates: List<UserTemplate>)

    @Update
    suspend fun update(userTemplate: UserTemplate)

    @Query("DELETE FROM user_template WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM user_template")
    suspend fun deleteAll()
}
