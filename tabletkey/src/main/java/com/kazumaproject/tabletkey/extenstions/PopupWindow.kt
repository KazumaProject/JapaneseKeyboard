package com.kazumaproject.tabletkey.extenstions

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.widget.PopupWindow
import com.kazumaproject.core.ui.key_window.ArrowDirection
import com.kazumaproject.core.ui.key_window.KeyWindowLayout
import com.kazumaproject.tabletkey.R

fun PopupWindow.setPopUpWindowFlickRight(
    context: Context,
    keyWindowLayout: KeyWindowLayout,
    anchorView: View
) {
    this.width = anchorView.width + (anchorView.width) / 2
    this.height = anchorView.height
    this.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    keyWindowLayout.apply {
        if (arrowDirection != ArrowDirection.LEFT_CENTER) this@setPopUpWindowFlickRight.dismiss()
        arrowDirection = ArrowDirection.LEFT_CENTER
        arrowHeight = anchorView.height - 12f
        arrowWidth = (anchorView.width / 2).toFloat()
    }
    showPopupWithOrientation(
        context,
        anchorView,
        anchorView.width - (anchorView.width) / 2,
        -anchorView.height
    )
}

fun PopupWindow.setPopUpWindowFlickLeft(
    context: Context,
    keyWindowLayout: KeyWindowLayout,
    anchorView: View
) {
    this.width = anchorView.width + (anchorView.width) / 2
    this.height = anchorView.height
    this.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    keyWindowLayout.apply {
        if (arrowDirection != ArrowDirection.RIGHT_CENTER) this@setPopUpWindowFlickLeft.dismiss()
        arrowDirection = ArrowDirection.RIGHT_CENTER
        arrowHeight = anchorView.height - 12f
        arrowWidth = (anchorView.width / 2).toFloat()
    }
    showPopupWithOrientation(context, anchorView, -anchorView.width, -anchorView.height)
}

fun PopupWindow.setPopUpWindowFlickTop(
    context: Context,
    keyWindowLayout: KeyWindowLayout,
    anchorView: View
) {
    this.width = anchorView.width
    this.height = anchorView.height + (anchorView.height / 2)
    this.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    keyWindowLayout.apply {
        if (arrowDirection != ArrowDirection.BOTTOM_CENTER) this@setPopUpWindowFlickTop.dismiss()
        arrowDirection = ArrowDirection.BOTTOM_CENTER
        arrowHeight = (anchorView.height / 2).toFloat()
        arrowWidth = anchorView.width - 12f
    }
    showPopupWithOrientation(context, anchorView, 0, -(anchorView.height * 2))
}

fun PopupWindow.setPopUpWindowFlickBottom(
    context: Context,
    keyWindowLayout: KeyWindowLayout,
    anchorView: View
) {
    this.width = anchorView.width
    this.height = anchorView.height + (anchorView.height / 2)
    this.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    keyWindowLayout.apply {
        if (arrowDirection != ArrowDirection.TOP_CENTER) this@setPopUpWindowFlickBottom.dismiss()
        arrowDirection = ArrowDirection.TOP_CENTER
        arrowHeight = (anchorView.height / 2).toFloat()
        arrowWidth = anchorView.width - 12f
    }
    if (anchorView.id !in listOf(R.id.key_10, R.id.key_11, R.id.key_12)) {
        showPopupWithOrientation(context, anchorView, 0, -anchorView.height / 2)
    }
}

fun PopupWindow.setPopUpWindowFlickTap(
    context: Context,
    keyWindowLayout: KeyWindowLayout,
    anchorView: View
) {
    this.width = anchorView.width
    this.height = anchorView.height
    this.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    keyWindowLayout.apply {
        if (arrowDirection != ArrowDirection.TOP_RIGHT) this@setPopUpWindowFlickTap.dismiss()
        arrowDirection = ArrowDirection.TOP_RIGHT
        arrowWidth = 0f
        arrowHeight = 0f
    }
    showPopupWithOrientation(context, anchorView, 0, -anchorView.height)
}

fun PopupWindow.setPopUpWindowCenter(
    context: Context,
    keyWindowLayout: KeyWindowLayout,
    anchorView: View
) {
    this.width = anchorView.width
    this.height = anchorView.height
    this.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    keyWindowLayout.let { bubble ->
        if (bubble.arrowDirection != ArrowDirection.TOP_RIGHT) this.dismiss()
        bubble.arrowDirection = ArrowDirection.TOP_RIGHT
        bubble.arrowWidth = 0f
        bubble.arrowHeight = 0f
    }
    when (context.resources.configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT -> {
            showAsDropDown(
                anchorView,
                0,
                -(anchorView.height),
                Gravity.CENTER
            )
        }

        Configuration.ORIENTATION_LANDSCAPE -> {
            showAsDropDown(
                anchorView,
                0,
                -(anchorView.height),
                Gravity.CENTER
            )
        }

        Configuration.ORIENTATION_UNDEFINED -> {
            showAsDropDown(
                anchorView,
                0,
                -(anchorView.height),
                Gravity.CENTER
            )
        }

        else -> {}
    }
}

