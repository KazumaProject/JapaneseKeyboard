package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.TextureView
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.VisibleForTesting
import timber.log.Timber

open class FluidInkEffectView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr), TextureView.SurfaceTextureListener {

    private val inputQueue = FluidInputCommandQueue()
    private val inputInjector = FluidInputInjector(inputQueue)

    @VisibleForTesting
    internal var rendererFactory: FluidInkRendererFactory = FluidInkRendererFactory { queue, callback ->
        FluidInkRenderer(
            inputQueue = queue,
            callback = callback
        )
    }

    private var renderer: FluidInkRendererController? = null
    private var effectEnabled = false
    private var currentSettings = FluidInkSettings.Disabled
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
        colorMode: String,
        @ColorInt fixedColor: Int
    ) {
        currentSettings = FluidInkSettings(
            enabled = enabled,
            colorMode = colorMode,
            fixedColor = FluidInkSettings.withoutTransparentAlpha(fixedColor)
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
        inputInjector.configure(
            colorMode = colorMode,
            fixedColor = fixedColor
        )
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
        if (inputInjector.onPointerUp(pointerId)) {
            renderer?.requestRender()
        }
    }

    fun onCancel() {
        if (!effectEnabled) return
        if (inputInjector.onCancel()) {
            renderer?.requestRender()
        }
    }

    fun clearInk() {
        inputInjector.clearActivePointers()
        inputQueue.clear()
        renderer?.clear()
    }

    fun pauseInk() {
        inputInjector.clearActivePointers()
        renderer?.pause()
    }

    fun releaseInk() {
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

    private fun canForwardInput(): Boolean {
        return effectEnabled && visibility == View.VISIBLE
    }

    private fun ensureRenderer(): FluidInkRendererController {
        renderer?.let { return it }
        return rendererFactory.create(
            inputQueue,
            FluidInkRendererCallback { reason, throwable ->
                Timber.w(throwable, "Suminagashi fluid effect disabled: %s", reason)
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
