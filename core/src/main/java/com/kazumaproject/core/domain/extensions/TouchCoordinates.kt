package com.kazumaproject.core.domain.extensions

import android.os.Build
import android.view.MotionEvent
import android.view.View

/** Screen coordinates remain consistent when a floating keyboard's ancestors scale its layout. */
fun View.touchScreenCoordinates(event: MotionEvent, pointerIndex: Int): Pair<Float, Float> {
    if (Build.VERSION.SDK_INT >= 29) return event.getRawX(pointerIndex) to event.getRawY(pointerIndex)
    // Old Android exposes raw coordinates for pointer zero only. Transform the other
    // pointer's displacement, then add it to that known screen position.
    val delta = floatArrayOf(event.getX(pointerIndex) - event.x, event.getY(pointerIndex) - event.y)
    var ancestor: View? = this
    while (ancestor != null) {
        ancestor.matrix.mapVectors(delta)
        ancestor = ancestor.parent as? View
    }
    return (event.rawX + delta[0]) to (event.rawY + delta[1])
}
