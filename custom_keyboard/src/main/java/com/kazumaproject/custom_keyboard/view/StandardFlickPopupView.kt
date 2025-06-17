package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.toColorInt
import androidx.core.text.inSpans
import com.kazumaproject.custom_keyboard.data.FlickDirection
import kotlin.math.roundToInt

/**
 * フリック入力時に表示される単一の円形ポップアップビュー。
 * 内部のテキストはコントローラーによって動的に更新される。
 */
class StandardFlickPopupView(context: Context) : AppCompatTextView(context) {

    val viewSize = dpToPx(72) // ポップアップの直径
    private val backgroundDrawable: GradientDrawable = createBackground()

    private class YOffsetSpan(private val yOffset: Int) : ReplacementSpan() {
        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            return paint.measureText(text, start, end).roundToInt()
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
            canvas.drawText(text, start, end, x, (y + yOffset).toFloat(), paint)
        }
    }


    init {
        width = viewSize
        height = viewSize
        gravity = Gravity.CENTER
        setTextColor(Color.BLACK)
        maxLines = 4
        setLineSpacing(0f, 0.8f)
        background = backgroundDrawable
    }

    fun setColors(backgroundColor: Int, textColor: Int, strokeColor: Int) {
        setTextColor(textColor)
        backgroundDrawable.setColor(backgroundColor)
        backgroundDrawable.setStroke(dpToPx(1), strokeColor)
        invalidate()
    }

    fun updateText(text: String?) {
        if (text.isNullOrEmpty()) {
            this.text = ""
            return
        }
        this.text = createSpannableText(text)
    }

    fun updateMultiCharText(characters: Map<FlickDirection, String>) {
        val up = characters[FlickDirection.UP] ?: ""
        val left = characters[FlickDirection.UP_LEFT_FAR] ?: ""
        val tap = characters[FlickDirection.TAP] ?: ""
        val right = characters[FlickDirection.UP_RIGHT_FAR] ?: ""
        val down = characters[FlickDirection.DOWN] ?: ""

        val tapSize = 19f
        val sideSize = 11f
        val verticalOffset = spToPx(-1.5f)
        val transparent = ForegroundColorSpan(Color.TRANSPARENT)

        val builder = SpannableStringBuilder()

        // Line 1: UP
        if (up.isNotEmpty()) {
            builder.inSpans(AbsoluteSizeSpan(spToPx(sideSize))) { append(up) }
        } else {
            builder.inSpans(AbsoluteSizeSpan(spToPx(sideSize)), transparent) { append(" ") }
        }
        builder.append("\n")

        // ▼▼▼ CHANGE IS HERE ▼▼▼
        // Line 2: LEFT - Added an extra space for more margin
        if (left.isNotEmpty()) {
            builder.inSpans(
                AbsoluteSizeSpan(spToPx(sideSize)),
                YOffsetSpan(verticalOffset)
            ) { append("$left  ") } // Two spaces
        } else {
            builder.inSpans(
                AbsoluteSizeSpan(spToPx(sideSize)),
                YOffsetSpan(verticalOffset),
                transparent
            ) { append("   ") } // Three spaces
        }

        // Line 2: TAP
        builder.inSpans(
            AbsoluteSizeSpan(spToPx(tapSize)),
            StyleSpan(Typeface.BOLD)
        ) { append(tap) }

        // ▼▼▼ CHANGE IS HERE ▼▼▼
        // Line 2: RIGHT - Added an extra space for more margin
        if (right.isNotEmpty()) {
            builder.inSpans(
                AbsoluteSizeSpan(spToPx(sideSize)),
                YOffsetSpan(verticalOffset)
            ) { append("  $right") } // Two spaces
        } else {
            builder.inSpans(
                AbsoluteSizeSpan(spToPx(sideSize)),
                YOffsetSpan(verticalOffset),
                transparent
            ) { append("   ") } // Three spaces
        }
        builder.append("\n")

        // Spacer Line
        builder.inSpans(AbsoluteSizeSpan(spToPx(4f)), transparent) { append(" \n") }


        // Line 3: DOWN
        if (down.isNotEmpty()) {
            builder.inSpans(AbsoluteSizeSpan(spToPx(sideSize))) { append(down) }
        } else {
            builder.inSpans(AbsoluteSizeSpan(spToPx(sideSize)), transparent) { append(" ") }
        }

        this.text = builder
    }


    private fun createSpannableText(text: String): SpannableString {
        val spannable = SpannableString(text)
        if (text.contains("\n")) {
            val parts = text.split("\n", limit = 2)
            val primaryText = parts[0]
            spannable.setSpan(
                AbsoluteSizeSpan(spToPx(26f)),
                0,
                primaryText.length,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
            spannable.setSpan(
                AbsoluteSizeSpan(spToPx(14f)),
                primaryText.length,
                text.length,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
        } else {
            spannable.setSpan(
                AbsoluteSizeSpan(spToPx(26f)),
                0,
                text.length,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
        }
        return spannable
    }

    private fun createBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor("#FFFFFF".toColorInt())
            setStroke(dpToPx(1), Color.LTGRAY)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun spToPx(sp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
            .toInt()
    }
}
