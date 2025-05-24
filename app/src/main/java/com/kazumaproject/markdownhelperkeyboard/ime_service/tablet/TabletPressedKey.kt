package com.kazumaproject.markdownhelperkeyboard.ime_service.tablet

data class TabletPressedKey(
    var tabletKey: TabletKey,
    var pointer: Int,
    var initialX: Float,
    var initialY: Float,
)
