package com.kazumaproject.core.ui.skin

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.kazumaproject.core.domain.skin.KeyboardSkinId

/** iOS dims labels during the kana guide, while leaving key surfaces unchanged. */
class SkinLongPressPresentation {
    private val colors = mutableMapOf<TextView, ColorStateList>()
    private var dimAnimator: android.animation.ValueAnimator? = null
    private var activeSkin: KeyboardSkin? = null
    fun show(root: View, id: KeyboardSkinId) {
        clear()
        val skin = KeyboardSkinRegistry.find(id) ?: return
        val color = skin.longPressLabelColor ?: return
        activeSkin = skin
        fun visit(view: View) {
            if (view is TextView) colors[view] = view.textColors
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        visit(root)
        // Measured label fade: a critically damped response, approximately 300 ms.
        // Animate labels only; changing root alpha also fades key surfaces and popups.
        dimAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = skin.longPressLabelFadeMillis
            interpolator = skin.longPressLabelInterpolator
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                colors.forEach { (view, saved) ->
                    val original = saved.getColorForState(view.drawableState, saved.defaultColor)
                    view.setTextColor(androidx.core.graphics.ColorUtils.blendARGB(original, color, fraction))
                }
            }
            start()
        }
    }
    fun clear(animated: Boolean = false) {
        dimAnimator?.cancel()
        dimAnimator = null
        val skin = activeSkin
        if (animated && skin != null && colors.isNotEmpty() && skin.longPressLabelRestoreMillis > 0) {
            val from = colors.keys.associateWith { it.currentTextColor }
            dimAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                duration = skin.longPressLabelRestoreMillis
                interpolator = skin.longPressLabelRestoreInterpolator
                addUpdateListener { animation ->
                    val fraction = animation.animatedValue as Float
                    colors.forEach { (view, saved) ->
                        val target = saved.getColorForState(view.drawableState, saved.defaultColor)
                        view.setTextColor(androidx.core.graphics.ColorUtils.blendARGB(from.getValue(view), target, fraction))
                    }
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) { restoreColors() }
                })
                start()
            }
        } else restoreColors()
    }

    private fun restoreColors() {
        colors.forEach { (view, color) -> view.setTextColor(color) }
        colors.clear()
        activeSkin = null
    }
}
