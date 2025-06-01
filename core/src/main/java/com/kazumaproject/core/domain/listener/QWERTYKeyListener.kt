package com.kazumaproject.core.domain.listener

import com.kazumaproject.core.domain.qwerty.QWERTYKey

interface QWERTYKeyListener {
    fun onPressedQWERTYKey(
        qwertyKey: QWERTYKey,
    )

    fun onReleasedQWERTYKey(
        qwertyKey: QWERTYKey,
        tap: Char?,
        variations: List<Char>?
    )

    fun onLongPressQWERTYKey(qwertyKey: QWERTYKey)

    fun onVariationSelected(selectedChar: Char)
}
