package com.kazumaproject.custom_keyboard.data

import com.kazumaproject.core.domain.flick.FlickGestureMath
import com.kazumaproject.core.domain.flick.GestureSessionConfig

/** A frozen gesture's display policy; selection remains owned by PopupMapSession. */
class PopupMapVisibility(val settings: GestureSessionConfig, private val downX: Float, private val downY: Float,
    private val presentation: PopupMapPresentation = PopupMapPresentation.AROUND_KEY) {
    private val above get() = presentation==PopupMapPresentation.GUIDE_ABOVE_KEY
    var expanded = false; private set
    var longPressEligible = true; private set
    private var longPressReached = false
    val visible get() = if(above) settings.showFlickPopup || settings.showLongPressPopup
        else if(longPressReached) expanded else settings.showFlickPopup
    fun move(x: Float, y: Float) {
        if(!expanded && FlickGestureMath.isThresholdCrossed(x-downX,y-downY,
                settings.flickThresholdPx*.5f,settings.flickThresholdShape)) longPressEligible=false
    }
    fun longPress(isTap: Boolean): Boolean {
        if(above) return false
        if(!longPressEligible || !isTap) return false
        longPressReached=true
        expanded=settings.showLongPressPopup
        return true
    }
    fun regions(session: PopupMapSession): Set<String>? = if(above || expanded) null else setOfNotNull(session.selectedRegionId)
    fun showsTap(session: PopupMapSession) = above || expanded || session.isTap
}
