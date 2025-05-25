package com.kazumaproject.core.ui.effect

import android.graphics.RenderEffect
import android.graphics.Shader
import android.view.View

object Blur {
    fun applyBlurEffect(view: View, blurRadius: Float) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val blurEffect = RenderEffect.createBlurEffect(
                blurRadius,
                blurRadius,
                Shader.TileMode.CLAMP
            )
            view.setRenderEffect(blurEffect)
        } else {
            // Handle older versions gracefully or provide an alternative
            // For example, show a message or apply a different effect
        }
    }

    fun removeBlurEffect(view: View) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            view.setRenderEffect(null)
        } else {
            // Handle older versions gracefully or provide an alternative
            // For example, show a message or apply a different effect
        }
    }
}