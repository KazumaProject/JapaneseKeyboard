package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

internal data class LiquidRippleSettings(
    val enabled: Boolean,
    val quality: String = KeyboardTouchEffectQuality.HIGH
) {
    val normalizedQuality: String = KeyboardTouchEffectQuality.normalize(quality)

    companion object {
        val Disabled = LiquidRippleSettings(
            enabled = false,
            quality = KeyboardTouchEffectQuality.HIGH
        )
    }
}

internal enum class LiquidRippleImpulseKind {
    Down,
    Move,
    Up
}

internal sealed class LiquidRippleInputCommand {
    abstract val eventTimeMillis: Long

    data class Impulse(
        val pointerId: Int,
        val x: Float,
        val y: Float,
        val strength: Float,
        val radiusPx: Float,
        val kind: LiquidRippleImpulseKind,
        override val eventTimeMillis: Long,
        val directionX: Float = 0f,
        val directionY: Float = 0f
    ) : LiquidRippleInputCommand()

    data class PointerUp(
        val pointerId: Int,
        override val eventTimeMillis: Long
    ) : LiquidRippleInputCommand()

    data class PointerCancel(
        val pointerId: Int,
        override val eventTimeMillis: Long
    ) : LiquidRippleInputCommand()

    data class CancelAll(
        override val eventTimeMillis: Long
    ) : LiquidRippleInputCommand()
}
