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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(userWord: UserWord)

    @Update
    suspend fun update(userWord: UserWord)

    @Query("DELETE FROM user_word WHERE id = :id")
    suspend fun delete(id: Int)
}
