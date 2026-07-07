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
    suspend fun insert(item: ClipboardHistoryItem): Long

    @Update
    suspend fun update(item: ClipboardHistoryItem)

    @Query("SELECT * FROM clipboard_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<ClipboardHistoryItem>>

    @Query("SELECT * FROM clipboard_history ORDER BY timestamp DESC")
    suspend fun getAllHistorySuspended(): List<ClipboardHistoryItem>

    @Query("SELECT * FROM clipboard_history ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestItem(): ClipboardHistoryItem?

    @Query("SELECT * FROM clipboard_history WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ClipboardHistoryItem?

    @Query("UPDATE clipboard_history SET isPinned = :isPinned WHERE id = :id")
    suspend fun setPinned(id: Long, isPinned: Boolean)

    @Query("SELECT * FROM clipboard_history WHERE isPinned = 0 AND timestamp < :threshold")
    suspend fun getExpiredUnpinnedItems(threshold: Long): List<ClipboardHistoryItem>

    @Query("DELETE FROM clipboard_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM clipboard_history")
    suspend fun deleteAll()

    @Query("SELECT contentPath FROM clipboard_history WHERE id = :id")
    suspend fun getContentPathById(id: Long): String?
}
