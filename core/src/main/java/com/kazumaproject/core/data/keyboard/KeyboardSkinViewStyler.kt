package com.kazumaproject.core.data.keyboard

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.graphics.drawable.Drawable
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.widget.ImageViewCompat
import java.util.WeakHashMap

/** Applies a renderer without replacing the keyboard's existing touch listeners. */
object KeyboardSkinViewStyler {
    private val pressedEnabledState = intArrayOf(
        android.R.attr.state_pressed,
        android.R.attr.state_enabled,
    )
    private val defaultState = intArrayOf()
    private val originalStyles = WeakHashMap<View, OriginalStyle>()

    private data class OriginalStyle(
        val background: Drawable?,
        val backgroundState: Drawable.ConstantState?,
        val backgroundTint: ColorStateList?,
        val stateListAnimator: StateListAnimator?,
        val elevation: Float,
        val alpha: Float,
        val translationX: Float,
        val translationY: Float,
        val scaleX: Float,
        val scaleY: Float,
        val textColors: ColorStateList?,
        val typeface: Typeface?,
        val letterSpacing: Float?,
        val imageTint: ColorStateList?,
        val imageColorFilter: ColorFilter?,
    )

    fun applyKey(
        view: View,
        skinId: KeyboardSkinId,
        role: KeyboardElementRole,
        motionMode: KeyboardSkinMotionMode = KeyboardSkinMotionMode.FULL,
        stableKey: Int = view.id,
    ) = applyKey(view, KeyboardSkinRef.BuiltIn(skinId), role, motionMode, stableKey)

    fun applyKey(
        view: View,
        skinRef: KeyboardSkinRef,
        role: KeyboardElementRole,
        motionMode: KeyboardSkinMotionMode = KeyboardSkinMotionMode.FULL,
        stableKey: Int = view.id,
    ) {
        if (skinRef.isDefault()) {
            clearTransientStyle(view)
            return
        }
        rememberOriginalStyle(view)
        clearTransientStyle(view)
        val spec = KeyboardSkinCatalog.specFor(skinRef)
        val renderer = KeyboardSkinRendererRegistry.rendererFor(skinRef)
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
    ) = applySurface(view, KeyboardSkinRef.BuiltIn(skinId), role)

    fun applySurface(
        view: View,
        skinRef: KeyboardSkinRef,
        role: KeyboardSurfaceRole,
    ) {
        if (skinRef.isDefault()) {
            clearTransientStyle(view)
            return
        }
        rememberOriginalStyle(view)
        clearTransientStyle(view)
        view.backgroundTintList = null
        view.background = KeyboardSkinRendererRegistry.rendererFor(skinRef)
            .createSurfaceDrawable(view.context, role)
    }

    /**
     * Applies skin color and typography to chrome controls without turning them into keycaps.
     * No background geometry is drawn in either state. Press feedback only fades the content,
     * so toolbar shortcuts and tabs never read as keycaps on their already styled surface.
     */
    fun applyFlatControl(
        view: View,
        skinId: KeyboardSkinId,
        role: KeyboardElementRole = KeyboardElementRole.TOOLBAR,
        tintContent: Boolean = true,
    ) = applyFlatControl(view, KeyboardSkinRef.BuiltIn(skinId), role, tintContent)

    fun applyFlatControl(
        view: View,
        skinRef: KeyboardSkinRef,
        role: KeyboardElementRole = KeyboardElementRole.TOOLBAR,
        tintContent: Boolean = true,
    ) {
        if (skinRef.isDefault()) {
            clearTransientStyle(view)
            return
        }
        rememberOriginalStyle(view)
        clearTransientStyle(view)
        view.backgroundTintList = null
        view.background = null
        view.elevation = 0f

        val spec = KeyboardSkinCatalog.specFor(skinRef)
        view.stateListAnimator = createFlatControlStateAnimator(view)
        if (tintContent) {
            applyFlatControlContent(view, spec, role)
        }
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
        restoreOriginalStyle(view)
    }

