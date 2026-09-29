package com.kazumaproject.custom_keyboard.data

import com.kazumaproject.core.ui.skin.SkinKeyRole

object KeyVisualStyleResolver {
    fun usesSpecialSurface(keyData: KeyData): Boolean =
        keyData.isSpecialKey &&
                keyData.specialKeyColorStyle == SpecialKeyColorStyle.SPECIAL

    /**
     * Space/Convert is one physical toggle key in several layouts. Classic assigns distinct
     * colors to space and modifier roles, so keep that key's surface stable across its states.
     */
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

    fun resolveSkinKeyRole(
        keyData: KeyData,
        keepSpaceConvertToggleSurface: Boolean = false
    ): SkinKeyRole {
        if (keepSpaceConvertToggleSurface && isSpaceConvertToggle(keyData)) {
            return SkinKeyRole.MODIFIER
        }
        return when (keyData.action) {
            KeyAction.Space, KeyAction.ForceHalfWidthSpace, KeyAction.ForceFullWidthSpace ->
                SkinKeyRole.SPACE
            else -> if (usesSpecialSurface(keyData)) SkinKeyRole.MODIFIER else SkinKeyRole.CHARACTER
        }
    }
}
