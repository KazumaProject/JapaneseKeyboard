package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FullKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.KeyDefinition
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.TwoStepFlickMapping
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyboardLayoutDao {

    // ★変更: sortOrder DESC を優先し、同順なら createdAt DESC（従来互換）
    @Query("SELECT * FROM keyboard_layouts ORDER BY sortOrder DESC, createdAt DESC")
    fun getLayoutsList(): Flow<List<CustomKeyboardLayout>>

    // ★変更
    @Query("SELECT * FROM keyboard_layouts ORDER BY sortOrder DESC, createdAt DESC")
    suspend fun getLayoutsListNotFlow(): List<CustomKeyboardLayout>

    @Transaction
    @Query("SELECT * FROM keyboard_layouts WHERE layoutId = :id")
    fun getFullLayoutById(id: Long): Flow<FullKeyboardLayout>

    /**
     * 新しいキーボードレイアウトをデータベースにアトミックに保存する。
     * キーとフリック、TwoStep の正しい関連付けを保証する。
     */
    @Transaction
    suspend fun insertFullKeyboardLayout(
        layout: CustomKeyboardLayout,
        keys: List<KeyDefinition>,
        flicksMap: Map<String, List<FlickMapping>>,
        twoStepFlicksMap: Map<String, List<TwoStepFlickMapping>>
    ) {
        val layoutId = insertLayout(layout)

        val keysWithLayoutId = keys.map { it.copy(ownerLayoutId = layoutId) }
        val newKeyIds = insertKeys(keysWithLayoutId)

        val identifierToIdMap = keysWithLayoutId
            .mapIndexed { index, key -> key.keyIdentifier to newKeyIds[index] }
            .toMap()

        val flicksWithRealKeyIds = mutableListOf<FlickMapping>()
        identifierToIdMap.forEach { (identifier, realKeyId) ->
            flicksMap[identifier]?.forEach { flick ->
                flicksWithRealKeyIds.add(flick.copy(ownerKeyId = realKeyId))
            }
        }
        if (flicksWithRealKeyIds.isNotEmpty()) {
            insertFlickMappings(flicksWithRealKeyIds)
        }

        val twoStepWithRealKeyIds = mutableListOf<TwoStepFlickMapping>()
        identifierToIdMap.forEach { (identifier, realKeyId) ->
            twoStepFlicksMap[identifier]?.forEach { mapping ->
                twoStepWithRealKeyIds.add(mapping.copy(ownerKeyId = realKeyId))
            }
        }
        if (twoStepWithRealKeyIds.isNotEmpty()) {
            insertTwoStepFlickMappings(twoStepWithRealKeyIds)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLayout(layout: CustomKeyboardLayout): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeys(keys: List<KeyDefinition>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlickMappings(flicks: List<FlickMapping>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTwoStepFlickMappings(mappings: List<TwoStepFlickMapping>)

    @Query("DELETE FROM keyboard_layouts WHERE layoutId = :layoutId")
    suspend fun deleteLayout(layoutId: Long)

    @Update
    suspend fun updateLayout(layout: CustomKeyboardLayout)

    @Query("DELETE FROM key_definitions WHERE ownerLayoutId = :layoutId")
    suspend fun deleteKeysForLayout(layoutId: Long)

    @Query("DELETE FROM flick_mappings WHERE ownerKeyId IN (SELECT keyId FROM key_definitions WHERE ownerLayoutId = :layoutId)")
    suspend fun deleteFlicksForLayout(layoutId: Long)

    @Query("DELETE FROM two_step_flick_mappings WHERE ownerKeyId IN (SELECT keyId FROM key_definitions WHERE ownerLayoutId = :layoutId)")
    suspend fun deleteTwoStepFlicksForLayout(layoutId: Long)

    @Transaction
    suspend fun deleteKeysAndFlicksForLayout(layoutId: Long) {
        deleteFlicksForLayout(layoutId)
        deleteTwoStepFlicksForLayout(layoutId)
        deleteKeysForLayout(layoutId)
    }

    @Query("SELECT name FROM keyboard_layouts WHERE layoutId = :id")
    suspend fun getLayoutName(id: Long): String?

    @Transaction
    @Query("SELECT * FROM keyboard_layouts WHERE layoutId = :id")
    suspend fun getFullLayoutOneShot(id: Long): FullKeyboardLayout?

    @Query("SELECT * FROM keyboard_layouts WHERE name = :name LIMIT 1")
    suspend fun findLayoutByName(name: String): CustomKeyboardLayout?

    @Transaction
    @Query("SELECT * FROM keyboard_layouts")
    fun getAllFullLayouts(): Flow<List<FullKeyboardLayout>>

    @Transaction
    @Query("SELECT * FROM keyboard_layouts")
    suspend fun getAllFullLayoutsOneShot(): List<FullKeyboardLayout>

    // -----------------------------
    // ★追加: 並び順永続化
    // -----------------------------

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM keyboard_layouts")
    suspend fun getMaxSortOrder(): Int

    @Query("UPDATE keyboard_layouts SET sortOrder = :sortOrder WHERE layoutId = :layoutId")
    suspend fun updateLayoutSortOrder(layoutId: Long, sortOrder: Int)

    /**
     * 表示順（上→下）で受け取った layoutId のリストを永続化する。
     * sortOrder は「大きいほど上」になるように採番する。
     */
    @Transaction
    suspend fun updateLayoutOrdersInDisplayOrder(layoutIdsInDisplayOrder: List<Long>) {
        val n = layoutIdsInDisplayOrder.size
        layoutIdsInDisplayOrder.forEachIndexed { index, id ->
            updateLayoutSortOrder(layoutId = id, sortOrder = n - index)
        }
    }
}
