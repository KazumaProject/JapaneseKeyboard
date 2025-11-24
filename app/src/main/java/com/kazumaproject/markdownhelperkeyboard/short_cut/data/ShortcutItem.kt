package com.kazumaproject.markdownhelperkeyboard.short_cut.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shortcut_items")
data class ShortcutItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val typeId: String, // ShortcutType.id を保存
    val sortOrder: Int // 並び順
)
