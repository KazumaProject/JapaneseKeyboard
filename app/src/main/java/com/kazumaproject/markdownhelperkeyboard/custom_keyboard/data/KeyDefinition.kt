package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kazumaproject.custom_keyboard.data.KeyType

/**
 * 個々のキーの定義を保存するエンティティ
 * どのレイアウトに属するかを示すために、外部キー(ownerLayoutId)を持つ
 */
@Entity(
    tableName = "key_definitions",
    foreignKeys = [ForeignKey(
        entity = CustomKeyboardLayout::class,
        parentColumns = ["layoutId"],
        childColumns = ["ownerLayoutId"],
        onDelete = ForeignKey.CASCADE // 親レイアウトが削除されたら、このキーも削除する
    )],
    indices = [Index(value = ["ownerLayoutId"])]
)
data class KeyDefinition(
    @PrimaryKey(autoGenerate = true)
    val keyId: Long = 0,
    val ownerLayoutId: Long,  // 外部キー (どの CustomKeyboardLayout に属するか)
    val label: String,        // キーの表示ラベル (例: "あ", "Del")
    val row: Int,             // キーの行位置
    val column: Int,          // キーの列位置
    val rowSpan: Int = 1,     // キーの縦幅
    val colSpan: Int = 1,     // キーの横幅
    val keyType: KeyType,     // キーの種類 (NORMAL, CROSS_FLICKなど)
    val isSpecialKey: Boolean = false,
    val drawableResId: Int? = null,
    val keyIdentifier: String,
    val action: String? = null
)
