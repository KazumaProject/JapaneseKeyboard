package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType

data class KeyboardDisplayResolution(
    val requested: KeyboardType?,
    val savedPosition: Int?,
    val savedPositionKeyboard: KeyboardType?,
    val keyboardOrder: List<KeyboardType>,
    val resolvedKeyboard: KeyboardType,
    val resolvedIndex: Int?,
    val requestedMissingFromOrder: Boolean,
    val savedPositionOutOfRange: Boolean,
    val usedEmptyOrderFallback: Boolean
)

fun resolveKeyboardDisplay(
    requested: KeyboardType?,
    keyboardOrder: List<KeyboardType>,
    savedPosition: Int? = null
): KeyboardDisplayResolution {
    val savedPositionKeyboard = savedPosition?.let { keyboardOrder.getOrNull(it) }
    val requestedKeyboard = requested ?: savedPositionKeyboard
    val usedEmptyOrderFallback = keyboardOrder.isEmpty()
    val resolvedKeyboard = when {
        usedEmptyOrderFallback -> KeyboardType.TENKEY
        requestedKeyboard != null && requestedKeyboard in keyboardOrder -> requestedKeyboard
        else -> keyboardOrder.first()
    }
    val resolvedIndex = keyboardOrder.indexOf(resolvedKeyboard).takeIf { it >= 0 }

    return KeyboardDisplayResolution(
        requested = requestedKeyboard,
        savedPosition = savedPosition,
        savedPositionKeyboard = savedPositionKeyboard,
        keyboardOrder = keyboardOrder,
        resolvedKeyboard = resolvedKeyboard,
        resolvedIndex = resolvedIndex,
        requestedMissingFromOrder =
            requestedKeyboard != null && requestedKeyboard !in keyboardOrder,
        savedPositionOutOfRange =
            savedPosition != null && savedPosition !in keyboardOrder.indices,
        usedEmptyOrderFallback = usedEmptyOrderFallback
    )
}
