package com.kazumaproject.markdownhelperkeyboard.clipboard_history.dto

import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ItemType

data class ClipboardHistoryDto(
    val itemType: ItemType,
    val textData: String?,
    val imageDataBase64: String?, // BitmapをBase64文字列で保持
    val timestamp: Long
)
