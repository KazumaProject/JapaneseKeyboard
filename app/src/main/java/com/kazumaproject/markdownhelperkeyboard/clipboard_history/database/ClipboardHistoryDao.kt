package com.kazumaproject.markdownhelperkeyboard.clipboard_history.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ClipboardHistoryItem)

    @Update
    suspend fun update(item: ClipboardHistoryItem)

    @Query("SELECT * FROM clipboard_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<ClipboardHistoryItem>>

    @Query("SELECT * FROM clipboard_history ORDER BY timestamp DESC")
    suspend fun getAllHistorySuspended(): List<ClipboardHistoryItem>

    @Query("SELECT * FROM clipboard_history ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestItem(): ClipboardHistoryItem?

    @Query("DELETE FROM clipboard_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM clipboard_history")
    suspend fun deleteAll()
}
