package com.kazumaproject.markdownhelperkeyboard.ime_service.extensions

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.widget.PopupWindow
import com.daasuu.bl.ArrowDirection
import com.daasuu.bl.BubbleLayout
import com.kazumaproject.markdownhelperkeyboard.R

fun PopupWindow.setPopUpWindowFlickRight(
    context: Context,
    bubbleLayout: BubbleLayout,
    anchorView: View
){
    this.width = anchorView.width
    this.height = anchorView.height
    this.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    bubbleLayout.let { bubble ->
        if (bubble.arrowDirection != ArrowDirection.LEFT_CENTER) this.dismiss()
        bubble.arrowDirection = ArrowDirection.LEFT_CENTER
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

fun PopupWindow.setPopUpWindowFlickLeft(
    context: Context,
    bubbleLayout: BubbleLayout,
    anchorView: View
){
    this.width = anchorView.width
    this.height = anchorView.height
    this.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    bubbleLayout.let { bubble ->
        if (bubble.arrowDirection != ArrowDirection.RIGHT_CENTER) this.dismiss()
        bubble.arrowDirection = ArrowDirection.RIGHT_CENTER
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
    bubbleLayout: BubbleLayout,
    anchorView: View
){
    this.width = anchorView.width
    this.height = anchorView.height
    this.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    bubbleLayout.let { bubble ->
        if (bubble.arrowDirection != ArrowDirection.TOP_CENTER) this.dismiss()
        bubble.arrowDirection = ArrowDirection.TOP_CENTER
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

fun PopupWindow.setPopUpWindowFlickTop(
    context: Context,
    bubbleLayout: BubbleLayout,
    anchorView: View
){
    this.width = anchorView.width
    this.height = anchorView.height
    this.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    bubbleLayout.let { bubble ->
        if (bubble.arrowDirection != ArrowDirection.BOTTOM_CENTER) this.dismiss()
        bubble.arrowDirection = ArrowDirection.BOTTOM_CENTER
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
