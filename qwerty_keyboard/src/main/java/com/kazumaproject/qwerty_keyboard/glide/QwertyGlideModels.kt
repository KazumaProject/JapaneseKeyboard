package com.kazumaproject.qwerty_keyboard.glide

data class QwertyInputPointerPoint(
    val x: Int,
    val y: Int,
    val time: Int,
    val pointerId: Int
)

data class QwertyInputPointers(
    val points: List<QwertyInputPointerPoint>
)

data class QwertyKeyProximity(
    val char: Char,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val rowIndex: Int,
    val columnIndex: Int,
    val neighborChars: List<Char>
)

data class QwertyKeyboardProximityInfo(
    val keys: List<QwertyKeyProximity>,
    val keyboardWidth: Int,
    val keyboardHeight: Int,
    val averageKeyWidth: Float,
    val averageKeyHeight: Float
)

interface QwertyGlideInputListener {
    fun onQwertyGlideStarted()

    fun onQwertyGlideUpdated(
        inputPointers: QwertyInputPointers,
        proximityInfo: QwertyKeyboardProximityInfo
    )

    fun onQwertyGlideEnded(
        inputPointers: QwertyInputPointers,
        proximityInfo: QwertyKeyboardProximityInfo
    )

    fun onQwertyGlideCancelled()
}
