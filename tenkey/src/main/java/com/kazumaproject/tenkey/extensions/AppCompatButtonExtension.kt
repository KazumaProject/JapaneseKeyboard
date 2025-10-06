package com.kazumaproject.tenkey.extensions

import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import com.kazumaproject.core.domain.extensions.JP_KEY_LAYOUT_WITH_SPACE
import com.kazumaproject.core.domain.extensions.KEY_ENGLISH_SIZE
import com.kazumaproject.core.domain.extensions.KEY_JAPANESE_SIZE
import com.kazumaproject.core.domain.extensions.KEY_NUMBER_SIZE
import com.kazumaproject.core.domain.extensions.getSpannableStringForKigouButtonJapanese
import com.kazumaproject.core.domain.extensions.getSpannableStringForNumberButton
import com.kazumaproject.tenkey.R

fun AppCompatButton.setTenKeyTextJapanese(
    keyId: Int,
    delta: Int
) {
    textSize = KEY_JAPANESE_SIZE + delta
    setTextColor(
        ContextCompat.getColor(
            context,
            com.kazumaproject.core.R.color.keyboard_icon_color
        )
    )
    text = when (keyId) {
        R.id.key_1 -> context.getString(com.kazumaproject.core.R.string.string_あ)
        R.id.key_2 -> context.getString(com.kazumaproject.core.R.string.string_か)
        R.id.key_3 -> context.getString(com.kazumaproject.core.R.string.string_さ)
        R.id.key_4 -> context.getString(com.kazumaproject.core.R.string.string_た)
        R.id.key_5 -> context.getString(com.kazumaproject.core.R.string.string_な)
        R.id.key_6 -> context.getString(com.kazumaproject.core.R.string.string_は)
        R.id.key_7 -> context.getString(com.kazumaproject.core.R.string.string_ま)
        R.id.key_8 -> context.getString(com.kazumaproject.core.R.string.string_や)
        R.id.key_9 -> context.getString(com.kazumaproject.core.R.string.string_ら)
        R.id.key_11 -> context.getString(com.kazumaproject.core.R.string.string_わ)
        R.id.key_12 -> getSpannableStringForKigouButtonJapanese()
        else -> ""
    }
}

fun AppCompatButton.setTenKeyTextEnglish(
    keyId: Int
) {
    textSize = KEY_ENGLISH_SIZE
    setTextColor(
        ContextCompat.getColor(
            context,
            com.kazumaproject.core.R.color.keyboard_icon_color
        )
    )
    text = when (keyId) {
        R.id.key_1 -> context.getString(com.kazumaproject.core.R.string.string_key1_english)
        R.id.key_2 -> context.getString(com.kazumaproject.core.R.string.string_key2_english)
        R.id.key_3 -> context.getString(com.kazumaproject.core.R.string.string_key3_english)
        R.id.key_4 -> context.getString(com.kazumaproject.core.R.string.string_key4_english)
        R.id.key_5 -> context.getString(com.kazumaproject.core.R.string.string_key5_english)
        R.id.key_6 -> context.getString(com.kazumaproject.core.R.string.string_key6_english)
        R.id.key_7 -> context.getString(com.kazumaproject.core.R.string.string_key7_english)
        R.id.key_8 -> context.getString(com.kazumaproject.core.R.string.string_key8_english)
        R.id.key_9 -> context.getString(com.kazumaproject.core.R.string.string_key9_english)
        R.id.key_11 -> context.getString(com.kazumaproject.core.R.string.string_key10_english)
        R.id.key_12 -> context.getString(com.kazumaproject.core.R.string.string_key12_english)
        else -> ""
    }
}

