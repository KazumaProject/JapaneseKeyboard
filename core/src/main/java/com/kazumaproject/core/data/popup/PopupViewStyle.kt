package com.kazumaproject.core.data.popup

data class PopupViewStyle(
    val sizeScalePercent: Int,
    val textSizeSp: Float,
    val backgroundColor: Int? = null,
    val textColor: Int? = null
)

data class QwertyPopupViewStyleSet(
    val keyPreview: PopupViewStyle,
    val variation: PopupViewStyle
)

data class FlickPopupViewStyleSet(
    val directional: PopupViewStyle,
    val cross: PopupViewStyle,
    val standard: PopupViewStyle,
    val tfbi: PopupViewStyle
)
