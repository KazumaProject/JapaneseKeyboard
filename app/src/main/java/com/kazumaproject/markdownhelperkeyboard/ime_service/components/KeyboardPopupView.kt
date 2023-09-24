package com.kazumaproject.markdownhelperkeyboard.ime_service.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.kazumaproject.markdownhelperkeyboard.R

class KeyboardPopupView(
    context : Context,
    attributeSet: AttributeSet,
) : View(context, attributeSet) {
    @SuppressLint("DrawAllocation")

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        val backgroundPaint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.popup_bg)
        }
        val textPaint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.main_text_color)
            textSize = 64f
            textAlign = Paint.Align.CENTER
        }

        canvas?.apply {
            drawCircle(width/2f,height/2f,width/2f,backgroundPaint)
            drawText("あ", (width / 2f ), ((height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2)), textPaint )
        }
    }
}