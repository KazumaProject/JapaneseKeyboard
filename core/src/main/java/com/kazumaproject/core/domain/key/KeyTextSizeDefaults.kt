package com.kazumaproject.core.domain.key

import com.kazumaproject.core.domain.state.InputMode

object KeyTextSizeDefaults {
    const val TenKeyJapaneseSp = 20f
    const val TenKeyEnglishSp = 12f
    const val TenKeyNumberSp = 16f
    const val SumireKeySp = 20f
    const val SumireSpecialKeySp = 12f

    fun tenKeySizeSp(inputMode: InputMode): Float = when (inputMode) {
        InputMode.ModeJapanese -> TenKeyJapaneseSp
        InputMode.ModeEnglish -> TenKeyEnglishSp
        InputMode.ModeNumber -> TenKeyNumberSp
    }
}
