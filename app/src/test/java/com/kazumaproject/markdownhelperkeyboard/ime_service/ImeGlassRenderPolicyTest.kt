package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertEquals
import org.junit.Test

class ImeGlassRenderPolicyTest {

    @Test
    fun supportedBlurWindowUsesSystemBlur() {
        val decision = resolve(sdkInt = 36, blurEnabled = true)

        assertEquals(ImeGlassRenderMode.SYSTEM_BLUR, decision.mode)
        assertEquals(ImeGlassRenderPolicy.SYSTEM_BLUR_RADIUS, decision.windowBlurRadius)
    }

    @Test
    fun android17UsesOpaqueBackdrop() {
        val decision = resolve(sdkInt = 37, blurEnabled = true)

        assertEquals(ImeGlassRenderMode.OPAQUE_BACKDROP, decision.mode)
        assertEquals(0, decision.windowBlurRadius)
    }

    @Test
    fun unavailableBlurUsesOpaqueBackdrop() {
        val decision = resolve(sdkInt = 36, blurEnabled = false)

        assertEquals(ImeGlassRenderMode.OPAQUE_BACKDROP, decision.mode)
        assertEquals(0, decision.windowBlurRadius)
    }

    @Test
    fun unsupportedOrNonNormalModesNeverUseTransparentSystemBlur() {
        assertEquals(
            ImeGlassRenderMode.OPAQUE_BACKDROP,
            resolve(sdkInt = 30, blurEnabled = true).mode,
        )
        assertEquals(
            ImeGlassRenderMode.NO_BLUR,
            resolve(sdkInt = 36, blurEnabled = true, floatingMode = true).mode,
        )
        assertEquals(
            ImeGlassRenderMode.NO_BLUR,
            resolve(sdkInt = 36, blurEnabled = true, physicalKeyboardEnabled = true).mode,
        )
        assertEquals(
            ImeGlassRenderMode.NO_BLUR,
            resolve(sdkInt = 36, blurEnabled = true, hardwareKeyboardConnected = true).mode,
        )
    }

    @Test
    fun disabledGlassUsesNoBlur() {
        val decision = resolve(sdkInt = 36, blurEnabled = true, glassEnabled = false)

        assertEquals(ImeGlassRenderMode.NO_BLUR, decision.mode)
        assertEquals(0, decision.windowBlurRadius)
    }

    private fun resolve(
        sdkInt: Int,
        blurEnabled: Boolean,
        glassEnabled: Boolean = true,
        floatingMode: Boolean = false,
        physicalKeyboardEnabled: Boolean = false,
        hardwareKeyboardConnected: Boolean = false,
    ): ImeGlassRenderDecision {
        return ImeGlassRenderPolicy.resolve(
            sdkInt = sdkInt,
            glassEnabled = glassEnabled,
            floatingMode = floatingMode,
            physicalKeyboardEnabled = physicalKeyboardEnabled,
            hardwareKeyboardConnected = hardwareKeyboardConnected,
            crossWindowBlurEnabled = blurEnabled,
        )
    }
}
