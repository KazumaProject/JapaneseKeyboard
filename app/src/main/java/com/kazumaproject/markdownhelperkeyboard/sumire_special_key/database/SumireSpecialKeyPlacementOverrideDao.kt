package com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SumireSpecialKeyPlacementOverrideDao {
    @Query("SELECT * FROM sumire_special_key_placement_overrides")
    fun observeAll(): Flow<List<SumireSpecialKeyPlacementOverrideEntity>>

    @Query(
        """
        SELECT * FROM sumire_special_key_placement_overrides
        WHERE layout_type = :layoutType AND input_mode = :inputMode
        """
    )
    fun getByLayoutAndMode(
        layoutType: String,
        inputMode: String
    ): Flow<List<SumireSpecialKeyPlacementOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SumireSpecialKeyPlacementOverrideEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SumireSpecialKeyPlacementOverrideEntity>)

    @Query(
        """
        DELETE FROM sumire_special_key_placement_overrides
        WHERE layout_type = :layoutType AND input_mode = :inputMode AND key_id = :keyId
        """
    )
    suspend fun deleteKey(layoutType: String, inputMode: String, keyId: String)

    @Query(
        """
        DELETE FROM sumire_special_key_placement_overrides
        WHERE layout_type = :layoutType AND input_mode = :inputMode
        """
    )
    suspend fun deleteLayoutMode(layoutType: String, inputMode: String)
}

