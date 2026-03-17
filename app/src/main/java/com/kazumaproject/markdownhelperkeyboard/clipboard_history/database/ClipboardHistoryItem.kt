package com.kazumaproject.markdownhelperkeyboard.clipboard_history.database

import androidx.room.Entity
import androidx.room.PrimaryKey

// アイテムの種類を区別するためのEnum
enum class ItemType {
    TEXT,
    IMAGE
}

@Entity(tableName = "clipboard_history")
data class ClipboardHistoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val itemType: ItemType,
    val preview: String,      // 一覧表示用の短い文字列
    val contentPath: String,   // ファイルの絶対パス
    val timestamp: Long = System.currentTimeMillis()
)
