package com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior

import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode

internal object QwertyEnglishDirectInputPolicy {
    fun shouldForceDirectCommit(
        preferenceEnabled: Boolean,
        qwertyMode: TenKeyQWERTYMode,
        inputMode: InputMode,
        currentQwertyRomajiModeForSession: Boolean,
    ): Boolean {
        if (!preferenceEnabled) return false
        if (inputMode != InputMode.ModeEnglish) return false
        if (currentQwertyRomajiModeForSession) return false

        return qwertyMode == TenKeyQWERTYMode.TenKeyQWERTY ||
                qwertyMode == TenKeyQWERTYMode.TenKeyQWERTYRomaji
    }
}
