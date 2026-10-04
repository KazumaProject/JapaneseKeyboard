package com.kazumaproject.custom_keyboard.view

/** Controls which parts of a keyboard surface can be assigned to the nearest key. */
enum class KeyHitTestMode {
    KEY_BOUNDS,
    NEAREST_KEY,
    NEAREST_KEY_IN_KEY_CELLS
}