    private fun rememberOriginalStyle(view: View) {
        if (originalStyles.containsKey(view)) return
        originalStyles[view] = OriginalStyle(
            background = view.background,
            backgroundState = view.background?.constantState,
            backgroundTint = view.backgroundTintList,
            stateListAnimator = view.stateListAnimator,
            elevation = view.elevation,
            alpha = view.alpha,
            translationX = view.translationX,
            translationY = view.translationY,
            scaleX = view.scaleX,
            scaleY = view.scaleY,
            textColors = (view as? TextView)?.textColors,
            typeface = (view as? TextView)?.typeface,
            letterSpacing = (view as? TextView)?.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP }
                ?.letterSpacing,
            imageTint = (view as? ImageView)?.let(ImageViewCompat::getImageTintList),
            imageColorFilter = (view as? ImageView)?.colorFilter,
        )
    }

    private fun restoreOriginalStyle(view: View) {
        val original = originalStyles[view] ?: return
        view.background = original.backgroundState?.newDrawable(view.resources)?.mutate() ?: original.background
        view.backgroundTintList = original.backgroundTint
        view.stateListAnimator = original.stateListAnimator
        view.elevation = original.elevation
        view.alpha = original.alpha
        view.translationX = original.translationX
        view.translationY = original.translationY
        view.scaleX = original.scaleX
        view.scaleY = original.scaleY
        if (view is TextView) {
            original.textColors?.let(view::setTextColor)
            original.typeface?.let { view.typeface = it }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && original.letterSpacing != null) {
                view.letterSpacing = original.letterSpacing
            }
        }
        if (view is ImageView) {
            ImageViewCompat.setImageTintList(view, original.imageTint)
            view.colorFilter = original.imageColorFilter
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
            interpolator = if (spec.material == KeyboardSkinMaterial.CUPERTINO) {
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

    private fun createFlatControlStateAnimator(view: View): StateListAnimator =
        StateListAnimator().apply {
            addState(
                pressedEnabledState,
                propertyAnimator(
                    view,
                    View.ALPHA,
                    FLAT_CONTROL_PRESSED_CONTENT_ALPHA,
                    FLAT_CONTROL_PRESS_MS,
                ),
            )
            addState(
                defaultState,
                propertyAnimator(view, View.ALPHA, 1f, FLAT_CONTROL_RELEASE_MS),
            )
        }

    private fun applyFlatControlContent(
        view: View,
        spec: KeyboardSkinSpec,
        role: KeyboardElementRole,
    ) {
        val contentColor = spec.palette.textColor(role)
        when (view) {
            is TextView -> {
                view.setTextColor(contentColor)
                view.typeface = Typeface.create(
                    spec.typography.familyName,
                    if (spec.typography.bold) Typeface.BOLD else Typeface.NORMAL,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    view.letterSpacing = spec.typography.letterSpacing
                }
            }

            is ImageView -> view.setColorFilter(contentColor, PorterDuff.Mode.SRC_IN)
            is ViewGroup -> for (index in 0 until view.childCount) {
                applyFlatControlContent(view.getChildAt(index), spec, role)
            }
        }
    }

    private fun pressedTextColor(spec: KeyboardSkinSpec, role: KeyboardElementRole): Int {
        return when {
            spec.reference is KeyboardSkinRef.Imported -> spec.palette.textColor(role)
            spec.id == KeyboardSkinId.FLAT -> if (
                role == KeyboardElementRole.CHARACTER || role == KeyboardElementRole.SPACE
            ) {
                Color.WHITE
            } else {
                spec.palette.textColor(role)
            }

            spec.id == KeyboardSkinId.TERMINAL -> if (role == KeyboardElementRole.ACTION) {
                spec.palette.backgroundColor
            } else {
                spec.palette.backgroundColor
            }

            spec.id == KeyboardSkinId.NEON -> Color.WHITE
            spec.id == KeyboardSkinId.WASHI -> ColorUtils.blendARGB(
                spec.palette.textColor(role),
                Color.BLACK,
                0.18f,
            )

            spec.id == KeyboardSkinId.MONOCHROME_LCD -> if (role == KeyboardElementRole.ACTION) {
                spec.palette.actionKeyTextColor
            } else {
                spec.palette.normalKeyColor
            }

            else -> spec.palette.textColor(role)
        }
    }

    private const val REDUCED_PRESSED_ALPHA = 0.88f
    private const val REDUCED_PRESS_MS = 55L
    private const val REDUCED_RELEASE_MS = 75L
    private const val FLAT_CONTROL_PRESSED_CONTENT_ALPHA = 0.72f
    private const val FLAT_CONTROL_PRESS_MS = 45L
    private const val FLAT_CONTROL_RELEASE_MS = 70L
}
