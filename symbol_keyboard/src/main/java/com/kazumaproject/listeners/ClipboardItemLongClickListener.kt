package com.kazumaproject.listeners

import com.kazumaproject.core.data.clipboard.ClipboardItem

fun interface ClipboardItemLongClickListener {
    fun onLongClick(item: ClipboardItem, position: Int)
}
