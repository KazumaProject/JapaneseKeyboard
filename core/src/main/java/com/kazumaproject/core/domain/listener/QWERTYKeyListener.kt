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

    /**
     * ソフトウェア QWERTY の Shift キー操作後の状態を通知します。
     *
     * 通常の文字入力で one-shot Shift が自動解除される場合は通知しません。
     * そのため、入力側は Shift キーを明示的に解除したときだけ、ソフトウェア
     * Shift に由来する一時的なローマ字入力状態を解除できます。
     */
    fun onQWERTYShiftStateChanged(
        capsLockOn: Boolean,
        shiftOn: Boolean
    ) = Unit

    fun onLongPressQWERTYKey(qwertyKey: QWERTYKey)

    /**
     * QWERTYキーが上フリックされたときに呼び出されます。
     * (setFlickUpDetectionEnabled(true) が設定されている場合のみ)
     *
     * @param qwertyKey フリックジェスチャーが開始されたキー
     */
    fun onFlickUPQWERTYKey(
        qwertyKey: QWERTYKey,
        tap: Char?,
        variations: List<Char>?
    )

    /**
     * QWERTYキーが下フリックされたときに呼び出されます。
     * (setFlickDownDetectionEnabled(true) が設定されている場合のみ)
     *
     * @param qwertyKey フリックジェスチャーが開始されたキー
     * @param character 下フリックで入力される大文字
     */
    fun onFlickDownQWERTYKey(
        qwertyKey: QWERTYKey,
        character: Char
    )
}
