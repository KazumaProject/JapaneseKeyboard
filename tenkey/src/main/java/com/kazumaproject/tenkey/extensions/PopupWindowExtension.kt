package com.kazumaproject.tenkey.extensions

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.widget.PopupWindow
import com.kazumaproject.core.ui.key_window.ArrowDirection
import com.kazumaproject.core.ui.key_window.KeyWindowLayout
import com.kazumaproject.tenkey.R

fun PopupWindow.setPopUpWindowFlickRight(
    context: Context,
    keyWindowLayout: KeyWindowLayout,
    anchorView: View
){
    this.width = anchorView.width + (anchorView.width) / 2
    this.height = anchorView.height
    this.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    keyWindowLayout.let { bubble ->
        if (bubble.arrowDirection != ArrowDirection.LEFT_CENTER) this.dismiss()
        bubble.arrowDirection = ArrowDirection.LEFT_CENTER
        bubble.arrowHeight = anchorView.height - 12f
        bubble.arrowWidth = (anchorView.width / 2).toFloat()
    }
    when(context.resources.configuration.orientation){
        Configuration.ORIENTATION_PORTRAIT ->{
            showAsDropDown(
                anchorView,
                anchorView.width - (anchorView.width) / 2,
                -(anchorView.height),
                Gravity.CENTER
            )
        }
        Configuration.ORIENTATION_LANDSCAPE ->{
            showAsDropDown(
                anchorView,
                anchorView.width - (anchorView.width) / 2,
                -(anchorView.height),
                Gravity.CENTER
            )
        }
        Configuration.ORIENTATION_UNDEFINED -> {
            showAsDropDown(
                anchorView,
                anchorView.width - (anchorView.width) / 2,
                -(anchorView.height),
                Gravity.CENTER
            )
        }
        else ->{}
    }
}

