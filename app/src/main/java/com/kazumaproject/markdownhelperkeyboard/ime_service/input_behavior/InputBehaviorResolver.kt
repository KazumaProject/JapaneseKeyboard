package com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior

import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputTypeForIME

fun interface TypeNullInputBehaviorPreferenceProvider {
    fun typeNullInputBehavior(): TypeNullInputBehaviorSetting
}

class InputBehaviorResolver(
    private val preferenceProvider: TypeNullInputBehaviorPreferenceProvider,
) {
    fun resolve(inputTypeForIME: InputTypeForIME): ResolvedInputBehavior {
        if (inputTypeForIME != InputTypeForIME.TypeNull) {
            return ResolvedInputBehavior.COMPOSING_TEXT
        }

        return when (preferenceProvider.typeNullInputBehavior()) {
            TypeNullInputBehaviorSetting.DEFAULT,
            TypeNullInputBehaviorSetting.DIRECT_COMMIT -> ResolvedInputBehavior.DIRECT_COMMIT

            TypeNullInputBehaviorSetting.COMPOSING_TEXT -> ResolvedInputBehavior.COMPOSING_TEXT
        }
    }
}
