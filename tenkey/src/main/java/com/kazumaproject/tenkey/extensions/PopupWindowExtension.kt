package com.kazumaproject.tenkey.extensions

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.PopupWindow
import androidx.core.graphics.drawable.toDrawable
import com.kazumaproject.core.ui.key_window.ArrowDirection
import com.kazumaproject.core.ui.key_window.KeyWindowLayout
import com.kazumaproject.tenkey.R

private fun Int.scaledPopupSize(sizeScalePercent: Int): Int {
    val scale = sizeScalePercent.coerceIn(50, 200) / 100f
    return (this * scale).toInt().coerceAtLeast(1)
}

private fun calculateCenteredXOffset(popupWidth: Int, anchorWidth: Int): Int {
    return -((popupWidth - anchorWidth) / 2)
}

// 上フリックの y-offset は popup の拡大後 height に依存させない。
// sizeScalePercent によって popup サイズだけが変わり、表示基準位置は anchorView 基準で維持する。
private fun calculateFlickTopYOffset(anchorHeight: Int): Int {
    return -(anchorHeight * 2) - 16
}

private fun isPortraitBottomRowAnchor(anchorId: Int, orientation: Int): Boolean {
    return orientation != Configuration.ORIENTATION_LANDSCAPE &&
        (anchorId == R.id.key_small_letter || anchorId == R.id.key_11 || anchorId == R.id.key_12)
}

internal enum class LongPressPopupPosition {
    TOP,
    LEFT,
    CENTER,
    RIGHT,
    BOTTOM
}

internal fun calculateLongPressPopupYOffset(
    position: LongPressPopupPosition,
    anchorId: Int,
    orientation: Int,
    popupHeight: Int,
    anchorHeight: Int
): Int {
    val centeredOnAnchor = -((popupHeight + anchorHeight) / 2)
    val isBottomRow = isPortraitBottomRowAnchor(anchorId, orientation)
    val guideStep = (popupHeight + anchorHeight) / 2
    return when (position) {
        LongPressPopupPosition.TOP -> -popupHeight - anchorHeight - if (isBottomRow) guideStep else 0
        LongPressPopupPosition.BOTTOM -> if (isBottomRow) centeredOnAnchor else 0
        LongPressPopupPosition.LEFT,
        LongPressPopupPosition.CENTER,
        LongPressPopupPosition.RIGHT -> centeredOnAnchor - if (isBottomRow) guideStep else 0
    }
}

internal data class FlickBottomPopupPlacement(
    val arrowDirection: ArrowDirection,
    val xOffset: Int,
    val yOffset: Int
)

internal fun calculateFlickBottomPopupPlacement(
    anchorId: Int,
    orientation: Int,
    popupWidth: Int,
    anchorWidth: Int,
    anchorHeight: Int
): FlickBottomPopupPlacement {
    val isPortraitBottomRow = isPortraitBottomRowAnchor(anchorId, orientation)
    return FlickBottomPopupPlacement(
        arrowDirection = if (isPortraitBottomRow) {
            ArrowDirection.BOTTOM_CENTER
        } else {
            ArrowDirection.TOP_CENTER
        },
        xOffset = calculateCenteredXOffset(popupWidth, anchorWidth),
        yOffset = if (isPortraitBottomRow) {
            calculateFlickTopYOffset(anchorHeight)
        } else {
            -(anchorHeight / 2) - 8
        }
    )
}

