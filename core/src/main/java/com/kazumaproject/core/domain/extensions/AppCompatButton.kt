package com.kazumaproject.core.domain.extensions


import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import com.kazumaproject.core.ui.appcompatbutton.CustomLineHeightSpan

const val KEY_JAPANESE_SIZE = 17f
const val KEY_ENGLISH_SIZE = 14f
const val KEY_NUMBER_SIZE = 18f

const val KEY_TABLET_SIZE = 18f

val JP_KEY_LAYOUT_WITH_SPACE = listOf(
    "    あ    ", "    か    ", "    さ    ", "    た    ", "    な    ",
    "    は    ", "    ま    ", "    や    ", "    ら    ", "    わ    ",
    "    、    "
)

// ?\n｡  ,  !\n…
fun getSpannableStringForKigouButtonJapanese(): SpannableString {
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

fun getSpannableStringForNumberButton(str: String): SpannableString {
    val spannable = SpannableString(str)
    spannable.setSpan(RelativeSizeSpan(1f), 0, 1, 0)
    spannable.setSpan(RelativeSizeSpan(0.5f), 1, str.length, 0)
    return spannable
}

fun AppCompatButton.layoutXPosition(): Int {
    val location = IntArray(2)
    this.getLocationOnScreen(location)
    return location[0]
}

fun AppCompatButton.layoutYPosition(): Int {
    val location = IntArray(2)
    this.getLocationOnScreen(location)
    return location[1]
}

fun AppCompatImageButton.layoutXPosition(): Int {
    val location = IntArray(2)
    this.getLocationOnScreen(location)
    return location[0]
}

fun AppCompatImageButton.layoutYPosition(): Int {
    val location = IntArray(2)
    this.getLocationOnScreen(location)
    return location[1]
}

fun AppCompatButton.setLargeUnicodeIcon(icon: String, iconSizeSp: Int = 18) {
    val spannable = SpannableString(icon)

    spannable.setSpan(
        AbsoluteSizeSpan(iconSizeSp, true),
        0,
        icon.length,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
    )

    this.text = spannable
    this.gravity = Gravity.CENTER
}

fun AppCompatButton.setLargeUnicodeIconScaleX(
    icon: String,
    iconSizeSp: Int = 18,
    scaleX: Float = 1f,
) {
    val spannable = SpannableString(icon)
    spannable.setSpan(
        AbsoluteSizeSpan(iconSizeSp, true),
        0,
        icon.length,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
    )

    this.text = spannable
    this.gravity = Gravity.CENTER
    this.textScaleX = scaleX
}
