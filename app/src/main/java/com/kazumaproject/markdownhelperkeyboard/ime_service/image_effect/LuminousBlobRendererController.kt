package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.graphics.SurfaceTexture

internal fun interface LuminousBlobRendererFactory {
    fun create(
        inputQueue: LuminousBlobInputCommandQueue,
        callback: LuminousBlobRendererCallback
    ): LuminousBlobRendererController
}

internal fun interface LuminousBlobRendererCallback {
    fun onRendererDisabled(reason: String, throwable: Throwable?)
}

internal interface LuminousBlobRendererController {
    fun configure(settings: LuminousBlobSettings)
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
