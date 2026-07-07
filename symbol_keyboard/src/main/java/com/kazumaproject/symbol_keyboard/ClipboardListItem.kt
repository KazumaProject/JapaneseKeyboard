package com.kazumaproject.symbol_keyboard

import com.kazumaproject.core.data.clipboard.ClipboardItem

sealed class ClipboardListItem {
    data class Header(val title: String) : ClipboardListItem()
    data class Content(val item: ClipboardItem) : ClipboardListItem()
}
