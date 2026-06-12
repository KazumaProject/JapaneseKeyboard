package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.graphics.SurfaceTexture

internal fun interface FluidInkRendererFactory {
    fun create(
        inputQueue: FluidInputCommandQueue,
        callback: FluidInkRendererCallback
    ): FluidInkRendererController
}

internal fun interface FluidInkRendererCallback {
    fun onRendererDisabled(reason: String, throwable: Throwable?)
}

internal interface FluidInkRendererController {
    fun configure(settings: FluidInkSettings)
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
