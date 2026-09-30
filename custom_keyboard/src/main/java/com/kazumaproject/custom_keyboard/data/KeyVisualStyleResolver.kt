package com.kazumaproject.custom_keyboard.data

import com.kazumaproject.core.ui.skin.SkinKeyRole

object KeyVisualStyleResolver {
    fun usesSpecialSurface(keyData: KeyData): Boolean =
        keyData.isSpecialKey &&
                keyData.specialKeyColorStyle == SpecialKeyColorStyle.SPECIAL

    /** Space/Convert is one physical toggle key in several layouts. */
    fun isSpaceConvertToggle(keyData: KeyData): Boolean {
        if (!usesSpecialSurface(keyData)) return false
        val actions = keyData.dynamicStates.orEmpty().map { it.action }
        val hasSpace = actions.any {
            it == KeyAction.Space ||
                    it == KeyAction.ForceHalfWidthSpace ||
                    it == KeyAction.ForceFullWidthSpace
        }
        return hasSpace && KeyAction.Convert in actions
    }

    /**
     * Classic's button-style special Space keys use the modifier surface; the QWERTY spacebar
     * has its own renderer and continues to use the dedicated space surface.
     */
    fun resolveSkinKeyRole(
        keyData: KeyData,
        useModifierSurfaceForSpecialSpaceKeys: Boolean = false
    ): SkinKeyRole {
        val isSpaceAction = when (keyData.action) {
            KeyAction.Space, KeyAction.ForceHalfWidthSpace, KeyAction.ForceFullWidthSpace -> true
            else -> isSpaceConvertToggle(keyData)
        }
        if (useModifierSurfaceForSpecialSpaceKeys && isSpaceAction && usesSpecialSurface(keyData)) {
            return SkinKeyRole.MODIFIER
        }
        return when (keyData.action) {
            KeyAction.Space, KeyAction.ForceHalfWidthSpace, KeyAction.ForceFullWidthSpace ->
                SkinKeyRole.SPACE
            else -> if (usesSpecialSurface(keyData)) SkinKeyRole.MODIFIER else SkinKeyRole.CHARACTER
        }
    }
}
