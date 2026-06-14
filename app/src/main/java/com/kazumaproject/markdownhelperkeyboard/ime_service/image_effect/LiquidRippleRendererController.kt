package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.graphics.SurfaceTexture

internal fun interface LiquidRippleRendererFactory {
    fun create(
        inputQueue: LiquidRippleInputCommandQueue,
        callback: LiquidRippleRendererCallback
    ): LiquidRippleRendererController
}

internal fun interface LiquidRippleRendererCallback {
    fun onRendererDisabled(reason: String, throwable: Throwable?)
}

internal interface LiquidRippleRendererController {
    fun configure(settings: LiquidRippleSettings)
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
