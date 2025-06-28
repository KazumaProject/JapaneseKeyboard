package com.kazumaproject.markdownhelperkeyboard.clipboard_history.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ClipboardHistoryItem)

    @Query("SELECT * FROM clipboard_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<ClipboardHistoryItem>>

    @Query("SELECT * FROM clipboard_history ORDER BY timestamp DESC")
    suspend fun getAllHistorySuspended(): List<ClipboardHistoryItem>

    @Query("DELETE FROM clipboard_history WHERE id = :id")
    suspend fun deleteById(id: Long)
}
