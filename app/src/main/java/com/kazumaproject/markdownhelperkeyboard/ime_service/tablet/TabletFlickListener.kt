package com.kazumaproject.markdownhelperkeyboard.ime_service.tablet

import com.kazumaproject.tenkey.state.GestureType

interface TabletFlickListener {
    fun onFlick(
        gestureType: GestureType,
        tabletKey: TabletKey,
        char: Char?
    )
}