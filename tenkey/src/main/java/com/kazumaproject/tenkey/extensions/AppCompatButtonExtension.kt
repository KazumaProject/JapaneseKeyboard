package com.kazumaproject.tenkey.extensions

import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import com.kazumaproject.core.ui.appcompatbutton.CustomLineHeightSpan
import com.kazumaproject.tenkey.R

var KEY_JAPANESE_SIZE = 17f
var KEY_ENGLISH_SIZE = 14f
var KEY_NUMBER_SIZE = 18f

private val JP_KEY_LAYOUT_WITH_SPACE = listOf(
    "    あ    ", "    か    ", "    さ    ", "    た    ", "    な    ",
    "    は    ", "    ま    ", "    や    ", "    ら    ", "    わ    ",
    "    、    "
)

fun AppCompatButton.setTenKeyTextJapanese(
    keyId: Int
) {
    textSize = KEY_JAPANESE_SIZE
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
        R.id.key_2 -> this.text = "A"
        R.id.key_3 -> this.text = "D"
        R.id.key_4 -> this.text = "G"
        R.id.key_5 -> this.text = "J"
        R.id.key_6 -> this.text = "M"
        R.id.key_7 -> this.text = "P"
        R.id.key_8 -> this.text = "T"
        R.id.key_9 -> this.text = "W"
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

// ?\n｡  ,  !\n…
private fun getSpannableStringForKigouButtonJapanese(): SpannableString {
    val spannable = SpannableString("？\n。 , !\n…")
    spannable.setSpan(RelativeSizeSpan(0.6f), 0, 1, 0)
    spannable.setSpan(RelativeSizeSpan(0.6f), 2, 3, 0)
    spannable.setSpan(RelativeSizeSpan(0.6f), 4, 5, 0)
    spannable.setSpan(RelativeSizeSpan(0.6f), 6, 7, 0)
    spannable.setSpan(RelativeSizeSpan(0.8f), 8, 9, 0)
    spannable.setSpan(RelativeSizeSpan(0.5f), 1, 2, 0)
    spannable.setSpan(CustomLineHeightSpan(15, 0), 7, 8, 0)
    return spannable
}

private fun getSpannableStringForNumberButton(str: String): SpannableString {
    val spannable = SpannableString(str)
    spannable.setSpan(RelativeSizeSpan(1f), 0, 1, 0)
    spannable.setSpan(RelativeSizeSpan(0.5f), 1, str.length, 0)
    return spannable
}