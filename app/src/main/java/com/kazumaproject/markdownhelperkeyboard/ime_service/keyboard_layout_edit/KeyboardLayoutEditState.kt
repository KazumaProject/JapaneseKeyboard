package com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_layout_edit

import com.kazumaproject.core.domain.state.TenKeyQWERTYMode

sealed interface KeyboardLayoutEditState {
    data object Disabled : KeyboardLayoutEditState

    data class Enabled(
        val surface: KeyboardLayoutEditSurface,
        val target: KeyboardLayoutEditTarget,
        val orientation: KeyboardLayoutEditOrientation,
        val values: KeyboardLayoutEditValues,
    ) : KeyboardLayoutEditState
}

enum class KeyboardLayoutEditSurface {
    Normal,
    Floating,
}

enum class KeyboardLayoutEditTarget {
    TenKeyFamily,
    QwertyFamily;

    companion object {
        fun from(mode: TenKeyQWERTYMode): KeyboardLayoutEditTarget {
            return if (mode == TenKeyQWERTYMode.TenKeyQWERTY ||
                mode == TenKeyQWERTYMode.TenKeyQWERTYRomaji
            ) {
                QwertyFamily
            } else {
                TenKeyFamily
            }
        }
    }
}

enum class KeyboardLayoutEditOrientation {
    Portrait,
    Landscape;

    companion object {
        fun from(isPortrait: Boolean): KeyboardLayoutEditOrientation {
            return if (isPortrait) Portrait else Landscape
        }
    }
}

sealed interface KeyboardLayoutEditValues {
    data class Normal(
        val heightDp: Int,
        val widthPercent: Int,
        val bottomMarginDp: Int,
        val marginStartDp: Int,
        val marginEndDp: Int,
        val positionIsEnd: Boolean,
    ) : KeyboardLayoutEditValues

    data class Floating(
        val heightDp: Int,
        val widthPercent: Int,
    ) : KeyboardLayoutEditValues
}

data class KeyboardLayoutEditPreferenceSlot(
    val orientation: KeyboardLayoutEditOrientation,
    val target: KeyboardLayoutEditTarget,
)

object KeyboardLayoutEditConstraints {
    const val MinHeightDp = 100
    const val MaxHeightDp = 420
    const val MinFloatingHeightDp = 60
    const val MaxFloatingHeightDp = 420
    const val MinWidthPercent = 32
    const val MaxWidthPercent = 100
    const val FullWidthThresholdPercent = 98
    const val MinMarginDp = 0
    const val DefaultHeightDp = 220
    const val DefaultWidthPercent = 100
    const val DefaultMarginDp = 0

    fun normalizeNormal(
        values: KeyboardLayoutEditValues.Normal,
    ): KeyboardLayoutEditValues.Normal {
        return values.copy(
            heightDp = values.heightDp.coerceIn(MinHeightDp, MaxHeightDp),
            widthPercent = normalizeWidthPercent(values.widthPercent),
            bottomMarginDp = values.bottomMarginDp.coerceAtLeast(MinMarginDp),
            marginStartDp = values.marginStartDp.coerceAtLeast(MinMarginDp),
            marginEndDp = values.marginEndDp.coerceAtLeast(MinMarginDp),
        )
    }

    fun normalizeFloating(
        values: KeyboardLayoutEditValues.Floating,
    ): KeyboardLayoutEditValues.Floating {
        return values.copy(
            heightDp = values.heightDp.coerceIn(MinFloatingHeightDp, MaxFloatingHeightDp),
            widthPercent = normalizeWidthPercent(values.widthPercent),
        )
    }

    fun normalizeWidthPercent(widthPercent: Int): Int {
        val clamped = widthPercent.coerceIn(MinWidthPercent, MaxWidthPercent)
        return if (clamped >= FullWidthThresholdPercent) MaxWidthPercent else clamped
    }
}
