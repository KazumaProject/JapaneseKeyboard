package com.kazumaproject.custom_keyboard.data

object KeyVisualStyleResolver {
    fun usesSpecialSurface(keyData: KeyData): Boolean =
        keyData.isSpecialKey &&
                keyData.specialKeyColorStyle == SpecialKeyColorStyle.SPECIAL
}
