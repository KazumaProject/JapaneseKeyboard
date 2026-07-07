package com.kazumaproject.markdownhelperkeyboard.sumire_special_key

import androidx.annotation.DrawableRes
import com.kazumaproject.custom_keyboard.data.KeyAction

data class SumireSpecialKeyActionDisplayMetadata(
    val action: KeyAction,
    val displayName: String,
    @DrawableRes val iconResId: Int? = null
)

