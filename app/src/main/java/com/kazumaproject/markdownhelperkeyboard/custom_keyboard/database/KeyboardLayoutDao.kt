package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CircularFlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FullKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.KeyDefinition
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.LongPressFlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.SpacerDefinition
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.TwoStepFlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.TwoStepLongPressMappingEntity
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
     * 既存レイアウトの identity (layoutId / stableId / createdAt / sortOrder) を維持したまま、
     * 子要素 (spacers / keys / flicks 系) だけをトランザクション内で再構築する。
     *
     * 旧実装は parent row を delete + insert で作り直していたため、
     * stableId が再生成されて `KeyAction.MoveToCustomKeyboard` の参照が壊れていた。
     * このメソッドは parent row を一切 delete / replace しない。
     *
     * - layout は @Update でフィールドのみ更新 (layoutId 維持・stableId 維持)
     * - 子テーブル: spacer_definitions / key_definitions / flick_mappings 系を
     *   layoutId 単位で削除し、新しい内容を再 insert する
     * - 例外発生時は @Transaction によって全てロールバックされる
     */
    @Transaction
    suspend fun updateFullKeyboardLayoutKeepingIdentity(
        layout: CustomKeyboardLayout,
        keys: List<KeyDefinition>,
        flicksMap: Map<String, List<FlickMapping>>,
        circularFlicksMap: Map<String, List<CircularFlickMapping>>,
        twoStepFlicksMap: Map<String, List<TwoStepFlickMapping>>,
        longPressFlicksMap: Map<String, List<LongPressFlickMapping>>,
        twoStepLongPressFlicksMap: Map<String, List<TwoStepLongPressMappingEntity>>,
        spacers: List<SpacerDefinition> = emptyList()
    ) {
        // 1) parent row は @Update で更新する。
        //    layoutId / stableId / createdAt / sortOrder は呼び出し側 (Repository) が
        //    既存値で埋めているはずだが、念のためここでも layoutId は維持される。
        updateLayout(layout)

        // 2) 子要素を全て layoutId 単位で削除する。
        //    flick 系 → keys → spacers の順序は依存関係に合わせる。
        deleteKeysAndFlicksForLayout(layout.layoutId)
        deleteSpacersForLayout(layout.layoutId)

        // 3) Spacers を再 insert (ownerLayoutId を上書き)。
        if (spacers.isNotEmpty()) {
            insertSpacers(spacers.map { it.copy(spacerId = 0, ownerLayoutId = layout.layoutId) })
        }

        // 4) Keys を再 insert。新しい keyId が AUTOINCREMENT で採番される。
        val keysWithLayoutId = keys.map {
            it.copy(keyId = 0, ownerLayoutId = layout.layoutId)
        }
        val newKeyIds = insertKeys(keysWithLayoutId)

        val identifierToIdMap = keysWithLayoutId
            .mapIndexed { index, key -> key.keyIdentifier to newKeyIds[index] }
            .toMap()

        // 5) flick / circular / two-step / long-press / two-step long-press を
        //    keyIdentifier 経由で新しい keyId にぶら下げ直して insert。
        val flicksWithRealKeyIds = mutableListOf<FlickMapping>()
        identifierToIdMap.forEach { (identifier, realKeyId) ->
            flicksMap[identifier]?.forEach { flick ->
                flicksWithRealKeyIds.add(flick.copy(ownerKeyId = realKeyId))
            }
        }
        if (flicksWithRealKeyIds.isNotEmpty()) {
            insertFlickMappings(flicksWithRealKeyIds)
        }

        val circularFlicksWithRealKeyIds = mutableListOf<CircularFlickMapping>()
        identifierToIdMap.forEach { (identifier, realKeyId) ->
            circularFlicksMap[identifier]?.forEach { flick ->
                circularFlicksWithRealKeyIds.add(flick.copy(ownerKeyId = realKeyId))
            }
        }
        if (circularFlicksWithRealKeyIds.isNotEmpty()) {
            insertCircularFlickMappings(circularFlicksWithRealKeyIds)
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

        val longPressWithRealKeyIds = mutableListOf<LongPressFlickMapping>()
        identifierToIdMap.forEach { (identifier, realKeyId) ->
            longPressFlicksMap[identifier]?.forEach { mapping ->
                longPressWithRealKeyIds.add(mapping.copy(ownerKeyId = realKeyId))
            }
        }
        if (longPressWithRealKeyIds.isNotEmpty()) {
            insertLongPressFlickMappings(longPressWithRealKeyIds)
        }

        val twoStepLongPressWithRealKeyIds = mutableListOf<TwoStepLongPressMappingEntity>()
        identifierToIdMap.forEach { (identifier, realKeyId) ->
            twoStepLongPressFlicksMap[identifier]?.forEach { mapping ->
                twoStepLongPressWithRealKeyIds.add(mapping.copy(ownerKeyId = realKeyId))
            }
        }
        if (twoStepLongPressWithRealKeyIds.isNotEmpty()) {
            insertTwoStepLongPressFlickMappings(twoStepLongPressWithRealKeyIds)
        }
    }

    /**
     * 新しいキーボードレイアウトをデータベースにアトミックに保存する。
     * キーとフリック、TwoStep の正しい関連付けを保証する。
     *
     * 注意:
     * - これは「新規作成」専用と考えること。既存レイアウトの編集保存は
     *   [updateFullKeyboardLayoutKeepingIdentity] を使う。
     * - layout.layoutId が 0 のときは AUTOINCREMENT で採番される。
     * - layout.layoutId が >0 で、まだ存在しない id を強制したい場合のみ
     *   そのまま insert される (バックアップ復元など)。既存 id と衝突した場合は
     *   ABORT で例外が出るので、呼び出し側が適切に新規作成を選択すること。
     */
    @Transaction
    suspend fun insertFullKeyboardLayout(
        layout: CustomKeyboardLayout,
        keys: List<KeyDefinition>,
        flicksMap: Map<String, List<FlickMapping>>,
        circularFlicksMap: Map<String, List<CircularFlickMapping>>,
        twoStepFlicksMap: Map<String, List<TwoStepFlickMapping>>,
        longPressFlicksMap: Map<String, List<LongPressFlickMapping>>,
        twoStepLongPressFlicksMap: Map<String, List<TwoStepLongPressMappingEntity>>,
        spacers: List<SpacerDefinition> = emptyList()
    ): Long {
        val layoutId = insertLayout(layout)

        if (spacers.isNotEmpty()) {
            insertSpacers(spacers.map { it.copy(spacerId = 0, ownerLayoutId = layoutId) })
        }

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

        val circularFlicksWithRealKeyIds = mutableListOf<CircularFlickMapping>()
        identifierToIdMap.forEach { (identifier, realKeyId) ->
            circularFlicksMap[identifier]?.forEach { flick ->
                circularFlicksWithRealKeyIds.add(flick.copy(ownerKeyId = realKeyId))
            }
        }
        if (circularFlicksWithRealKeyIds.isNotEmpty()) {
            insertCircularFlickMappings(circularFlicksWithRealKeyIds)
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

        val longPressWithRealKeyIds = mutableListOf<LongPressFlickMapping>()
        identifierToIdMap.forEach { (identifier, realKeyId) ->
            longPressFlicksMap[identifier]?.forEach { mapping ->
                longPressWithRealKeyIds.add(mapping.copy(ownerKeyId = realKeyId))
            }
        }
        if (longPressWithRealKeyIds.isNotEmpty()) {
            insertLongPressFlickMappings(longPressWithRealKeyIds)
        }

        val twoStepLongPressWithRealKeyIds = mutableListOf<TwoStepLongPressMappingEntity>()
        identifierToIdMap.forEach { (identifier, realKeyId) ->
            twoStepLongPressFlicksMap[identifier]?.forEach { mapping ->
                twoStepLongPressWithRealKeyIds.add(mapping.copy(ownerKeyId = realKeyId))
            }
        }
        if (twoStepLongPressWithRealKeyIds.isNotEmpty()) {
            insertTwoStepLongPressFlickMappings(twoStepLongPressWithRealKeyIds)
        }

        return layoutId
    }

    /**
     * 親レイアウト ([CustomKeyboardLayout]) は他データから stableId 経由で参照される
     * identity を持つため、conflict 時に row を置換してはいけない。
     *
     * - 新規作成では layoutId=0 が渡され、Room によって AUTOINCREMENT で採番される。
     * - 既存更新は [updateLayout] (= @Update) を使うこと。
     *   既存更新で誤って [insertLayout] を呼ぶと、stableId / createdAt / sortOrder などの
     *   identity が壊れ、`KeyAction.MoveToCustomKeyboard(stableId)` の参照が
     *   「削除済みのカスタムキーボード」になる。
     *
     * 重複ガード:
     * - 同じ layoutId を持つ row があれば失敗 (ABORT)。
     * - 同じ stableId を持つ row があれば unique index により失敗 (ABORT)。
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLayout(layout: CustomKeyboardLayout): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeys(keys: List<KeyDefinition>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlickMappings(flicks: List<FlickMapping>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCircularFlickMappings(flicks: List<CircularFlickMapping>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTwoStepFlickMappings(mappings: List<TwoStepFlickMapping>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLongPressFlickMappings(mappings: List<LongPressFlickMapping>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTwoStepLongPressFlickMappings(mappings: List<TwoStepLongPressMappingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpacers(spacers: List<SpacerDefinition>)

    @Query("DELETE FROM spacer_definitions WHERE ownerLayoutId = :layoutId")
    suspend fun deleteSpacersForLayout(layoutId: Long)

    @Query("DELETE FROM keyboard_layouts WHERE layoutId = :layoutId")
    suspend fun deleteLayout(layoutId: Long)

    @Update
    suspend fun updateLayout(layout: CustomKeyboardLayout)

    @Query("DELETE FROM key_definitions WHERE ownerLayoutId = :layoutId")
    suspend fun deleteKeysForLayout(layoutId: Long)

    @Query("DELETE FROM flick_mappings WHERE ownerKeyId IN (SELECT keyId FROM key_definitions WHERE ownerLayoutId = :layoutId)")
    suspend fun deleteFlicksForLayout(layoutId: Long)

    @Query("DELETE FROM circular_flick_mappings WHERE ownerKeyId IN (SELECT keyId FROM key_definitions WHERE ownerLayoutId = :layoutId)")
    suspend fun deleteCircularFlicksForLayout(layoutId: Long)

    @Query("DELETE FROM two_step_flick_mappings WHERE ownerKeyId IN (SELECT keyId FROM key_definitions WHERE ownerLayoutId = :layoutId)")
    suspend fun deleteTwoStepFlicksForLayout(layoutId: Long)

    @Query("DELETE FROM long_press_flick_mappings WHERE ownerKeyId IN (SELECT keyId FROM key_definitions WHERE ownerLayoutId = :layoutId)")
    suspend fun deleteLongPressFlicksForLayout(layoutId: Long)

    @Query("DELETE FROM two_step_long_press_mappings WHERE ownerKeyId IN (SELECT keyId FROM key_definitions WHERE ownerLayoutId = :layoutId)")
    suspend fun deleteTwoStepLongPressFlicksForLayout(layoutId: Long)

    @Transaction
    suspend fun deleteKeysAndFlicksForLayout(layoutId: Long) {
        deleteFlicksForLayout(layoutId)
        deleteCircularFlicksForLayout(layoutId)
        deleteTwoStepFlicksForLayout(layoutId)
        deleteLongPressFlicksForLayout(layoutId)
        deleteTwoStepLongPressFlicksForLayout(layoutId)
        deleteKeysForLayout(layoutId)
    }

    @Query("SELECT name FROM keyboard_layouts WHERE layoutId = :id")
    suspend fun getLayoutName(id: Long): String?

    @Transaction
    @Query("SELECT * FROM keyboard_layouts WHERE layoutId = :id")
    suspend fun getFullLayoutOneShot(id: Long): FullKeyboardLayout?

    @Query("SELECT * FROM keyboard_layouts WHERE name = :name LIMIT 1")
    suspend fun findLayoutByName(name: String): CustomKeyboardLayout?

    @Query("SELECT * FROM keyboard_layouts WHERE stableId = :stableId LIMIT 1")
    suspend fun findLayoutByStableId(stableId: String): CustomKeyboardLayout?

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
