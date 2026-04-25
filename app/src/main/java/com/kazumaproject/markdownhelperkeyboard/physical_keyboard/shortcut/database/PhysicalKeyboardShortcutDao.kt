package com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PhysicalKeyboardShortcutDao {
    @Query("SELECT * FROM physical_keyboard_shortcut_items ORDER BY sortOrder ASC, id ASC")
    fun getAll(): Flow<List<PhysicalKeyboardShortcutItem>>

    @Query("SELECT * FROM physical_keyboard_shortcut_items WHERE enabled = 1 ORDER BY sortOrder ASC, id ASC")
    fun getEnabled(): Flow<List<PhysicalKeyboardShortcutItem>>

    @Query("SELECT * FROM physical_keyboard_shortcut_items WHERE id = :id")
    fun getById(id: Long): Flow<PhysicalKeyboardShortcutItem?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: PhysicalKeyboardShortcutItem): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<PhysicalKeyboardShortcutItem>)

    @Update
    suspend fun update(item: PhysicalKeyboardShortcutItem)

    @Delete
    suspend fun delete(item: PhysicalKeyboardShortcutItem)

    @Query("DELETE FROM physical_keyboard_shortcut_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM physical_keyboard_shortcut_items")
    suspend fun count(): Int

    @Query(
        """
        SELECT * FROM physical_keyboard_shortcut_items
        WHERE context = :context
          AND keyCode = :keyCode
          AND ((scanCode IS NULL AND :scanCode IS NULL) OR scanCode = :scanCode)
          AND ctrl = :ctrl
          AND shift = :shift
          AND alt = :alt
          AND meta = :meta
          AND id != :excludeId
        LIMIT 1
        """
    )
    suspend fun findDuplicate(
        context: String,
        keyCode: Int,
        scanCode: Int?,
        ctrl: Boolean,
        shift: Boolean,
        alt: Boolean,
        meta: Boolean,
        excludeId: Long = 0
    ): PhysicalKeyboardShortcutItem?
}
