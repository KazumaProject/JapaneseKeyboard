package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

internal enum class LuminousBlobInputKind {
    Down,
    Move,
    Up
}

internal data class LuminousBlobActivePointer(
    val pointerId: Int,
    val x: Float,
    val y: Float,
    val velocityX: Float,
    val velocityY: Float,
    val colorSet: LuminousBlobColorSet,
    val downTimeMillis: Long,
    val lastEventTimeMillis: Long
)

internal data class LuminousBlobPointerSnapshot(
    val x: Float,
    val y: Float,
    val velocityX: Float,
    val velocityY: Float,
    val colorSet: LuminousBlobColorSet
)

internal sealed class LuminousBlobInputCommand {
    abstract val eventTimeMillis: Long

    data class Pointer(
        val pointerId: Int,
        val x: Float,
        val y: Float,
        val previousX: Float,
        val previousY: Float,
        val velocityX: Float,
        val velocityY: Float,
        val colorSet: LuminousBlobColorSet,
        val kind: LuminousBlobInputKind,
        override val eventTimeMillis: Long
    ) : LuminousBlobInputCommand()

    data class PointerUp(
        val pointerId: Int,
        override val eventTimeMillis: Long
    ) : LuminousBlobInputCommand()

    data class PointerCancel(
        val pointerId: Int,
        override val eventTimeMillis: Long
    ) : LuminousBlobInputCommand()

    data class CancelAll(
        override val eventTimeMillis: Long
    ) : LuminousBlobInputCommand()
}
