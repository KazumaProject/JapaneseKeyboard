package com.kazumaproject.markdownhelperkeyboard.ime_service

object QwertyGlideInputModeResolver {
    fun resolve(
        qwertyGlideInputPreference: Boolean,
        isQwertySurfaceActive: Boolean,
        currentQwertyRomajiModeForSession: Boolean
    ): Boolean {
        return qwertyGlideInputPreference &&
                isQwertySurfaceActive &&
                !currentQwertyRomajiModeForSession
    }
}
