package com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.data

import androidx.room.Entity
import androidx.room.ForeignKey
import com.kazumaproject.custom_keyboard.data.FlickDirection

/**
 * フリック操作のマッピング情報を保存するエンティティ
 * どのキーに属するかを示すために、外部キー(ownerKeyId)を持つ
 */
@Entity(
    tableName = "flick_mappings",
    primaryKeys = ["ownerKeyId", "stateIndex", "flickDirection"], // 複合主キー
    foreignKeys = [ForeignKey(
        entity = KeyDefinition::class,
        parentColumns = ["keyId"],
        childColumns = ["ownerKeyId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class FlickMapping(
    val ownerKeyId: Long,          // 外部キー (どの KeyDefinition に属するか)
    val stateIndex: Int = 0,       // 状態インデックス (例: 「は」「ば」「ぱ」の切り替え)
    val flickDirection: FlickDirection, // フリック方向 (TAP, UP, LEFTなど)
    val actionType: String,        // KeyActionの種類を文字列で保存 (例: "INPUT_TEXT", "DELETE")
    val actionValue: String?       // actionTypeに対応する値 (例: "あ", "^_^")
)
