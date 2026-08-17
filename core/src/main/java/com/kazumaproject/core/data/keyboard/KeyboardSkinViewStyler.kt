package com.kazumaproject.core.data.keyboard

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.widget.ImageViewCompat

/** Applies a renderer without replacing the keyboard's existing touch listeners. */
object KeyboardSkinViewStyler {
    private val pressedEnabledState = intArrayOf(
        android.R.attr.state_pressed,
        android.R.attr.state_enabled,
    )
    private val defaultState = intArrayOf()

    fun applyKey(
        view: View,
        skinId: KeyboardSkinId,
        role: KeyboardElementRole,
        motionMode: KeyboardSkinMotionMode = KeyboardSkinMotionMode.FULL,
        stableKey: Int = view.id,
    ) {
        if (skinId == KeyboardSkinId.DEFAULT) {
            clearTransientStyle(view)
            return
        }
        val spec = KeyboardSkinCatalog.specFor(skinId)
        val renderer = KeyboardSkinRendererRegistry.rendererFor(skinId)
        view.backgroundTintList = null
        view.background = renderer.createKeyDrawable(view.context, role, stableKey)
        view.alpha = 1f
        view.translationX = 0f
        view.translationY = 0f
        view.scaleX = 1f
        view.scaleY = 1f
        view.elevation = 0f
        view.stateListAnimator = createStateAnimator(view, spec, motionMode)

        val normalText = spec.palette.textColor(role)
        val pressedText = pressedTextColor(spec, role)
        val textColors = ColorStateList(
            arrayOf(pressedEnabledState, defaultState),
            intArrayOf(pressedText, normalText),
        )
        when (view) {
            is TextView -> {
                view.setTextColor(textColors)
                view.typeface = Typeface.create(
                    spec.typography.familyName,
                    if (spec.typography.bold) Typeface.BOLD else Typeface.NORMAL,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    view.letterSpacing = spec.typography.letterSpacing
                }
                view.includeFontPadding = false
            }

            is ImageView -> ImageViewCompat.setImageTintList(view, textColors)
        }
    }

    fun applySurface(
        view: View,
        skinId: KeyboardSkinId,
        role: KeyboardSurfaceRole,
    ) {
        if (skinId == KeyboardSkinId.DEFAULT) return
        view.backgroundTintList = null
        view.background = KeyboardSkinRendererRegistry.rendererFor(skinId)
            .createSurfaceDrawable(view.context, role)
    }

    fun clearTransientStyle(view: View) {
        view.stateListAnimator = null
        view.animate().cancel()
        view.translationX = 0f
        view.translationY = 0f
        view.scaleX = 1f
        view.scaleY = 1f
        view.alpha = 1f
        if (view is TextView) {
            view.typeface = Typeface.DEFAULT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) view.letterSpacing = 0f
        }
    }

    private fun createStateAnimator(
        view: View,
        spec: KeyboardSkinSpec,
        mode: KeyboardSkinMotionMode,
    ): StateListAnimator? {
        if (mode == KeyboardSkinMotionMode.OFF) return null
        if (mode == KeyboardSkinMotionMode.REDUCED) {
            return StateListAnimator().apply {
                addState(
                    pressedEnabledState,
                    propertyAnimator(view, View.ALPHA, REDUCED_PRESSED_ALPHA, REDUCED_PRESS_MS),
                )
                addState(
                    defaultState,
                    propertyAnimator(view, View.ALPHA, 1f, REDUCED_RELEASE_MS),
                )
            }
        }
        val density = view.resources.displayMetrics.density
        val motion = spec.motion
        val pressed = AnimatorSet().apply {
            playTogether(
                propertyAnimator(view, View.SCALE_X, motion.pressScale, motion.pressDurationMs),
                propertyAnimator(view, View.SCALE_Y, motion.pressScale, motion.pressDurationMs),
                propertyAnimator(
                    view,
                    View.TRANSLATION_Y,
                    motion.pressTranslationYDp * density,
                    motion.pressDurationMs,
                ),
                propertyAnimator(
                    view,
                    View.TRANSLATION_X,
                    motion.pressTranslationXDp * density,
                    motion.pressDurationMs,
                ),
            )
            interpolator = AccelerateDecelerateInterpolator()
        }
        val released = AnimatorSet().apply {
            playTogether(
                propertyAnimator(view, View.SCALE_X, 1f, motion.releaseDurationMs),
                propertyAnimator(view, View.SCALE_Y, 1f, motion.releaseDurationMs),
                propertyAnimator(view, View.TRANSLATION_Y, 0f, motion.releaseDurationMs),
                propertyAnimator(view, View.TRANSLATION_X, 0f, motion.releaseDurationMs),
            )
            interpolator = if (spec.id == KeyboardSkinId.CUPERTINO) {
                OvershootInterpolator(1.35f)
            } else {
                AccelerateDecelerateInterpolator()
            }
        }
        return StateListAnimator().apply {
            addState(pressedEnabledState, pressed)
            addState(defaultState, released)
        }
    }

    private fun propertyAnimator(
        view: View,
        property: android.util.Property<View, Float>,
        target: Float,
        durationMs: Long,
    ): Animator = ObjectAnimator.ofFloat(view, property, target).apply {
        duration = durationMs
    }

    private fun pressedTextColor(spec: KeyboardSkinSpec, role: KeyboardElementRole): Int {
        return when (spec.id) {
            KeyboardSkinId.FLAT -> if (
                role == KeyboardElementRole.CHARACTER || role == KeyboardElementRole.SPACE
            ) {
                Color.WHITE
            } else {
                spec.palette.textColor(role)
            }

            KeyboardSkinId.TERMINAL -> if (role == KeyboardElementRole.ACTION) {
                spec.palette.backgroundColor
            } else {
                spec.palette.backgroundColor
            }

            KeyboardSkinId.NEON -> Color.WHITE
            KeyboardSkinId.WASHI -> ColorUtils.blendARGB(
                spec.palette.textColor(role),
                Color.BLACK,
                0.18f,
            )

            else -> spec.palette.textColor(role)
        }
    }

    private const val REDUCED_PRESSED_ALPHA = 0.88f
    private const val REDUCED_PRESS_MS = 55L
    private const val REDUCED_RELEASE_MS = 75L
}
