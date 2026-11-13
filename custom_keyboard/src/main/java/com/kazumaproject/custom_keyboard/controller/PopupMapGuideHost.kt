package com.kazumaproject.custom_keyboard.controller

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.PopupWindow
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.kazumaproject.custom_keyboard.view.PopupMapGuideView
import com.kazumaproject.custom_keyboard.view.TfbiGestureArrowView
import com.kazumaproject.custom_keyboard.view.compactFlickGuideSize
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Uses a non-touchable window so a readable guide can extend above the IME's surface. */
internal class PopupMapGuideHost(
    context: Context,
    private val hostProvider: () -> View?,
    private val onGeometryChanged: () -> Unit
) {
    val view = PopupMapGuideView(context)
    private val arrow=TfbiGestureArrowView(context).apply {setColor(0xff1976d2.toInt())}
    private val arrowOverlay=KeyboardPopupOverlay(::positionArrow)
    private val arrowLocation=IntArray(2)
    private val arrowKeyLocation=IntArray(2)
    private val arrowHostLocation=IntArray(2)
    private var window: PopupWindow? = null
    private var anchor: View? = null
    private var observed: ViewTreeObserver? = null
    private var initialLocation = intArrayOf(0,0)
    private var initialWidth = 0
    private var initialHeight = 0
    private var initialRotation = 0f
    private var lastPlacement = emptyList<Int>()
    private val density = context.resources.displayMetrics.density
    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        val key=anchor
        if(key!=null) {
            val location=IntArray(2).also(key::getLocationOnScreen)
            if(!location.contentEquals(initialLocation) || key.width!=initialWidth || key.height!=initialHeight || key.rotation!=initialRotation) {
                onGeometryChanged()
            } else position()
        }
        true
    }
    fun show(key: View) {
        if(anchor !== key) {
            dismiss();anchor=key
            initialLocation=IntArray(2).also(key::getLocationOnScreen)
            initialWidth=key.width;initialHeight=key.height;initialRotation=key.rotation
            observed=key.rootView.viewTreeObserver.also {it.addOnPreDrawListener(preDraw)}
        }
        arrow.rotation=key.rotation
        if(!arrowOverlay.isShowing(arrow)) arrowOverlay.show(key,hostProvider(),arrow,key.width,key.height)
        position();view.invalidate()
    }
    private fun position() {
        val key=anchor ?: return
        if(!key.isAttachedToWindow) {dismiss();return}
        val host=hostProvider() ?: key
        if(!host.isAttachedToWindow || host.windowToken==null) return
        val insets=ViewCompat.getRootWindowInsets(key)?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val safeLeft=insets?.left ?: 0;val safeTop=insets?.top ?: 0
        val metrics=key.resources.displayMetrics
        val screenWidth=(metrics.widthPixels-safeLeft-(insets?.right ?: 0)).coerceAtLeast(1)
        val screenHeight=(metrics.heightPixels-safeTop-(insets?.bottom ?: 0)).coerceAtLeast(1)
        val screen=IntArray(2).also(key::getLocationOnScreen)
        adjustPopupAnchorForTransform(key,screen)
        val angle=Math.toRadians(key.rotation.toDouble())
        val halfHeight=abs(sin(angle))*key.width/2+abs(cos(angle))*key.height/2
        val physicalTop=(screen[1]+key.height/2-halfHeight).roundToInt()
        val gap=(4*density).roundToInt()
        val size=compactFlickGuideSize(key.width,key.height,density)
        val width=minOf(size.width,screenWidth)
        val height=minOf(size.height,(physicalTop-safeTop-gap).coerceAtLeast(1),screenHeight)
        val placement=resolveTfbiGuidePopupPlacement(screen[0]-safeLeft,physicalTop-safeTop,key.width,width,height,screenWidth,screenHeight,gap)
        val relative=getLocationRelativeToWindowAnchor(key,host)
        val x=relative[0]+placement.panelLeft+safeLeft-screen[0]
        val y=relative[1]+placement.panelTop+safeTop-screen[1]
        val popup=window ?: PopupWindow(view,width,height,false).apply {
            isTouchable=false;isOutsideTouchable=false;isClippingEnabled=false
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }.also {window=it}
        val next=listOf(x,y,width,height)
        if(popup.isShowing) {
            if(next!=lastPlacement) popup.update(x,y,width,height)
        } else {
            popup.width=width;popup.height=height
            popup.showAtLocation(host,Gravity.TOP or Gravity.LEFT,x,y)
        }
        lastPlacement=next
    }
    fun updatePointer() {arrow.setColor(view.popupHighlightColor);arrow.setDirection(view.pointerDirection());view.invalidate()}
    private fun positionArrow() {
        val key=anchor ?: return
        val host=arrowOverlay.currentHost ?: return
        getLocationRelativeToOverlayHost(key,host,arrowLocation,arrowKeyLocation,arrowHostLocation)
        arrowOverlay.place(arrow,arrowLocation[0],arrowLocation[1],key.width,key.height)
    }
    fun dismiss() {
        arrowOverlay.dismissAll();arrow.setDirection(null)
        observed?.takeIf {it.isAlive}?.removeOnPreDrawListener(preDraw)
        observed=null;window?.dismiss();anchor=null;lastPlacement=emptyList();view.fingerPosition=null
    }
}
