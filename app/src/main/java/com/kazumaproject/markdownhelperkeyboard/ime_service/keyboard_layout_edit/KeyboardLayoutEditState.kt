package com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_layout_edit

import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import kotlin.math.roundToInt

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
    QwertyFamily,
    Gojuon,
    Custom;

    companion object {
        fun from(mode: TenKeyQWERTYMode, customNumberLayout: Boolean = false): KeyboardLayoutEditTarget = when (mode) {
            TenKeyQWERTYMode.TenKeyQWERTY, TenKeyQWERTYMode.TenKeyQWERTYRomaji -> QwertyFamily
            TenKeyQWERTYMode.Gojuon -> Gojuon
            TenKeyQWERTYMode.Custom -> Custom
            TenKeyQWERTYMode.Number -> if (customNumberLayout) Custom else TenKeyFamily
            else -> TenKeyFamily
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
        // Independent slots store bottom margins in pixels; retain the original so an
        // unrelated edit does not quantize that value after converting it to dp.
        val originalBottomMarginPx: Int? = null,
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

    fun independentBottomMarginPixels(values: KeyboardLayoutEditValues.Normal, density: Float): Int {
        require(density > 0f && density.isFinite())
        val original = values.originalBottomMarginPx
        return if (original != null && values.bottomMarginDp == (original / density).roundToInt()) original
        else (values.bottomMarginDp * density).roundToInt().coerceAtLeast(0)
    }

    fun normalizeNormalForDraft(
        values: KeyboardLayoutEditValues.Normal,
    ): KeyboardLayoutEditValues.Normal {
        return values.copy(
            heightDp = values.heightDp.coerceIn(MinHeightDp, MaxHeightDp),
            widthPercent = normalizeWidthPercentForDraft(values.widthPercent),
            bottomMarginDp = values.bottomMarginDp.coerceAtLeast(MinMarginDp),
            marginStartDp = values.marginStartDp.coerceAtLeast(MinMarginDp),
            marginEndDp = values.marginEndDp.coerceAtLeast(MinMarginDp),
        )
    }

    fun normalizeNormalForCommit(
        values: KeyboardLayoutEditValues.Normal,
    ): KeyboardLayoutEditValues.Normal {
        return normalizeNormalForDraft(values).copy(
            widthPercent = normalizeWidthPercentForCommit(values.widthPercent),
        )
    }

    fun normalizeFloatingForDraft(
        values: KeyboardLayoutEditValues.Floating,
    ): KeyboardLayoutEditValues.Floating {
        return values.copy(
            heightDp = values.heightDp.coerceIn(MinFloatingHeightDp, MaxFloatingHeightDp),
            widthPercent = normalizeWidthPercentForDraft(values.widthPercent),
        )
    }

    fun normalizeFloatingForCommit(
        values: KeyboardLayoutEditValues.Floating,
    ): KeyboardLayoutEditValues.Floating {
        return normalizeFloatingForDraft(values).copy(
            widthPercent = normalizeWidthPercentForCommit(values.widthPercent),
        )
    }

    fun normalizeNormal(values: KeyboardLayoutEditValues.Normal): KeyboardLayoutEditValues.Normal {
        return normalizeNormalForCommit(values)
    }

    fun normalizeFloating(
        values: KeyboardLayoutEditValues.Floating,
    ): KeyboardLayoutEditValues.Floating {
        return normalizeFloatingForCommit(values)
    }

    fun normalizeWidthPercentForDraft(widthPercent: Int): Int {
        return widthPercent.coerceIn(MinWidthPercent, MaxWidthPercent)
    }

    fun normalizeWidthPercentForCommit(widthPercent: Int): Int {
        val clamped = widthPercent.coerceIn(MinWidthPercent, MaxWidthPercent)
        return if (clamped >= FullWidthThresholdPercent) MaxWidthPercent else clamped
    }

    fun normalizeWidthPercent(widthPercent: Int): Int {
        return normalizeWidthPercentForCommit(widthPercent)
    }
}
