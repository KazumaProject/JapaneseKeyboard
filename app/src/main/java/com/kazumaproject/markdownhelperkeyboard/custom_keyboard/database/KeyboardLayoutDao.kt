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
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyboardLayoutDao {

    @Query("SELECT * FROM keyboard_layouts ORDER BY createdAt DESC")
    fun getLayoutsList(): Flow<List<CustomKeyboardLayout>>

    @Query("SELECT * FROM keyboard_layouts ORDER BY createdAt DESC")
    suspend fun getLayoutsListNotFlow(): List<CustomKeyboardLayout>

    @Transaction
    @Query("SELECT * FROM keyboard_layouts WHERE layoutId = :id")
    fun getFullLayoutById(id: Long): Flow<FullKeyboardLayout>

    /**
     * 【バグ修正版】新しいキーボードレイアウトをデータベースにアトミックに保存する。
     * キーとフリックの正しい関連付けを保証する。
     */
    @Transaction
    suspend fun insertFullKeyboardLayout(
        layout: CustomKeyboardLayout,
        keys: List<KeyDefinition>,
        flicksMap: Map<String, List<FlickMapping>> // ★キーとフリックの関連情報を受け取る
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
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLayout(layout: CustomKeyboardLayout): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeys(keys: List<KeyDefinition>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlickMappings(flicks: List<FlickMapping>)

    @Query("DELETE FROM keyboard_layouts WHERE layoutId = :layoutId")
    suspend fun deleteLayout(layoutId: Long)

    @Update
    suspend fun updateLayout(layout: CustomKeyboardLayout)

    @Query("DELETE FROM key_definitions WHERE ownerLayoutId = :layoutId")
    suspend fun deleteKeysForLayout(layoutId: Long)

    @Query("DELETE FROM flick_mappings WHERE ownerKeyId IN (SELECT keyId FROM key_definitions WHERE ownerLayoutId = :layoutId)")
    suspend fun deleteFlicksForLayout(layoutId: Long)

    @Transaction
    suspend fun deleteKeysAndFlicksForLayout(layoutId: Long) {
        deleteFlicksForLayout(layoutId)
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
}