fun PopupWindow.setPopUpWindowFlickRight(
    context: Context,
    keyWindowLayout: KeyWindowLayout,
    anchorView: View,
    sizeScalePercent: Int = 100
) {
    val baseWidth = anchorView.width + (anchorView.width) / 2 + 24
    val baseHeight = anchorView.height
    this.width = baseWidth.scaledPopupSize(sizeScalePercent)
    this.height = baseHeight.scaledPopupSize(sizeScalePercent)
    this.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
    keyWindowLayout.let { bubble ->
        if (bubble.arrowDirection != ArrowDirection.LEFT_CENTER) this.dismiss()
        bubble.arrowDirection = ArrowDirection.LEFT_CENTER
        bubble.arrowHeight = anchorView.height.toFloat() - 5
        bubble.arrowWidth = (anchorView.width / 2).toFloat() - 8
        bubble.cornersRadius = 10f
    }
    when (context.resources.configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT -> {
            showAsDropDown(
                anchorView,
                anchorView.width - ((width - anchorView.width) / 2) - 10,
                -(anchorView.height),
                Gravity.CENTER
            )
        }

        Configuration.ORIENTATION_LANDSCAPE -> {
            showAsDropDown(
                anchorView,
                anchorView.width - ((width - anchorView.width) / 2) - 10,
                -(anchorView.height),
                Gravity.CENTER
            )
        }

        Configuration.ORIENTATION_UNDEFINED -> {
            showAsDropDown(
                anchorView,
                anchorView.width - ((width - anchorView.width) / 2) - 10,
                -(anchorView.height),
                Gravity.CENTER
            )
        }

        else -> {}
    }
}

fun PopupWindow.setPopUpWindowFlickLeft(
    context: Context,
    keyWindowLayout: KeyWindowLayout,
    anchorView: View,
    sizeScalePercent: Int = 100
) {
    val baseWidth = anchorView.width + (anchorView.width) / 2 + 24
    val baseHeight = anchorView.height
    this.width = baseWidth.scaledPopupSize(sizeScalePercent)
    this.height = baseHeight.scaledPopupSize(sizeScalePercent)
    this.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
    keyWindowLayout.let { bubble ->
        if (bubble.arrowDirection != ArrowDirection.RIGHT_CENTER) this.dismiss()
        bubble.arrowDirection = ArrowDirection.RIGHT_CENTER
        bubble.arrowHeight = anchorView.height.toFloat() - 5
        bubble.arrowWidth = (anchorView.width / 2).toFloat() - 8
        bubble.cornersRadius = 10f
    }
    when (context.resources.configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT -> {
            showAsDropDown(
                anchorView,
                -width + (anchorView.width / 2) - 14,
                -(anchorView.height),
                Gravity.CENTER
            )
        }

        Configuration.ORIENTATION_LANDSCAPE -> {
            showAsDropDown(
                anchorView,
                -width + (anchorView.width / 2) - 14,
                -(anchorView.height),
                Gravity.CENTER
            )
        }

        Configuration.ORIENTATION_UNDEFINED -> {
            showAsDropDown(
                anchorView,
                -width + (anchorView.width / 2) - 14,
                -(anchorView.height),
                Gravity.CENTER
            )
        }

        else -> {

        }
    }
}


fun PopupWindow.setPopUpWindowFlickBottom(
    context: Context,
    keyWindowLayout: KeyWindowLayout,
    anchorView: View,
    sizeScalePercent: Int = 100
) {
    val baseWidth = anchorView.width
    val baseHeight = anchorView.height + (anchorView.height / 2) + 24
    this.width = baseWidth.scaledPopupSize(sizeScalePercent)
    this.height = baseHeight.scaledPopupSize(sizeScalePercent)
    this.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
    val placement = calculateFlickBottomPopupPlacement(
        anchorId = anchorView.id,
        orientation = context.resources.configuration.orientation,
        popupWidth = width,
        anchorWidth = anchorView.width,
        anchorHeight = anchorView.height
    )
    keyWindowLayout.let { bubble ->
        if (bubble.arrowDirection != placement.arrowDirection) this.dismiss()
        bubble.arrowDirection = placement.arrowDirection
        bubble.arrowHeight = (anchorView.height / 2).toFloat() - 8
        bubble.arrowWidth = anchorView.width.toFloat() - 10
        bubble.cornersRadius = 20f
    }
    when (context.resources.configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT,
        Configuration.ORIENTATION_LANDSCAPE,
        Configuration.ORIENTATION_UNDEFINED -> showAsDropDown(
            anchorView,
            placement.xOffset,
            placement.yOffset,
            Gravity.CENTER
        )

        else -> {
        }
    }
}

