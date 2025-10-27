package com.kazumaproject.markdownhelperkeyboard.custom_romaji.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RomajiMapDao {
    @Query("SELECT * FROM romaji_maps WHERE isActive = 1 LIMIT 1")
    fun getActiveMap(): Flow<RomajiMapEntity?>

    @Query("SELECT * FROM romaji_maps ORDER BY id ASC")
    fun getAllMaps(): Flow<List<RomajiMapEntity>>

    @Query("SELECT * FROM romaji_maps WHERE id = :id")
    fun getMapById(id: Long): Flow<RomajiMapEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(map: RomajiMapEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(maps: List<RomajiMapEntity>)

    @Update
    suspend fun update(map: RomajiMapEntity)

    @Delete
    suspend fun delete(map: RomajiMapEntity)

    @Query("SELECT COUNT(*) FROM romaji_maps")
    suspend fun count(): Int

    /**
     * Finds the single non-deletable map in the database.
     * Assumes there is only one such map.
     */
    @Query("SELECT * FROM romaji_maps WHERE isDeletable = 0 LIMIT 1")
    suspend fun getNonDeletableMap(): RomajiMapEntity?

    @Transaction
    suspend fun setActiveMap(mapId: Long) {
        deactivateAllMaps()
        activateMap(mapId)
    }

    @Query("UPDATE romaji_maps SET isActive = 0")
    suspend fun deactivateAllMaps()

    @Query("UPDATE romaji_maps SET isActive = 1 WHERE id = :mapId")
    suspend fun activateMap(mapId: Long)
}
