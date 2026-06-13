package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.TextureView
import android.view.View
import androidx.annotation.VisibleForTesting
import timber.log.Timber

class LiquidRippleEffectView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr), TextureView.SurfaceTextureListener {

    private val inputQueue = LiquidRippleInputCommandQueue()
    private val inputInjector = LiquidRippleInputInjector(inputQueue)

    @VisibleForTesting
    internal var rendererFactory: LiquidRippleRendererFactory =
        LiquidRippleRendererFactory { queue, callback ->
            LiquidRippleRenderer(
                inputQueue = queue,
                callback = callback
            )
        }

    private var renderer: LiquidRippleRendererController? = null
    private var effectEnabled = false
    private var currentSettings = LiquidRippleSettings.Disabled
    private var attachedSurfaceTexture: SurfaceTexture? = null

    init {
        surfaceTextureListener = this
        setOpaque(false)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = View.GONE
    }

    fun configure(
        enabled: Boolean,
        quality: String = KeyboardTouchEffectQuality.HIGH
    ) {
        currentSettings = LiquidRippleSettings(
            enabled = enabled,
            quality = KeyboardTouchEffectQuality.normalize(quality)
        )

        if (!enabled) {
            effectEnabled = false
            inputInjector.disable()
            renderer?.clear()
            renderer?.release()
            renderer = null
            visibility = View.GONE
            return
        }

        effectEnabled = true
        inputInjector.configure(enabled = true)
        visibility = View.VISIBLE

        val activeRenderer = ensureRenderer()
        activeRenderer.configure(currentSettings)
        activeRenderer.resume()
        val surface = attachedSurfaceTexture ?: surfaceTexture
        if (surface != null && width > 0 && height > 0) {
            activeRenderer.attachSurface(surface, width, height)
        }
        activeRenderer.requestRender()
    }

    fun onPointerDown(pointerId: Int, x: Float, y: Float) {
        if (!canForwardInput()) return
        if (inputInjector.onPointerDown(pointerId, x, y)) {
            renderer?.requestRender()
        }
    }

    fun onPointerMove(pointerId: Int, x: Float, y: Float) {
        if (!canForwardInput()) return
        if (inputInjector.onPointerMove(pointerId, x, y)) {
            renderer?.requestRender()
        }
    }

    fun onPointerUp(pointerId: Int, x: Float? = null, y: Float? = null) {
        if (!effectEnabled) return
        if (inputInjector.onPointerUp(pointerId, x, y)) {
            renderer?.requestRender()
        }
    }

    fun onCancel() {
        if (!effectEnabled) return
        if (inputInjector.onCancel()) {
            renderer?.requestRender()
        }
    }

    fun clearRipple() {
        inputInjector.clearActivePointers()
        inputQueue.clear()
        renderer?.clear()
    }

    fun pauseRipple() {
        inputInjector.clearActivePointers()
        renderer?.pause()
    }

    fun releaseRipple() {
        inputInjector.disable()
        renderer?.release()
        renderer = null
        effectEnabled = false
        visibility = View.GONE
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        attachedSurfaceTexture = surface
        if (!effectEnabled) return
        ensureRenderer().attachSurface(surface, width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        attachedSurfaceTexture = surface
        if (!effectEnabled) return
        renderer?.resizeSurface(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        attachedSurfaceTexture = null
        renderer?.detachSurface()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    @VisibleForTesting
    internal fun pointerStateCountForTesting(): Int = inputInjector.pointerStateCountForTesting()

    @VisibleForTesting
    internal fun queuedInputCountForTesting(): Int = inputQueue.sizeForTesting()

    @VisibleForTesting
    internal fun hasRendererForTesting(): Boolean = renderer != null

    @SuppressLint("UseKtx")
    private fun canForwardInput(): Boolean {
        return effectEnabled && visibility == View.VISIBLE
    }

    private fun ensureRenderer(): LiquidRippleRendererController {
        renderer?.let { return it }
        return rendererFactory.create(
            inputQueue,
            LiquidRippleRendererCallback { reason, throwable ->
                Timber.w(throwable, "Keyboard liquid ripple effect disabled: %s", reason)
                disableAfterRendererFailure()
            }
        ).also { renderer = it }
    }

    private fun disableAfterRendererFailure() {
        if (!effectEnabled && renderer == null) return
        inputInjector.disable()
        renderer?.release()
        renderer = null
        effectEnabled = false
        visibility = View.GONE
    }
}