fun PopupWindow.setPopUpWindowFlickTop(
    context: Context,
    keyWindowLayout: KeyWindowLayout,
    anchorView: View,
    sizeScalePercent: Int = 100
) {
    val baseWidth = anchorView.width
    val baseHeight = anchorView.height + (anchorView.height / 2) + 24
    this.width = baseWidth.scaledPopupSize(sizeScalePercent)
    this.height = baseHeight.scaledPopupSize(sizeScalePercent)
    this.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
    keyWindowLayout.let { bubble ->
        if (bubble.arrowDirection != ArrowDirection.BOTTOM_CENTER) this.dismiss()
        bubble.arrowDirection = ArrowDirection.BOTTOM_CENTER
        bubble.arrowHeight = (anchorView.height / 2).toFloat() - 8
        bubble.arrowWidth = anchorView.width.toFloat() - 10
        bubble.cornersRadius = 20f
    }
    // 上フリック popup の y-offset は popup の拡大後 height に依存させない。
    // sizeScalePercent によって popup サイズだけが変わり、表示基準位置は anchorView 基準で維持する。
    val xOffset = calculateCenteredXOffset(width, anchorView.width)
    val yOffset = calculateFlickTopYOffset(anchorView.height)
    when (context.resources.configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT -> {
            showAsDropDown(
                anchorView,
                xOffset,
                yOffset,
                Gravity.CENTER
            )
        }

        Configuration.ORIENTATION_LANDSCAPE -> {
            showAsDropDown(
                anchorView,
                xOffset,
                yOffset,
                Gravity.CENTER
            )
        }

        Configuration.ORIENTATION_UNDEFINED -> {
            showAsDropDown(
                anchorView,
                xOffset,
                yOffset,
                Gravity.CENTER
            )
        }

        else -> {

        }
    }
}

fun PopupWindow.setPopUpWindowCenter(
    context: Context,
    keyWindowLayout: KeyWindowLayout,
    anchorView: View,
    sizeScalePercent: Int = 100
) {
    this.width = anchorView.width.scaledPopupSize(sizeScalePercent)
    this.height = anchorView.height.scaledPopupSize(sizeScalePercent)
    this.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
    keyWindowLayout.let { bubble ->
        if (bubble.arrowDirection != ArrowDirection.TOP_RIGHT) this.dismiss()
        bubble.arrowDirection = ArrowDirection.TOP_RIGHT
        bubble.arrowWidth = 0f
        bubble.arrowHeight = 0f
    }
    val orientation = context.resources.configuration.orientation
    val yOffset = calculateLongPressPopupYOffset(
        LongPressPopupPosition.CENTER, anchorView.id, orientation, height, anchorView.height
    )
    when (orientation) {
        Configuration.ORIENTATION_PORTRAIT,
        Configuration.ORIENTATION_LANDSCAPE,
        Configuration.ORIENTATION_UNDEFINED -> showAsDropDown(
            anchorView,
            -((width - anchorView.width) / 2),
            yOffset,
            Gravity.CENTER
        )

        else -> {}
    }
}

fun PopupWindow.setPopUpWindowRight(
    context: Context,
    keyWindowLayout: KeyWindowLayout,
    anchorView: View,
    sizeScalePercent: Int = 100
) {
    this.width = anchorView.width.scaledPopupSize(sizeScalePercent)
    this.height = anchorView.height.scaledPopupSize(sizeScalePercent)
    this.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
    keyWindowLayout.let { bubble ->
        if (bubble.arrowDirection != ArrowDirection.LEFT_CENTER) this.dismiss()
        bubble.arrowDirection = ArrowDirection.LEFT_CENTER
        bubble.arrowWidth = 0f
        bubble.arrowHeight = 0f
    }
    val orientation = context.resources.configuration.orientation
    val yOffset = calculateLongPressPopupYOffset(
        LongPressPopupPosition.RIGHT, anchorView.id, orientation, height, anchorView.height
    )
    when (orientation) {
        Configuration.ORIENTATION_PORTRAIT,
        Configuration.ORIENTATION_LANDSCAPE,
        Configuration.ORIENTATION_UNDEFINED -> showAsDropDown(
            anchorView,
            anchorView.width,
            yOffset,
            Gravity.CENTER
        )

        else -> {}
    }
}

