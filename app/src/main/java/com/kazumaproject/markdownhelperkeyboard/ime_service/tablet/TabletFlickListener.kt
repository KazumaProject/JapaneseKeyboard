package com.kazumaproject.markdownhelperkeyboard.ime_service.tablet

import com.kazumaproject.core.state.GestureType

interface TabletFlickListener {
    fun onFlick(
        gestureType: GestureType,
        tabletKey: TabletKey,
        char: Char?
    )
}