fun PopupWindow.setPopUpWindowFlickLeft(
    context: Context,
    keyWindowLayout: KeyWindowLayout,
    anchorView: View
){
    this.width = anchorView.width + (anchorView.width) / 2
    this.height = anchorView.height
    this.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    keyWindowLayout.let { bubble ->
        if (bubble.arrowDirection != ArrowDirection.RIGHT_CENTER) this.dismiss()
        bubble.arrowDirection = ArrowDirection.RIGHT_CENTER
        bubble.arrowHeight = anchorView.height - 12f
        bubble.arrowWidth = (anchorView.width / 2).toFloat()
    }
    when(context.resources.configuration.orientation){
        Configuration.ORIENTATION_PORTRAIT ->{
            showAsDropDown(
                anchorView,
                -(anchorView.width),
                -(anchorView.height),
                Gravity.CENTER
            )
        }
        Configuration.ORIENTATION_LANDSCAPE ->{
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
        else ->{

        }
    }
}


fun PopupWindow.setPopUpWindowFlickBottom(
    context: Context,
    keyWindowLayout: KeyWindowLayout,
    anchorView: View
){
    this.width = anchorView.width
    this.height = anchorView.height + (anchorView.height / 2)
    this.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    keyWindowLayout.let { bubble ->
        if (bubble.arrowDirection != ArrowDirection.TOP_CENTER) this.dismiss()
        bubble.arrowDirection = ArrowDirection.TOP_CENTER
        bubble.arrowHeight = (anchorView.height / 2).toFloat()
        bubble.arrowWidth = anchorView.width - 12f
    }
    when(context.resources.configuration.orientation){
        Configuration.ORIENTATION_PORTRAIT ->{
            when(anchorView.id){
                R.id.key_small_letter,
                R.id.key_11,
                R.id.key_12 ->{

                }
                else ->{
                    showAsDropDown(
                        anchorView,
                        0,
                        - (anchorView.height / 2),
                        Gravity.CENTER
                    )
                }
            }
        }
        Configuration.ORIENTATION_LANDSCAPE ->{
            showAsDropDown(
                anchorView,
                0,
                - (anchorView.height / 2),
                Gravity.CENTER
            )
        }
        Configuration.ORIENTATION_UNDEFINED -> {
            when(anchorView.id){
                R.id.key_small_letter,
                R.id.key_11,
                R.id.key_12 ->{

                }
                else ->{
                    showAsDropDown(
                        anchorView,
                        0,
                        - (anchorView.height / 2),
                        Gravity.CENTER
                    )
                }
            }
        }
        else ->{

        }
    }
}

fun PopupWindow.setPopUpWindowFlickTop(
    context: Context,
    keyWindowLayout: KeyWindowLayout,
    anchorView: View
){
    this.width = anchorView.width
    this.height = anchorView.height + (anchorView.height / 2)
    this.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    keyWindowLayout.let { bubble ->
        if (bubble.arrowDirection != ArrowDirection.BOTTOM_CENTER) this.dismiss()
        bubble.arrowDirection = ArrowDirection.BOTTOM_CENTER
        bubble.arrowHeight = (anchorView.height / 2).toFloat()
        bubble.arrowWidth = anchorView.width - 12f
    }
    when(context.resources.configuration.orientation){
        Configuration.ORIENTATION_PORTRAIT ->{
            showAsDropDown(
                anchorView,
                0,
                -(anchorView.height * 2),
                Gravity.CENTER
            )
        }
        Configuration.ORIENTATION_LANDSCAPE ->{
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
        else ->{

        }
    }
}

fun PopupWindow.setPopUpWindowCenter(
    context: Context,
    keyWindowLayout: KeyWindowLayout,
    anchorView: View
){
    this.width = anchorView.width
    this.height = anchorView.height
    this.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    keyWindowLayout.let { bubble ->
        if (bubble.arrowDirection != ArrowDirection.TOP_RIGHT) this.dismiss()
        bubble.arrowDirection = ArrowDirection.TOP_RIGHT
        bubble.arrowWidth = 0f
        bubble.arrowHeight = 0f
    }
    when(context.resources.configuration.orientation){
        Configuration.ORIENTATION_PORTRAIT ->{
            showAsDropDown(
                anchorView,
                0,
                -(anchorView.height),
                Gravity.CENTER
            )
        }
        Configuration.ORIENTATION_LANDSCAPE ->{
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
        else ->{}
    }
}

fun PopupWindow.setPopUpWindowRight(
    context: Context,
    keyWindowLayout: KeyWindowLayout,
    anchorView: View
){
    this.width = anchorView.width
    this.height = anchorView.height
    this.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    keyWindowLayout.let { bubble ->
        if (bubble.arrowDirection != ArrowDirection.LEFT_CENTER) this.dismiss()
        bubble.arrowDirection = ArrowDirection.LEFT_CENTER
        bubble.arrowWidth = 0f
        bubble.arrowHeight = 0f
    }
    when(context.resources.configuration.orientation){
        Configuration.ORIENTATION_PORTRAIT ->{
            showAsDropDown(
                anchorView,
                anchorView.width,
                -(anchorView.height),
                Gravity.CENTER
            )
        }
        Configuration.ORIENTATION_LANDSCAPE ->{
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
        else ->{}
    }
}

fun PopupWindow.setPopUpWindowLeft(
    context: Context,
    keyWindowLayout: KeyWindowLayout,
    anchorView: View
){
    this.width = anchorView.width
    this.height = anchorView.height
    this.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    keyWindowLayout.let { bubble ->
        if (bubble.arrowDirection != ArrowDirection.RIGHT_CENTER) this.dismiss()
        bubble.arrowDirection = ArrowDirection.RIGHT_CENTER
        bubble.arrowWidth = 0f
        bubble.arrowHeight = 0f
    }
    when(context.resources.configuration.orientation){
        Configuration.ORIENTATION_PORTRAIT ->{
            showAsDropDown(
                anchorView,
                -(anchorView.width),
                -(anchorView.height),
                Gravity.CENTER
            )
        }
        Configuration.ORIENTATION_LANDSCAPE ->{
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
        else ->{

        }
    }
}


fun PopupWindow.setPopUpWindowBottom(
    context: Context,
    keyWindowLayout: KeyWindowLayout,
    anchorView: View
){
    this.width = anchorView.width
    this.height = anchorView.height
    this.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    keyWindowLayout.let { bubble ->
        if (bubble.arrowDirection != ArrowDirection.TOP_CENTER) this.dismiss()
        bubble.arrowDirection = ArrowDirection.TOP_CENTER
        bubble.arrowWidth = 0f
        bubble.arrowHeight = 0f
    }
    when(context.resources.configuration.orientation){
        Configuration.ORIENTATION_PORTRAIT ->{
            when(anchorView.id){
                R.id.key_small_letter,
                R.id.key_11,
                R.id.key_12 ->{

                }
                else ->{
                    showAsDropDown(
                        anchorView,
                        0,
                        0,
                        Gravity.CENTER
                    )
                }
            }
        }
        Configuration.ORIENTATION_LANDSCAPE ->{
            showAsDropDown(
                anchorView,
                0,
                0,
                Gravity.CENTER
            )
        }
        Configuration.ORIENTATION_UNDEFINED -> {
            when(anchorView.id){
                R.id.key_small_letter,
                R.id.key_11,
                R.id.key_12 ->{

                }
                else ->{
                    showAsDropDown(
                        anchorView,
                        0,
                        0,
                        Gravity.CENTER
                    )
                }
            }
        }
        else ->{

        }
    }
}

fun PopupWindow.setPopUpWindowTop(
    context: Context,
    keyWindowLayout: KeyWindowLayout,
    anchorView: View
){
    this.width = anchorView.width
    this.height = anchorView.height
    this.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    keyWindowLayout.let { bubble ->
        if (bubble.arrowDirection != ArrowDirection.BOTTOM_CENTER) this.dismiss()
        bubble.arrowDirection = ArrowDirection.BOTTOM_CENTER
        bubble.arrowWidth = 0f
        bubble.arrowHeight = 0f
    }
    when(context.resources.configuration.orientation){
        Configuration.ORIENTATION_PORTRAIT ->{
            showAsDropDown(
                anchorView,
                0,
                -(anchorView.height * 2),
                Gravity.CENTER
            )
        }
        Configuration.ORIENTATION_LANDSCAPE ->{
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
        else ->{

        }
    }
}
