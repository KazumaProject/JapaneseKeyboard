package com.kazumaproject.tenkey.listener

import com.kazumaproject.tenkey.state.GestureType
import com.kazumaproject.tenkey.state.Key

interface FlickListener {
    fun onFlick(
        gestureType: GestureType,
        key: Key,
        char: Char?
    )
}