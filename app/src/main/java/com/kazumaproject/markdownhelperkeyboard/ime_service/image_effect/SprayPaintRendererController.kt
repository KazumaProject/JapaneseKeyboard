package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.graphics.SurfaceTexture

internal fun interface SprayPaintRendererFactory {
    fun create(
        inputQueue: SprayPaintInputCommandQueue,
        callback: SprayPaintRendererCallback
    ): SprayPaintRendererController
}

internal fun interface SprayPaintRendererCallback {
    fun onRendererDisabled(reason: String, throwable: Throwable?)
}

internal interface SprayPaintRendererController {
    fun configure(settings: SprayPaintSettings)
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
