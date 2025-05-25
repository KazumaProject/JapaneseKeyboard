package com.kazumaproject.tenkey.listener

import com.kazumaproject.core.key.Key

interface LongPressListener {
    fun onLongPress(
        key: Key
    )
}