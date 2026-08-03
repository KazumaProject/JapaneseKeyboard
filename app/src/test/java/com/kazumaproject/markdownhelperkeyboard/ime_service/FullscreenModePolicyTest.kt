package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullscreenModePolicyTest {

    @Test
    fun usesFullscreenWhenFrameworkRequestsItAndUserAllowsIt() {
        assertTrue(
            FullscreenModePolicy.shouldUseFullscreenMode(
                frameworkRequestsFullscreen = true,
                fullscreenModeAllowed = true,
                imeOptions = 0,
            )
        )
    }

    @Test
    fun rejectsFullscreenWhenFrameworkDoesNotRequestIt() {
        assertFalse(
            FullscreenModePolicy.shouldUseFullscreenMode(
                frameworkRequestsFullscreen = false,
                fullscreenModeAllowed = true,
                imeOptions = 0,
            )
        )
    }

    @Test
    fun rejectsFullscreenWhenUserDisallowsIt() {
        assertFalse(
            FullscreenModePolicy.shouldUseFullscreenMode(
                frameworkRequestsFullscreen = true,
                fullscreenModeAllowed = false,
                imeOptions = 0,
            )
        )
    }

    @Test
    fun rejectsFullscreenWhenEditorDisablesExtractUi() {
        assertFalse(
            FullscreenModePolicy.shouldUseFullscreenMode(
                frameworkRequestsFullscreen = true,
                fullscreenModeAllowed = true,
                imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI,
            )
        )
    }
}
