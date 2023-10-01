package com.kazumaproject.markdownhelperkeyboard.ime_service.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.convertDp2PxFloat
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.convertSp2Px
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputMode

class InputModeSwitch(context: Context, attrs: AttributeSet): androidx.appcompat.widget.AppCompatImageButton(context, attrs) {

    private val textPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.sub_text_color)
        textSize = 16f.convertSp2Px(context)
        textAlign = Paint.Align.CENTER
    }

    private val textPaintBold = Paint().apply {
        color = ContextCompat.getColor(context, R.color.keyboard_icon_color)
        textSize = 16f.convertSp2Px(context)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private var currentInputMode: InputMode = InputMode.ModeJapanese

    fun setInputMode(inputMode: InputMode){
        currentInputMode = inputMode
    }

    fun getCurrentInputMode(): InputMode {
        return currentInputMode
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val text = "あa1"
        when(currentInputMode){
            is InputMode.ModeJapanese ->{
                canvas?.apply {
                    drawText(
                        text,
                        0,
                        1,
                        (width / 2f) - 12f.convertDp2PxFloat(context),
                        (height / 2f) + 8f.convertDp2PxFloat(context),
                        textPaintBold
                    )
                    drawText(
                        text,
                        1,
                        3,
                        ((width / 2f) + 12f.convertDp2PxFloat(context)),
                        (height / 2f) + 8f.convertDp2PxFloat(context),
                        textPaint)
                }
            }
            is InputMode.ModeEnglish ->{
                canvas?.apply {
                    drawText(
                        text,
                        0,
                        1,
                        ((width / 2f) - 12f.convertDp2PxFloat(context)),
                        (height / 2f) + 8f.convertDp2PxFloat(context),
                        textPaint
                    )
                    drawText(
                        text,
                        1,
                        2,
                        ((width / 2f) + 6f.convertDp2PxFloat(context)),
                        (height / 2f) + 8f.convertDp2PxFloat(context),
                        textPaintBold
                    )
                    drawText(
                        text,
                        2,
                        3,
                        ((width / 2f) + 18f.convertDp2PxFloat(context)),
                        (height / 2f) + 8f.convertDp2PxFloat(context),
                        textPaint
                    )
                }
            }
            is InputMode.ModeNumber ->{
                canvas?.apply {
                    drawText(
                        text,
                        0,
                        1,
                        ((width / 2f) - 12f.convertDp2PxFloat(context)),
                        (height / 2f) + 8f.convertDp2PxFloat(context),
                        textPaint)
                    drawText(
                        text,
                        1,
                        2,
                        ((width / 2f) + 6f.convertDp2PxFloat(context)),
                        (height / 2f) + 8f.convertDp2PxFloat(context),
                        textPaint
                    )
                    drawText(
                        text,
                        2,
                        3,
                        ((width / 2f) + 18f.convertDp2PxFloat(context)),
                        (height / 2f) + 8f.convertDp2PxFloat(context),
                        textPaintBold
                    )
                }
            }
        }
    }

}