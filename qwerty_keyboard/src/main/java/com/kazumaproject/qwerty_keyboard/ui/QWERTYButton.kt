package com.kazumaproject.qwerty_keyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat

class QWERTYButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.buttonStyle
) : AppCompatButton(context, attrs, defStyleAttr) {

    private val gestureDetector = GestureDetector(context, GestureListener())

    /**
     * ✅ STEP 1: 右上の文字を保持するプロパティを追加
     * このプロパティに文字を設定すると、自動的にビューが再描画されます。
     */
    var topRightChar: Char? = null
        set(value) {
            field = value
            invalidate() // Viewの再描画をリクエストする
        }

    /**
     * ✅ STEP 2: 文字描画用のPaintオブジェクトを準備
     */
    private val topRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color =
            ContextCompat.getColor(context, com.kazumaproject.core.R.color.keyboard_icon_color)
        textAlign = Paint.Align.RIGHT
        setPadding(0, 1, 6, 0)
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            9f,
            context.resources.displayMetrics
        )
    }

    init {
        isAllCaps = false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // gestureDetectorの処理を優先させたい場合は、super.onTouchEventより前に置く
        gestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    /**
     * ✅ STEP 3: onDrawメソッドをオーバーライドして文字を描画
     */
    override fun onDraw(canvas: Canvas) {
        // 最初にボタン本来の描画処理を呼び出す
        super.onDraw(canvas)

        // topRightCharがnullでなければ文字を描画する
        topRightChar?.toString()?.let { charText ->
            // 描画位置を計算 (ボタンの右上、少し内側)
            val x = (width - paddingRight).toFloat()
            val y = paddingTop.toFloat() + topRightPaint.textSize

            // Canvasに文字を描画
            canvas.drawText(charText, x, y, topRightPaint)
        }
    }


    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            Toast.makeText(context, "Single Tap", Toast.LENGTH_SHORT).show()
            // super.onSingleTapConfirmed(e) を呼ぶか、独自の処理を行う
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            Toast.makeText(context, "Double Tap", Toast.LENGTH_SHORT).show()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            Toast.makeText(context, "Long Press", Toast.LENGTH_SHORT).show()
        }
    }
}
