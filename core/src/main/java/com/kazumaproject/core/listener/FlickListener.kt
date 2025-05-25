package com.kazumaproject.core.listener

import com.kazumaproject.core.key.Key
import com.kazumaproject.core.state.GestureType

interface FlickListener {
    fun onFlick(
        gestureType: GestureType,
        key: Key,
        char: Char?
    )
}