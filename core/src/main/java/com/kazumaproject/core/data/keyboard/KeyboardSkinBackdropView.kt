package com.kazumaproject.core.data.keyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import java.util.concurrent.atomic.AtomicInteger

/**
 * Single low-frequency animated deck per visible keyboard. Key views only animate on interaction,
 * so rich skins do not create an animator for every key.
 */
class KeyboardSkinBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs), Choreographer.FrameCallback {
    private var skinRef: KeyboardSkinRef = KeyboardSkinRef.DEFAULT
    private var motionMode: KeyboardSkinMotionMode = KeyboardSkinMotionMode.FULL
    private var runtimeGeneration = KeyboardSkinRuntime.generation()
    private var deckDrawable: Drawable? = null
    private var startFrameNanos = 0L
    private var lastDrawNanos = 0L
    private var frameScheduled = false
    private var countedAsRunning = false

    init {
        setWillNotDraw(false)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    }

    fun setSkin(
        skin: KeyboardSkinId,
        motion: KeyboardSkinMotionMode = KeyboardSkinMotionMode.FULL,
    ) = setSkin(KeyboardSkinRef.BuiltIn(skin), motion)

    fun setSkin(
        skin: KeyboardSkinRef,
        motion: KeyboardSkinMotionMode = KeyboardSkinMotionMode.FULL,
    ) {
        val nextRuntimeGeneration = runtimeGenerationFor(skin)
        if (
            skin == skinRef &&
            motion == motionMode &&
            deckDrawable != null &&
            nextRuntimeGeneration == runtimeGeneration
        ) {
            updateFrameLoop()
            return
        }
        stopFrameLoop()
        skinRef = skin
        motionMode = motion
        runtimeGeneration = nextRuntimeGeneration
        deckDrawable = if (skin.isDefault()) {
            null
        } else {
            KeyboardSkinRendererRegistry.rendererFor(skin)
                .createSurfaceDrawable(context, KeyboardSurfaceRole.DECK)
        }
        visibility = if (skin.isDefault()) GONE else VISIBLE
        invalidate()
        updateFrameLoop()
    }

    private fun runtimeGenerationFor(skin: KeyboardSkinRef): Long =
        if (skin is KeyboardSkinRef.Imported) KeyboardSkinRuntime.generation() else 0L

    fun activeSkin(): KeyboardSkinId = (skinRef as? KeyboardSkinRef.BuiltIn)?.id
        ?: KeyboardSkinId.DEFAULT

    fun activeSkinRef(): KeyboardSkinRef = skinRef

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        deckDrawable?.let { drawable ->
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        frameScheduled = false
        if (!shouldAnimate()) {
            updateRunningCount(false)
            return
        }
        if (startFrameNanos == 0L) startFrameNanos = frameTimeNanos
        if (frameTimeNanos - lastDrawNanos >= FRAME_INTERVAL_NANOS) {
            val periodNanos = KeyboardSkinCatalog.specFor(skinRef).motion.continuousPeriodMs * 1_000_000L
            val phase = if (periodNanos > 0L) {
                ((frameTimeNanos - startFrameNanos) % periodNanos).toFloat() / periodNanos.toFloat()
            } else {
                0f
            }
            (deckDrawable as? PhasedKeyboardSkinDrawable)?.setPhase(phase)
            invalidate()
            lastDrawNanos = frameTimeNanos
        }
        scheduleFrame()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateFrameLoop()
    }

    override fun onDetachedFromWindow() {
        stopFrameLoop()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateFrameLoop()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateFrameLoop()
    }

    private fun updateFrameLoop() {
        if (shouldAnimate()) {
            scheduleFrame()
            updateRunningCount(true)
        } else {
            stopFrameLoop()
        }
    }

    private fun shouldAnimate(): Boolean {
        if (!isAttachedToWindow || visibility != VISIBLE || windowVisibility != VISIBLE) return false
        if (motionMode != KeyboardSkinMotionMode.FULL || skinRef.isDefault()) return false
        if (KeyboardSkinCatalog.specFor(skinRef).motion.continuousPeriodMs <= 0L) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
    }

    private fun scheduleFrame() {
        if (frameScheduled) return
        frameScheduled = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun stopFrameLoop() {
        if (frameScheduled) {
            Choreographer.getInstance().removeFrameCallback(this)
            frameScheduled = false
        }
        startFrameNanos = 0L
        lastDrawNanos = 0L
        updateRunningCount(false)
    }

    private fun updateRunningCount(running: Boolean) {
        if (countedAsRunning == running) return
        countedAsRunning = running
        if (running) activeAnimatorCount.incrementAndGet() else activeAnimatorCount.decrementAndGet()
    }

    companion object {
        private const val FRAME_INTERVAL_NANOS = 33_333_333L
        private val activeAnimatorCount = AtomicInteger(0)

        @JvmStatic
        fun activeAnimatorCountForTesting(): Int = activeAnimatorCount.get()
    }
}
