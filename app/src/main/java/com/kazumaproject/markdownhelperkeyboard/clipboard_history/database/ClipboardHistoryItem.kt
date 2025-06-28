package com.kazumaproject.markdownhelperkeyboard.clipboard_history.database

import android.graphics.Bitmap
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
    val textData: String?,   // テキスト用
    val imageData: Bitmap?, // 画像用
    val timestamp: Long = System.currentTimeMillis()
)
