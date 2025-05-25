package com.kazumaproject.core.domain.listener

import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.state.GestureType

interface FlickListener {
    fun onFlick(
        gestureType: GestureType,
        key: Key,
        char: Char?
    )
}