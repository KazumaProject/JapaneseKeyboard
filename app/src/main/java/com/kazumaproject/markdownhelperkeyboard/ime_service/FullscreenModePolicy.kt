package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.view.inputmethod.EditorInfo

internal object FullscreenModePolicy {

    fun shouldUseFullscreenMode(
        frameworkRequestsFullscreen: Boolean,
        fullscreenModeAllowed: Boolean,
        imeOptions: Int,
    ): Boolean =
        frameworkRequestsFullscreen &&
            fullscreenModeAllowed &&
            imeOptions and EditorInfo.IME_FLAG_NO_EXTRACT_UI == 0
}
