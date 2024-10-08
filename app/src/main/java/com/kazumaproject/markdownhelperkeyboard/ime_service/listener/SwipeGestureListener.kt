package com.kazumaproject.markdownhelperkeyboard.ime_service.listener

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView

class SwipeGestureListener(
    context: Context,
    private val onSwipeDown: () -> Unit,
    private val onSwipeUp: () -> Unit
) : RecyclerView.OnItemTouchListener {

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 != null) {
                val deltaY = e2.y - e1.y
                val absVelocityY = Math.abs(velocityY)

                // Detect swipe down
                if (deltaY > 100 && absVelocityY > Math.abs(velocityX)) {
                    onSwipeDown()
                    return true
                }
                // Detect swipe up
                if (deltaY < -100 && absVelocityY > Math.abs(velocityX)) {
                    onSwipeUp()
                    return true
                }
            }
            return false
        }
    })

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(e)
        return false
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        // no-op
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // no-op
    }
}