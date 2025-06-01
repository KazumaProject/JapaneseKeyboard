package com.kazumaproject.core.domain.listener

import com.kazumaproject.core.domain.qwerty.QWERTYKey

interface QWERTYKeyListener {
    fun onTouchQWERTYKey(
        qwertyKey: QWERTYKey
    )
}
