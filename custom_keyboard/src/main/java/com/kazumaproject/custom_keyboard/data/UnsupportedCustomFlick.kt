package com.kazumaproject.custom_keyboard.data

/** Compatibility-only key kinds. Custom layout data never authorizes built-in input. */
object UnsupportedCustomFlick {
    fun isUnsupported(type: KeyType?): Boolean = type == null || type in setOf(
        KeyType.TWO_STEP_FLICK, KeyType.STICKY_TWO_STEP_FLICK,
        KeyType.HIERARCHICAL_FLICK, KeyType.BUILT_IN_TWO_STEP
    )
}
