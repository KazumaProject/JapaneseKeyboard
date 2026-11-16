package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import android.content.Context
import com.kazumaproject.custom_keyboard.data.PopupBounds
import com.kazumaproject.custom_keyboard.data.PopupMap
import com.kazumaproject.custom_keyboard.view.PopupMapView

/** A destination card uses the saved map's contours, rather than a schematic icon. */
class PopupMapThumbnail(context: Context, value: PopupMap, private var sourceWidth: Float, private var sourceHeight: Float) : PopupMapView(context) {
    init { map=value;tapActive=false;applyFreeFlickTheme();importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    fun setSourceKeySize(width: Float, height: Float) {
        if(width<=0f || height<=0f) return
        sourceWidth=width;sourceHeight=height
        updateGeometry(this.width,this.height);invalidate()
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        updateGeometry(w,h)
    }
    private fun updateGeometry(w: Int,h: Int) {
        if(w<=0 || h<=0) return
        val bounds=listOf(PopupBounds.KEY)+map!!.regions.map { it.bounds }
        val left=bounds.minOf { it.left };val right=bounds.maxOf { it.right }
        val top=bounds.minOf { it.top };val bottom=bounds.maxOf { it.bottom }
        val scale=minOf((w-4*resources.displayMetrics.density).coerceAtLeast(1f)/((right-left)*sourceWidth),
            (h-4*resources.displayMetrics.density).coerceAtLeast(1f)/((bottom-top)*sourceHeight))
        keyWidth=sourceWidth*scale;keyHeight=sourceHeight*scale
        originX=w/2f-(left+right)*keyWidth/2;originY=h/2f-(top+bottom)*keyHeight/2
    }
}
