package com.kazumaproject.custom_keyboard.controller

/**
 * Lifecycle used by input controllers that are reused while the keyboard layout stays alive.
 *
 * Resetting a touch stream must not detach the controller or cancel resources that are needed by
 * the next stream. [dispose] is reserved for the actual view/layout teardown path.
 */
interface GestureStateResettable {
    fun resetGestureState()
    fun dispose()
}
