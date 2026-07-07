package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.TextureView
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.VisibleForTesting
import timber.log.Timber

class CinematicWaveEffectView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr), TextureView.SurfaceTextureListener {

    private val inputQueue = CinematicWaveInputCommandQueue()
    private val inputInjector = CinematicWaveInputInjector(inputQueue)

    @VisibleForTesting
    internal var rendererFactory: CinematicWaveRendererFactory =
        CinematicWaveRendererFactory { queue, callback ->
            CinematicWaveRenderer(
                inputQueue = queue,
                callback = callback
            )
        }

    private var renderer: CinematicWaveRendererController? = null
    private var effectEnabled = false
    private var currentSettings = CinematicWaveSettings.Disabled
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
        @ColorInt primaryColor: Int,
        @ColorInt secondaryColor: Int,
        secondaryColorAuto: Boolean,
        waveType: String,
        opacityPercent: Int,
        intensityPercent: Int,
        motion: String,
        touchResponse: String,
        quality: String
    ) {
        currentSettings = CinematicWaveSettings(
            enabled = enabled,
            colorMode = colorMode,
            primaryColor = CinematicWaveSettings.withoutTransparentAlpha(primaryColor),
            secondaryColor = CinematicWaveSettings.withoutTransparentAlpha(secondaryColor),
            secondaryColorAuto = secondaryColorAuto,
            waveType = waveType,
            opacityPercent = opacityPercent,
            intensityPercent = intensityPercent,
            motion = motion,
            touchResponse = touchResponse,
            quality = quality
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

    fun onPointerDown(pointerId: Int, x: Float, y: Float, pressure: Float = 1f) {
        if (!canForwardInput()) return
        if (inputInjector.onPointerDown(pointerId, x, y, pressure)) {
            renderer?.requestRender()
        }
    }

    fun onPointerMove(pointerId: Int, x: Float, y: Float, pressure: Float = 1f) {
        if (!canForwardInput()) return
        if (inputInjector.onPointerMove(pointerId, x, y, pressure)) {
            renderer?.requestRender()
        }
    }

    fun onPointerUp(
        pointerId: Int,
        x: Float? = null,
        y: Float? = null,
        pressure: Float = 1f
    ) {
        if (!effectEnabled) return
        if (inputInjector.onPointerUp(pointerId, x, y, pressure)) {
            renderer?.requestRender()
        }
    }

    fun onCancel() {
        if (!effectEnabled) return
        if (inputInjector.onCancel()) {
            renderer?.requestRender()
        }
    }

    fun clearWave() {
        inputInjector.clearActivePointers()
        inputQueue.clear()
        renderer?.clear()
    }

    fun pauseWave() {
        inputInjector.clearActivePointers()
        renderer?.pause()
    }

    fun releaseWave() {
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

    private fun ensureRenderer(): CinematicWaveRendererController {
        renderer?.let { return it }
        return rendererFactory.create(
            inputQueue,
            CinematicWaveRendererCallback { reason, throwable ->
                Timber.w(throwable, "Keyboard cinematic wave effect disabled: %s", reason)
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
