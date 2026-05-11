package com.kazumaproject.markdownhelperkeyboard.sumire_special_key

import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.ResolvedSumireSpecialKeyAction
import com.kazumaproject.custom_keyboard.data.SumireSpecialKeyDirection
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyActionOverrideEntity

class SumireSpecialKeyActionResolver(
    private val overrides: List<SumireSpecialKeyActionOverrideEntity>
) {
    fun resolve(
        layoutType: String,
        inputMode: String,
        keyData: KeyData,
        direction: SumireSpecialKeyDirection
    ): ResolvedSumireSpecialKeyAction {
        if (!keyData.isSpecialKey) return ResolvedSumireSpecialKeyAction.Default

        val keyId = keyData.keyId?.takeIf { it.isNotBlank() }
            ?: return ResolvedSumireSpecialKeyAction.Default

        val override = overrides.firstOrNull {
            it.layoutType == layoutType &&
                    it.inputMode == inputMode &&
                    it.keyId == keyId &&
                    it.direction == direction.name
        } ?: return ResolvedSumireSpecialKeyAction.Default

        return when (override.overrideType) {
            SumireSpecialKeyOverrideType.DEFAULT.name ->
                ResolvedSumireSpecialKeyAction.Default

            SumireSpecialKeyOverrideType.NONE.name ->
                ResolvedSumireSpecialKeyAction.None

            SumireSpecialKeyOverrideType.KEY_ACTION.name -> {
                val action = KeyActionMapper.toKeyAction(override.actionString)
                if (action != null) {
                    ResolvedSumireSpecialKeyAction.Action(action)
                } else {
                    ResolvedSumireSpecialKeyAction.Default
                }
            }

            SumireSpecialKeyOverrideType.INPUT_TEXT.name -> {
                val text = override.inputText.orEmpty()
                if (text.isNotEmpty()) {
                    ResolvedSumireSpecialKeyAction.InputText(text)
                } else {
                    ResolvedSumireSpecialKeyAction.Default
                }
            }

            else -> ResolvedSumireSpecialKeyAction.Default
        }
    }
}