fun PopupWindow.setPopUpWindowRight(
    context: Context,
    keyWindowLayout: KeyWindowLayout,
    anchorView: View
) {
    this.width = anchorView.width
    this.height = anchorView.height
    this.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    keyWindowLayout.let { bubble ->
        if (bubble.arrowDirection != ArrowDirection.LEFT_CENTER) this.dismiss()
        bubble.arrowDirection = ArrowDirection.LEFT_CENTER
        bubble.arrowWidth = 0f
        bubble.arrowHeight = 0f
    }
    when (context.resources.configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT -> {
            showAsDropDown(
                anchorView,
                anchorView.width,
                -(anchorView.height),
                Gravity.CENTER
            )
        }

        Configuration.ORIENTATION_LANDSCAPE -> {
            showAsDropDown(
                anchorView,
                anchorView.width,
                -(anchorView.height),
                Gravity.CENTER
            )
        }

        Configuration.ORIENTATION_UNDEFINED -> {
            showAsDropDown(
                anchorView,
                anchorView.width,
                -(anchorView.height),
                Gravity.CENTER
            )
        }

        else -> {}
    }
}

fun PopupWindow.setPopUpWindowLeft(
    context: Context,
    keyWindowLayout: KeyWindowLayout,
    anchorView: View
) {
    this.width = anchorView.width
    this.height = anchorView.height
    this.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    keyWindowLayout.let { bubble ->
        if (bubble.arrowDirection != ArrowDirection.RIGHT_CENTER) this.dismiss()
        bubble.arrowDirection = ArrowDirection.RIGHT_CENTER
        bubble.arrowWidth = 0f
        bubble.arrowHeight = 0f
    }
    when (context.resources.configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT -> {
            showAsDropDown(
                anchorView,
                -(anchorView.width),
                -(anchorView.height),
                Gravity.CENTER
            )
        }

        Configuration.ORIENTATION_LANDSCAPE -> {
            showAsDropDown(
                anchorView,
                -(anchorView.width),
                -(anchorView.height),
                Gravity.CENTER
            )
        }

        Configuration.ORIENTATION_UNDEFINED -> {
            showAsDropDown(
                anchorView,
                -(anchorView.width),
                -(anchorView.height),
                Gravity.CENTER
            )
        }

        else -> {

        }
    }
}


fun PopupWindow.setPopUpWindowBottom(
    context: Context,
    keyWindowLayout: KeyWindowLayout,
    anchorView: View
) {
    this.width = anchorView.width
    this.height = anchorView.height
    this.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    keyWindowLayout.let { bubble ->
        if (bubble.arrowDirection != ArrowDirection.TOP_CENTER) this.dismiss()
        bubble.arrowDirection = ArrowDirection.TOP_CENTER
        bubble.arrowWidth = 0f
        bubble.arrowHeight = 0f
    }
    when (context.resources.configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT -> {
            when (anchorView.id) {
                R.id.key_10,
                R.id.key_11,
                R.id.key_12 -> {

                }

                else -> {
                    showAsDropDown(
                        anchorView,
                        0,
                        0,
                        Gravity.CENTER
                    )
                }
            }
        }

        Configuration.ORIENTATION_LANDSCAPE -> {
            showAsDropDown(
                anchorView,
                0,
                0,
                Gravity.CENTER
            )
        }

        Configuration.ORIENTATION_UNDEFINED -> {
            when (anchorView.id) {
                R.id.key_10,
                R.id.key_11,
                R.id.key_12 -> {

                }

                else -> {
                    showAsDropDown(
                        anchorView,
                        0,
                        0,
                        Gravity.CENTER
                    )
                }
            }
        }

        else -> {

        }
    }
}

fun PopupWindow.setPopUpWindowTop(
    context: Context,
    keyWindowLayout: KeyWindowLayout,
    anchorView: View
) {
    this.width = anchorView.width
    this.height = anchorView.height
    this.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    keyWindowLayout.let { bubble ->
        if (bubble.arrowDirection != ArrowDirection.BOTTOM_CENTER) this.dismiss()
        bubble.arrowDirection = ArrowDirection.BOTTOM_CENTER
        bubble.arrowWidth = 0f
        bubble.arrowHeight = 0f
    }
    when (context.resources.configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT -> {
            showAsDropDown(
                anchorView,
                0,
                -(anchorView.height * 2),
                Gravity.CENTER
            )
        }

        Configuration.ORIENTATION_LANDSCAPE -> {
            showAsDropDown(
                anchorView,
                0,
                -(anchorView.height * 2),
                Gravity.CENTER
            )
        }

        Configuration.ORIENTATION_UNDEFINED -> {
            showAsDropDown(
                anchorView,
                0,
                -(anchorView.height * 2),
                Gravity.CENTER
            )
        }

        else -> {

        }
    }
}

/**
 * Helper function to handle showing with orientation.
 */
private fun PopupWindow.showPopupWithOrientation(
    context: Context,
    anchorView: View,
    offsetX: Int,
    offsetY: Int
) {
    when (context.resources.configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT,
        Configuration.ORIENTATION_LANDSCAPE,
        Configuration.ORIENTATION_UNDEFINED -> {
            showAsDropDown(anchorView, offsetX, offsetY, Gravity.CENTER)
        }

        else -> {}
    }
}