fun AppCompatButton.setTenKeyTextNumber(
    keyId: Int
) {
    textSize = KEY_NUMBER_SIZE
    setTextColor(
        ContextCompat.getColor(
            context,
            com.kazumaproject.core.R.color.keyboard_icon_color
        )
    )
    text = when (keyId) {
        R.id.key_1 -> getSpannableStringForNumberButton(context.getString(com.kazumaproject.core.R.string.string_key1_number))
        R.id.key_2 -> getSpannableStringForNumberButton(context.getString(com.kazumaproject.core.R.string.string_key2_number))
        R.id.key_3 -> getSpannableStringForNumberButton(context.getString(com.kazumaproject.core.R.string.string_key3_number))
        R.id.key_4 -> getSpannableStringForNumberButton(context.getString(com.kazumaproject.core.R.string.string_key4_number))
        R.id.key_5 -> getSpannableStringForNumberButton(context.getString(com.kazumaproject.core.R.string.string_key5_number))
        R.id.key_6 -> getSpannableStringForNumberButton(context.getString(com.kazumaproject.core.R.string.string_key6_number))
        R.id.key_7 -> getSpannableStringForNumberButton(context.getString(com.kazumaproject.core.R.string.string_key7_number))
        R.id.key_8 -> getSpannableStringForNumberButton(context.getString(com.kazumaproject.core.R.string.string_key8_number))
        R.id.key_9 -> getSpannableStringForNumberButton(context.getString(com.kazumaproject.core.R.string.string_key9_number))
        R.id.key_11 -> getSpannableStringForNumberButton(context.getString(com.kazumaproject.core.R.string.string_key10_number))
        R.id.key_12 -> context.getString(com.kazumaproject.core.R.string.string_key12_number)
        else -> ""
    }
}

fun AppCompatButton.setTenKeyTextWhenTapJapanese(
    keyId: Int
) {
    when (keyId) {
        R.id.key_1 -> this.text = JP_KEY_LAYOUT_WITH_SPACE[0]
        R.id.key_2 -> this.text = JP_KEY_LAYOUT_WITH_SPACE[1]
        R.id.key_3 -> this.text = JP_KEY_LAYOUT_WITH_SPACE[2]
        R.id.key_4 -> this.text = JP_KEY_LAYOUT_WITH_SPACE[3]
        R.id.key_5 -> this.text = JP_KEY_LAYOUT_WITH_SPACE[4]
        R.id.key_6 -> this.text = JP_KEY_LAYOUT_WITH_SPACE[5]
        R.id.key_7 -> this.text = JP_KEY_LAYOUT_WITH_SPACE[6]
        R.id.key_8 -> this.text = JP_KEY_LAYOUT_WITH_SPACE[7]
        R.id.key_9 -> this.text = JP_KEY_LAYOUT_WITH_SPACE[8]
        R.id.key_11 -> this.text = JP_KEY_LAYOUT_WITH_SPACE[9]
        R.id.key_12 -> this.text = JP_KEY_LAYOUT_WITH_SPACE[10]
    }
}

fun AppCompatButton.setTenKeyTextWhenTapEnglish(
    keyId: Int
) {
    when (keyId) {
        R.id.key_1 -> this.text = "@"
        R.id.key_2 -> this.text = "a"
        R.id.key_3 -> this.text = "d"
        R.id.key_4 -> this.text = "g"
        R.id.key_5 -> this.text = "j"
        R.id.key_6 -> this.text = "m"
        R.id.key_7 -> this.text = "p"
        R.id.key_8 -> this.text = "t"
        R.id.key_9 -> this.text = "w"
        R.id.key_11 -> this.text = "\'"
        R.id.key_12 -> this.text = "."
    }
}

fun AppCompatButton.setTenKeyTextWhenTapNumber(
    keyId: Int
) {
    when (keyId) {
        R.id.key_1 -> this.text = "1"
        R.id.key_2 -> this.text = "2"
        R.id.key_3 -> this.text = "3"
        R.id.key_4 -> this.text = "4"
        R.id.key_5 -> this.text = "5"
        R.id.key_6 -> this.text = "6"
        R.id.key_7 -> this.text = "7"
        R.id.key_8 -> this.text = "8"
        R.id.key_9 -> this.text = "9"
        R.id.key_11 -> this.text = "0"
        R.id.key_12 -> this.text = "."
    }
}
