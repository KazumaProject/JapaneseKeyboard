package com.kazumaproject.core.ui.skin

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.doOnPreDraw
import com.kazumaproject.core.domain.skin.KeyboardSkinId

/** Dims labels without fading key surfaces or changing input timing. */
class SkinLongPressPresentation {
    private val colors = mutableMapOf<TextView, ColorStateList>()
    private var frameCallback: Choreographer.FrameCallback? = null
    private var activeSkin: KeyboardSkin? = null
    private var pendingDimStart: OneShotPreDrawListener? = null

    fun show(root: View, id: KeyboardSkinId, guide: View? = null) {
        clear()
        val skin = KeyboardSkinRegistry.find(id) ?: return
        val color = skin.longPressLabelColor ?: return
        activeSkin = skin
        fun visit(view: View) {
            if (view is TextView) colors[view] = view.textColors
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) visit(view.getChildAt(index))
            }
        }
        visit(root)
        if (guide == null) startDimming(skin, color)
        else pendingDimStart = guide.doOnPreDraw {
            pendingDimStart = null
            startDimming(skin, color)
        }
    }

    private fun startDimming(skin: KeyboardSkin, color: Int) {
        animate(skin.longPressLabelFadeMillis, skin.longPressLabelInterpolator, { fraction ->
            colors.forEach { (view, saved) ->
                val original = saved.getColorForState(view.drawableState, saved.defaultColor)
                view.setTextColor(ColorUtils.blendARGB(original, color, fraction))
            }
        })
    }

    fun clear(animated: Boolean = false) {
        val wasAwaitingGuide = pendingDimStart != null
        pendingDimStart?.removeListener()
        pendingDimStart = null
        cancelAnimation()
        val skin = activeSkin
        if (animated && !wasAwaitingGuide && skin != null && colors.isNotEmpty()) {
            val from = colors.keys.associateWith { it.currentTextColor }
            animate(skin.longPressLabelRestoreMillis, skin.longPressLabelRestoreInterpolator, { fraction ->
                colors.forEach { (view, saved) ->
                    val target = saved.getColorForState(view.drawableState, saved.defaultColor)
                    view.setTextColor(ColorUtils.blendARGB(from.getValue(view), target, fraction))
                }
            }, ::restoreColors)
        } else restoreColors()
    }

    private fun animate(
        durationMillis: Long,
        interpolator: TimeInterpolator,
        update: (Float) -> Unit,
        end: () -> Unit = {},
    ) {
        cancelAnimation()
        val context = colors.keys.firstOrNull()?.context ?: return
        val scale = if (Build.VERSION.SDK_INT >= 33) ValueAnimator.getDurationScale()
        else Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val duration = durationMillis * scale.coerceAtLeast(0f)
        if (duration <= 0f) {
            update(1f)
            end()
            return
        }
        // Use the requested presentation time, not ValueAnimator's compensated first
        // frame. The guide's pre-draw and the actual UP each establish their own origin.
        // Choreographer's frame timestamp and uptimeMillis share the monotonic time base.
        val startedAt = SystemClock.uptimeMillis()
        val choreographer = Choreographer.getInstance()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (frameCallback !== this) return
                val fraction = ((frameTimeNanos / 1_000_000.0 - startedAt) / duration)
                    .toFloat().coerceIn(0f, 1f)
                update(interpolator.getInterpolation(fraction))
                if (fraction >= 1f) {
                    frameCallback = null
                    end()
                } else choreographer.postFrameCallback(this)
            }
        }
        frameCallback = callback
        update(0f)
        choreographer.postFrameCallback(callback)
    }

    private fun cancelAnimation() {
        frameCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        frameCallback = null
    }

    private fun restoreColors() {
        colors.forEach { (view, color) -> view.setTextColor(color) }
        colors.clear()
        activeSkin = null
    }
}
