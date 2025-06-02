package com.kazumaproject.qwerty_keyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton

class QWERTYButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.buttonStyle
) : AppCompatButton(context, attrs, defStyleAttr) {
    private val gestureDetector = GestureDetector(context, GestureListener())

    init {
        isAllCaps = false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            Toast.makeText(context, "Single Tap", Toast.LENGTH_SHORT).show()
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
