package com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.data.FlickMapping
import com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.data.FullKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.data.KeyDefinition
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyboardLayoutDao {

    /**
     * 保存されているすべてのキーボードレイアウトのリストを取得する (名前などの基本情報のみ)
     * キーボード選択画面などで使用
     */
    @Query("SELECT * FROM keyboard_layouts ORDER BY createdAt DESC")
    fun getLayoutsList(): Flow<List<CustomKeyboardLayout>>

    @Query("SELECT * FROM keyboard_layouts ORDER BY createdAt DESC")
    suspend fun getLayoutsListNotFlow(): List<CustomKeyboardLayout>

    /**
     * 指定したIDのキーボードレイアウトを、キーとフリック情報を含めてすべて取得する
     */
    @Transaction
    @Query("SELECT * FROM keyboard_layouts WHERE layoutId = :id")
    fun getFullLayoutById(id: Long): Flow<FullKeyboardLayout>

    /**
     * 新しいキーボードレイアウトをデータベースに保存する
     * トランザクション内で実行することで、途中で失敗した場合にすべての変更がロールバックされる
     */
    @Transaction
    suspend fun insertFullKeyboardLayout(
        layout: CustomKeyboardLayout, keys: List<KeyDefinition>, flicks: List<FlickMapping>
    ) {
        val layoutId = insertLayout(layout)
        // キーに正しい ownerLayoutId を設定
        val keysWithLayoutId = keys.map { it.copy(ownerLayoutId = layoutId) }
        val keyIds = insertKeys(keysWithLayoutId) // 新しく挿入されたキーのIDリストが返る

        // フリック設定に正しい ownerKeyId を設定
        // keysWithLayoutId と keyIds は同じ順序であることが前提
        val flicksWithKeyIds = mutableListOf<FlickMapping>()
        keysWithLayoutId.forEachIndexed { index, keyDef ->
            flicks.filter { it.ownerKeyId.toString() == keyDef.keyIdentifier } // 文字列の仮IDでフィルタ
                .forEach { flick ->
                    flicksWithKeyIds.add(flick.copy(ownerKeyId = keyIds[index])) // 本物のIDに差し替え
                }
        }
        insertFlickMappings(flicksWithKeyIds)
    }

    // DAO内で使用するprivateなヘルパーメソッド
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

    /**
     * IDを指定して、Flowではない単発のデータとして完全なレイアウトを取得します（複製用）
     */
    @Transaction
    @Query("SELECT * FROM keyboard_layouts WHERE layoutId = :id")
    suspend fun getFullLayoutOneShot(id: Long): FullKeyboardLayout?

    /**
     * 指定された名前のレイアウトを検索します。
     */
    @Query("SELECT * FROM keyboard_layouts WHERE name = :name LIMIT 1")
    suspend fun findLayoutByName(name: String): CustomKeyboardLayout?

    @Transaction
    @Query("SELECT * FROM keyboard_layouts")
    fun getAllFullLayouts(): Flow<List<FullKeyboardLayout>>

}
