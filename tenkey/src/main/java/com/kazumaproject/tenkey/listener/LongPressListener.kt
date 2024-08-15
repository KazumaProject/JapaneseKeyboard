package com.kazumaproject.tenkey.listener

import com.kazumaproject.tenkey.state.Key

interface LongPressListener {
    fun onLongPress(
        key: Key
    )
}