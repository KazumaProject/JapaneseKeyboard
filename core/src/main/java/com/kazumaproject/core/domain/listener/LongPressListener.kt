package com.kazumaproject.core.domain.listener

import com.kazumaproject.core.domain.key.Key

interface LongPressListener {
    fun onLongPress(
        key: Key
    )
}