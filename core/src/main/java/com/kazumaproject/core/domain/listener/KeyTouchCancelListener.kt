package com.kazumaproject.core.domain.listener

import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.qwerty.QWERTYKey

enum class KeyTouchCancelReason {
    ActionCancel,
    DetachedFromWindow,
    PointerInterrupted,
    ViewHidden
}

interface KeyTouchCancelListener {
    fun onKeyTouchCanceled(
        key: Key,
        reason: KeyTouchCancelReason
    )
}

interface QwertyKeyTouchCancelListener {
    fun onQwertyKeyTouchCanceled(
        key: QWERTYKey,
        reason: KeyTouchCancelReason
    )
}
