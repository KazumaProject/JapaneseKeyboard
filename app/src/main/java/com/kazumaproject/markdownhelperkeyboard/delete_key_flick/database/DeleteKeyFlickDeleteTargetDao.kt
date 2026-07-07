package com.kazumaproject.markdownhelperkeyboard.delete_key_flick.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DeleteKeyFlickDeleteTargetDao {

    @Query("SELECT * FROM delete_key_flick_delete_targets ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<DeleteKeyFlickDeleteTarget>>

    @Query("SELECT * FROM delete_key_flick_delete_targets ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<DeleteKeyFlickDeleteTarget>

    @Query("SELECT COUNT(*) FROM delete_key_flick_delete_targets")
    suspend fun count(): Int

    @Query("SELECT * FROM delete_key_flick_delete_targets WHERE symbol = :symbol LIMIT 1")
    suspend fun findBySymbol(symbol: String): DeleteKeyFlickDeleteTarget?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(target: DeleteKeyFlickDeleteTarget): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(targets: List<DeleteKeyFlickDeleteTarget>)

    @Update
    suspend fun update(target: DeleteKeyFlickDeleteTarget)

    @Delete
    suspend fun delete(target: DeleteKeyFlickDeleteTarget)

    @Query("DELETE FROM delete_key_flick_delete_targets")
    suspend fun deleteAll()
}
