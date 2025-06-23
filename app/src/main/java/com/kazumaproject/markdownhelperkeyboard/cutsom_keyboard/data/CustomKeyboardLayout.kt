package com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ユーザーが作成したキーボードレイアウトの全体設定を保存するエンティティ
 */
@Entity(tableName = "keyboard_layouts")
data class CustomKeyboardLayout(
    @PrimaryKey(autoGenerate = true)
    val layoutId: Long = 0,
    val name: String,         // キーボード名 (例: "自分用ひらがな")
    val columnCount: Int,     // 列数
    val rowCount: Int,        // 行数
    val createdAt: Long = System.currentTimeMillis() // 作成日時
)
