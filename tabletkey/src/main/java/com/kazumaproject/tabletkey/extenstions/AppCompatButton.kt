package com.kazumaproject.tabletkey.extenstions

import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import com.kazumaproject.tabletkey.R

var KEY_JAPANESE_SIZE = 17f
var KEY_ENGLISH_SIZE = 14f
var KEY_NUMBER_SIZE = 18f

fun AppCompatButton.setTabletKeyTextJapanese(keyId: Int) {
    textSize = KEY_JAPANESE_SIZE
    setTextColor(
        ContextCompat.getColor(
            context,
            com.kazumaproject.core.R.color.keyboard_icon_color
        )
    )
    text = when (keyId) {
        R.id.key_1 -> context.getString(com.kazumaproject.core.R.string.string_quotation)
        R.id.key_2 -> context.getString(com.kazumaproject.core.R.string.question)
        R.id.key_3 -> context.getString(com.kazumaproject.core.R.string.mark)
        R.id.key_4 -> context.getString(com.kazumaproject.core.R.string.string_touten)
        R.id.key_5 -> context.getString(com.kazumaproject.core.R.string.string_kuten)
        R.id.key_6 -> context.getString(com.kazumaproject.core.R.string.string_わ)
        R.id.key_7 -> context.getString(com.kazumaproject.core.R.string.string_を)
        R.id.key_8 -> context.getString(com.kazumaproject.core.R.string.string_ん)
        R.id.key_9 -> context.getString(com.kazumaproject.core.R.string.string_minus)
        R.id.key_10 -> context.getString(com.kazumaproject.core.R.string.string_small)
        R.id.key_11 -> context.getString(com.kazumaproject.core.R.string.string_ら)
        R.id.key_12 -> context.getString(com.kazumaproject.core.R.string.string_り)
        R.id.key_13 -> context.getString(com.kazumaproject.core.R.string.string_る)
        R.id.key_14 -> context.getString(com.kazumaproject.core.R.string.string_れ)
        R.id.key_15 -> context.getString(com.kazumaproject.core.R.string.string_ろ)
        R.id.key_16 -> context.getString(com.kazumaproject.core.R.string.string_や)
        R.id.key_17 -> ""
        R.id.key_18 -> context.getString(com.kazumaproject.core.R.string.string_ゆ)
        R.id.key_19 -> ""
        R.id.key_20 -> context.getString(com.kazumaproject.core.R.string.string_よ)
        R.id.key_21 -> context.getString(com.kazumaproject.core.R.string.string_ま)
        R.id.key_22 -> context.getString(com.kazumaproject.core.R.string.string_み)
        R.id.key_23 -> context.getString(com.kazumaproject.core.R.string.string_む)
        R.id.key_24 -> context.getString(com.kazumaproject.core.R.string.string_め)
        R.id.key_25 -> context.getString(com.kazumaproject.core.R.string.string_も)
        R.id.key_26 -> context.getString(com.kazumaproject.core.R.string.string_は)
        R.id.key_27 -> context.getString(com.kazumaproject.core.R.string.string_ひ)
        R.id.key_28 -> context.getString(com.kazumaproject.core.R.string.string_ふ)
        R.id.key_29 -> context.getString(com.kazumaproject.core.R.string.string_へ)
        R.id.key_30 -> context.getString(com.kazumaproject.core.R.string.string_ほ)
        R.id.key_31 -> context.getString(com.kazumaproject.core.R.string.string_な)
        R.id.key_32 -> context.getString(com.kazumaproject.core.R.string.string_に)
        R.id.key_33 -> context.getString(com.kazumaproject.core.R.string.string_ぬ)
        R.id.key_34 -> context.getString(com.kazumaproject.core.R.string.string_ね)
        R.id.key_35 -> context.getString(com.kazumaproject.core.R.string.string_の)
        R.id.key_36 -> context.getString(com.kazumaproject.core.R.string.string_た)
        R.id.key_37 -> context.getString(com.kazumaproject.core.R.string.string_ち)
        R.id.key_38 -> context.getString(com.kazumaproject.core.R.string.string_つ)
        R.id.key_39 -> context.getString(com.kazumaproject.core.R.string.string_て)
        R.id.key_40 -> context.getString(com.kazumaproject.core.R.string.string_と)
        R.id.key_41 -> context.getString(com.kazumaproject.core.R.string.string_さ)
        R.id.key_42 -> context.getString(com.kazumaproject.core.R.string.string_し)
        R.id.key_43 -> context.getString(com.kazumaproject.core.R.string.string_す)
        R.id.key_44 -> context.getString(com.kazumaproject.core.R.string.string_せ)
        R.id.key_45 -> context.getString(com.kazumaproject.core.R.string.string_そ)
        R.id.key_46 -> context.getString(com.kazumaproject.core.R.string.string_か)
        R.id.key_47 -> context.getString(com.kazumaproject.core.R.string.string_き)
        R.id.key_48 -> context.getString(com.kazumaproject.core.R.string.string_く)
        R.id.key_49 -> context.getString(com.kazumaproject.core.R.string.string_け)
        R.id.key_50 -> context.getString(com.kazumaproject.core.R.string.string_こ)
        R.id.key_51 -> context.getString(com.kazumaproject.core.R.string.string_あ)
        R.id.key_52 -> context.getString(com.kazumaproject.core.R.string.string_い)
        R.id.key_53 -> context.getString(com.kazumaproject.core.R.string.string_う)
        R.id.key_54 -> context.getString(com.kazumaproject.core.R.string.string_え)
        R.id.key_55 -> context.getString(com.kazumaproject.core.R.string.string_お)
        else -> ""
    }
}

private fun getSpannableStringForNumberButton(str: String): SpannableString {
    val spannable = SpannableString(str)
    spannable.setSpan(RelativeSizeSpan(1f), 0, 1, 0)
    if (str.length > 1) {
        spannable.setSpan(RelativeSizeSpan(0.5f), 1, str.length, 0)
    }
    return spannable
}