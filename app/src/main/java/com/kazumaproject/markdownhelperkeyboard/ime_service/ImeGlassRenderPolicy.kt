package com.kazumaproject.markdownhelperkeyboard.ime_service

/**
 * Rendering modes for the normal IME surface.
 *
 * The system blur is deliberately limited to the Android releases where this IME's
 * translucent-window path is known to be stable.  The opaque fallback keeps the glass-like
 * key surfaces while ensuring that an editor frame can never be sampled through the IME root.
 */
internal enum class ImeGlassRenderMode {
    SYSTEM_BLUR,
    OPAQUE_BACKDROP,
    NO_BLUR,
}

internal data class ImeGlassRenderDecision(
    val mode: ImeGlassRenderMode,
    val windowBlurRadius: Int,
)

internal object ImeGlassRenderPolicy {
    const val SYSTEM_BLUR_MIN_API = 31
    const val SYSTEM_BLUR_MAX_API = 36
    const val SYSTEM_BLUR_RADIUS = 50

    fun resolve(
        sdkInt: Int,
        glassEnabled: Boolean,
        floatingMode: Boolean,
        physicalKeyboardEnabled: Boolean,
        hardwareKeyboardConnected: Boolean,
        crossWindowBlurEnabled: Boolean,
    ): ImeGlassRenderDecision {
        if (
            !glassEnabled ||
            floatingMode ||
            physicalKeyboardEnabled ||
            hardwareKeyboardConnected
        ) {
            return ImeGlassRenderDecision(
                mode = ImeGlassRenderMode.NO_BLUR,
                windowBlurRadius = 0,
            )
        }

        val canUseSystemBlur =
            sdkInt in SYSTEM_BLUR_MIN_API..SYSTEM_BLUR_MAX_API &&
                crossWindowBlurEnabled

        return if (canUseSystemBlur) {
            ImeGlassRenderDecision(
                mode = ImeGlassRenderMode.SYSTEM_BLUR,
                windowBlurRadius = SYSTEM_BLUR_RADIUS,
            )
        } else {
            ImeGlassRenderDecision(
                mode = ImeGlassRenderMode.OPAQUE_BACKDROP,
                windowBlurRadius = 0,
            )
        }
    }
}
