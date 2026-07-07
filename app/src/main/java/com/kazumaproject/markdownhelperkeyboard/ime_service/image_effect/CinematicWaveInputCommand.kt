package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

internal enum class CinematicWaveInputKind {
    Down,
    Move,
    Up
}

internal data class CinematicWaveTouchPoint(
    val pointerId: Int,
    var x: Float,
    var y: Float,
    var startTimeMs: Long,
    var lastUpdateTimeMs: Long,
    var pressure: Float,
    var strength: Float
)

internal data class CinematicWaveTouchSnapshot(
    val x: Float,
    val y: Float,
    val ageSeconds: Float,
    val strength: Float
)

internal sealed class CinematicWaveInputCommand {
    abstract val eventTimeMs: Long

    data class Pointer(
        val pointerId: Int,
        val x: Float,
        val y: Float,
        val pressure: Float,
        val kind: CinematicWaveInputKind,
        override val eventTimeMs: Long
    ) : CinematicWaveInputCommand()

    data class PointerUp(
        val pointerId: Int,
        override val eventTimeMs: Long
    ) : CinematicWaveInputCommand()

    data class PointerCancel(
        val pointerId: Int,
        override val eventTimeMs: Long
    ) : CinematicWaveInputCommand()

    data class CancelAll(
        override val eventTimeMs: Long
    ) : CinematicWaveInputCommand()
}
