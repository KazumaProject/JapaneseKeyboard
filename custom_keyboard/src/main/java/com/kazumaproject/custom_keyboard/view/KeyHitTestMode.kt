package com.kazumaproject.custom_keyboard.view

/** Input surface policy; custom layouts retain their intentional untouchable gaps by default. */
enum class KeyHitTestMode {
    KEY_BOUNDS,
    NEAREST_KEY
}
