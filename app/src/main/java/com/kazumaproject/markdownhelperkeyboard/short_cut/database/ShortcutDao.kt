package com.kazumaproject.markdownhelperkeyboard.short_cut.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kazumaproject.markdownhelperkeyboard.short_cut.data.ShortcutItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ShortcutDao {
    @Query("SELECT * FROM shortcut_items ORDER BY sortOrder ASC")
    fun getAllShortcutsFlow(): Flow<List<ShortcutItem>>

    @Query("SELECT * FROM shortcut_items ORDER BY sortOrder ASC")
    suspend fun getAllShortcuts(): List<ShortcutItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ShortcutItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ShortcutItem>)

    @Delete
    suspend fun delete(item: ShortcutItem)

    @Query("DELETE FROM shortcut_items")
    suspend fun deleteAll()

    // 初期化用（データがない場合にデフォルトセットを入れるなど）
    @Transaction
    suspend fun initDefaultShortcuts(defaults: List<ShortcutItem>) {
        if (getAllShortcuts().isEmpty()) {
            insertAll(defaults)
        }
    }
}
