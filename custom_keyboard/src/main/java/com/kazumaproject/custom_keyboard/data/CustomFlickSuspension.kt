package com.kazumaproject.custom_keyboard.data

/** Selected by the caller, never restored from a layout backup. */
enum class KeyboardLayoutOrigin { CUSTOM, BUILT_IN }

/** Storage identifiers remain readable while their custom input implementations are suspended. */
object CustomFlickSuspension {
    fun isSuspended(type: KeyType?, origin: KeyboardLayoutOrigin = KeyboardLayoutOrigin.CUSTOM): Boolean =
        type == null || type == KeyType.HIERARCHICAL_FLICK || type == KeyType.STICKY_TWO_STEP_FLICK ||
            (type == KeyType.TWO_STEP_FLICK && origin != KeyboardLayoutOrigin.BUILT_IN)
}