fun PopupWindow.setPopUpWindowLeft(
    context: Context,
    keyWindowLayout: KeyWindowLayout,
    anchorView: View,
    sizeScalePercent: Int = 100
) {
    this.width = anchorView.width.scaledPopupSize(sizeScalePercent)
    this.height = anchorView.height.scaledPopupSize(sizeScalePercent)
    this.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
    keyWindowLayout.let { bubble ->
        if (bubble.arrowDirection != ArrowDirection.RIGHT_CENTER) this.dismiss()
        bubble.arrowDirection = ArrowDirection.RIGHT_CENTER
        bubble.arrowWidth = 0f
        bubble.arrowHeight = 0f
    }
    val orientation = context.resources.configuration.orientation
    val yOffset = calculateLongPressPopupYOffset(
        LongPressPopupPosition.LEFT, anchorView.id, orientation, height, anchorView.height
    )
    when (orientation) {
        Configuration.ORIENTATION_PORTRAIT,
        Configuration.ORIENTATION_LANDSCAPE,
        Configuration.ORIENTATION_UNDEFINED -> showAsDropDown(
            anchorView,
            -width,
            yOffset,
            Gravity.CENTER
        )

        else -> {}
    }
}


fun PopupWindow.setPopUpWindowBottom(
    context: Context,
    keyWindowLayout: KeyWindowLayout,
    anchorView: View,
    sizeScalePercent: Int = 100
) {
    this.width = anchorView.width.scaledPopupSize(sizeScalePercent)
    this.height = anchorView.height.scaledPopupSize(sizeScalePercent)
    this.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
    keyWindowLayout.let { bubble ->
        if (bubble.arrowDirection != ArrowDirection.TOP_CENTER) this.dismiss()
        bubble.arrowDirection = ArrowDirection.TOP_CENTER
        bubble.arrowWidth = 0f
        bubble.arrowHeight = 0f
    }
    val orientation = context.resources.configuration.orientation
    val yOffset = calculateLongPressPopupYOffset(
        LongPressPopupPosition.BOTTOM, anchorView.id, orientation, height, anchorView.height
    )
    when (orientation) {
        Configuration.ORIENTATION_PORTRAIT,
        Configuration.ORIENTATION_LANDSCAPE,
        Configuration.ORIENTATION_UNDEFINED -> showAsDropDown(
            anchorView,
            -((width - anchorView.width) / 2),
            yOffset,
            Gravity.CENTER
        )

        else -> {}
    }
}

fun PopupWindow.setPopUpWindowTop(
    context: Context,
    keyWindowLayout: KeyWindowLayout,
    anchorView: View,
    sizeScalePercent: Int = 100
) {
    this.width = anchorView.width.scaledPopupSize(sizeScalePercent)
    this.height = anchorView.height.scaledPopupSize(sizeScalePercent)
    this.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
    keyWindowLayout.let { bubble ->
        if (bubble.arrowDirection != ArrowDirection.BOTTOM_CENTER) this.dismiss()
        bubble.arrowDirection = ArrowDirection.BOTTOM_CENTER
        bubble.arrowWidth = 0f
        bubble.arrowHeight = 0f
    }
    val orientation = context.resources.configuration.orientation
    val yOffset = calculateLongPressPopupYOffset(
        LongPressPopupPosition.TOP, anchorView.id, orientation, height, anchorView.height
    )
    when (orientation) {
        Configuration.ORIENTATION_PORTRAIT,
        Configuration.ORIENTATION_LANDSCAPE,
        Configuration.ORIENTATION_UNDEFINED -> showAsDropDown(
            anchorView,
            -((width - anchorView.width) / 2),
            yOffset,
            Gravity.CENTER
        )

        else -> {}
    }
}
