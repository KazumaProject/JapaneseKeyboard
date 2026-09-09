package com.kazumaproject.core.ui.skin

import kotlin.math.roundToInt

/** Expanded preview measured at a visible 111 x 135 px key, reference scale 3. */
object CupertinoKeyPreviewGeometry {
    data class Layout(val width:Int,val height:Int,val xOffset:Int,val yOffset:Int)
    fun resolve(keyWidth:Int,keyHeight:Int,keyLeft:Int,screenWidth:Int):Layout {
        val initialWidth=(keyWidth*178f/111f).roundToInt().coerceAtLeast(1)
        val rightEdge=screenWidth-keyLeft-keyWidth<(initialWidth-keyWidth)/2
        val leftEdge=keyLeft<(initialWidth-keyWidth)/2
        val width=when {
            rightEdge -> (keyWidth*172f/112f).roundToInt().coerceAtLeast(1)
            leftEdge -> initialWidth
            else -> (keyWidth*186f/112f).roundToInt().coerceAtLeast(1)
        }
        val height=(keyHeight*346f/135f).roundToInt().coerceAtLeast(1)
        val expansion=(width-keyWidth)/2
        val x=when {
            keyLeft<expansion -> 0
            screenWidth-keyLeft-keyWidth<expansion -> keyWidth-width
            else -> -expansion
        }
        return Layout(width,height,x,-height+(keyHeight*4f/135f).roundToInt())
    }
}
