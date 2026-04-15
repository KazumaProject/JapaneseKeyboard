package com.kazumaproject.listeners

import com.kazumaproject.core.data.clipboard.ClipboardItem

fun interface ClipboardItemLongClickListener {
    fun onAction(item: ClipboardItem, action: ClipboardItemAction)
}
