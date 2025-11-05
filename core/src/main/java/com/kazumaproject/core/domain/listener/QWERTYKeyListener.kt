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

    /**
     * QWERTYキーが上フリックされたときに呼び出されます。
     * (setFlickDetectionEnabled(true) が設定されている場合のみ)
     *
     * @param qwertyKey フリックジェスチャーが開始されたキー
     */
    fun onFlickUPQWERTYKey(
        qwertyKey: QWERTYKey,
        tap: Char?,
        variations: List<Char>?
    )
}
