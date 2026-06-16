package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.graphics.SurfaceTexture

internal fun interface CinematicWaveRendererFactory {
    fun create(
        inputQueue: CinematicWaveInputCommandQueue,
        callback: CinematicWaveRendererCallback
    ): CinematicWaveRendererController
}

internal fun interface CinematicWaveRendererCallback {
    fun onRendererDisabled(reason: String, throwable: Throwable?)
}

internal interface CinematicWaveRendererController {
    fun configure(settings: CinematicWaveSettings)
    fun attachSurface(surfaceTexture: SurfaceTexture, width: Int, height: Int)
    fun resizeSurface(width: Int, height: Int)
    fun detachSurface()
    fun resume()
    fun requestRender()
    fun clear()
    fun pause()
    fun release()
    fun isRendererThreadAliveForTesting(): Boolean
}
