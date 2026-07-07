package com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SumireSpecialKeyActionOverrideDao {
    @Query("SELECT * FROM sumire_special_key_action_overrides")
    fun observeAll(): Flow<List<SumireSpecialKeyActionOverrideEntity>>

    @Query(
        """
        SELECT * FROM sumire_special_key_action_overrides
        WHERE layout_type = :layoutType AND input_mode = :inputMode
        """
    )
    fun getByLayoutAndMode(
        layoutType: String,
        inputMode: String
    ): Flow<List<SumireSpecialKeyActionOverrideEntity>>

    @Query(
        """
        SELECT * FROM sumire_special_key_action_overrides
        WHERE layout_type = :layoutType AND input_mode = :inputMode AND key_id = :keyId
        """
    )
    fun getByKey(
        layoutType: String,
        inputMode: String,
        keyId: String
    ): Flow<List<SumireSpecialKeyActionOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SumireSpecialKeyActionOverrideEntity)

    @Query(
        """
        DELETE FROM sumire_special_key_action_overrides
        WHERE layout_type = :layoutType
          AND input_mode = :inputMode
          AND key_id = :keyId
          AND direction = :direction
        """
    )
    suspend fun deleteDirection(
        layoutType: String,
        inputMode: String,
        keyId: String,
        direction: String
    )

    @Query(
        """
        DELETE FROM sumire_special_key_action_overrides
        WHERE layout_type = :layoutType AND input_mode = :inputMode AND key_id = :keyId
        """
    )
    suspend fun deleteKey(layoutType: String, inputMode: String, keyId: String)

    @Query(
        """
        DELETE FROM sumire_special_key_action_overrides
        WHERE layout_type = :layoutType AND input_mode = :inputMode
        """
    )
    suspend fun deleteLayoutMode(layoutType: String, inputMode: String)
}

