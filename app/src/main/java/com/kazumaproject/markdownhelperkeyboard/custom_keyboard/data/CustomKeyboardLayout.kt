package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kazumaproject.custom_keyboard.data.KeyboardLayoutUsageMode
import java.util.UUID

/**
 * ユーザーが作成したキーボードレイアウトの全体設定を保存するエンティティ
 *
 * stableId は MoveToCustomKeyboard の永続参照 ID。layoutId とは別に管理し、
 * 編集保存・名前変更・キー変更などで決して書き換えてはいけない。
 *
 * stableId に unique index を張ることで、同一の stableId を持つ row が DB 上で
 * 複数存在しないことを保証する。これにより
 * `KeyAction.MoveToCustomKeyboard(stableId)` の解決結果が一意になる。
 */
@Entity(
    tableName = "keyboard_layouts",
    indices = [
        Index(value = ["stableId"], unique = true)
    ]
)
data class CustomKeyboardLayout(
    @PrimaryKey(autoGenerate = true)
    val layoutId: Long = 0,
    val name: String,         // キーボード名 (例: "自分用ひらがな")
    val columnCount: Int,     // 列数
    val rowCount: Int,        // 行数
    val isRomaji: Boolean = false,
    val isDirectMode: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(), // 作成日時
    val sortOrder: Int = 0,
    val stableId: String = UUID.randomUUID().toString(),
    val isFlexiblePlacementLayout: Boolean = false,
    val usageMode: KeyboardLayoutUsageMode = KeyboardLayoutUsageMode.Normal
)
