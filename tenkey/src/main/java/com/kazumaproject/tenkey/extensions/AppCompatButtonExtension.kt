package com.kazumaproject.tenkey.extensions

import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import com.kazumaproject.core.domain.extensions.JP_KEY_LAYOUT_WITH_SPACE
import com.kazumaproject.core.domain.extensions.KEY_ENGLISH_SIZE
import com.kazumaproject.core.domain.extensions.KEY_JAPANESE_SIZE
import com.kazumaproject.core.domain.extensions.KEY_NUMBER_SIZE
import com.kazumaproject.core.domain.extensions.getSpannableStringForKigouButtonJapanese
import com.kazumaproject.core.domain.extensions.getSpannableStringForNumberButton
import com.kazumaproject.tenkey.R

private data class FlickChars(
    val center: String,
    val left: String?,
    val top: String?,
    val right: String?,
    val bottom: String?
)

/**
 * 真ん中＋上下左右の文字を 3 行の十字レイアウトで表示する Spannable を作る
 *
 *  例:
 *    "  う  \n" +
 *    "い あ え\n" +
 *    "  お  "
 *
 * center: 中央に表示する文字（通常タップ）
 * left/top/right/bottom: フリック方向に対応する文字（null または "" なら空白）
 */
private fun createFlickSpannable(
    center: String,
    left: String? = null,
    top: String? = null,
    right: String? = null,
    bottom: String? = null,
    sideScale: Float = 0.8f  // 周囲の文字を少し小さくする倍率
): SpannableString {

    val sb = StringBuilder()

    var topStart = -1
    var leftStart = -1
    var centerStart = -1
    var rightStart = -1
    var bottomStart = -1

    // 1行目: 上
    if (!top.isNullOrEmpty()) {
        sb.append("  ")              // 少し中央寄せ
        topStart = sb.length
        sb.append(top)
        sb.append("  ")
    } else {
        sb.append("     ")           // "  " + 中央 + "  " と同じ幅をスペースで
    }
    sb.append("\n")

    // 2行目: 左 真ん中 右
    if (!left.isNullOrEmpty()) {
        leftStart = sb.length
        sb.append(left)
    } else {
        sb.append(" ")
    }
    sb.append(" ")

    centerStart = sb.length
    sb.append(center)
    sb.append(" ")

    if (!right.isNullOrEmpty()) {
        rightStart = sb.length
        sb.append(right)
    } else {
        sb.append(" ")
    }
    sb.append("\n")

    // 3行目: 下
    if (!bottom.isNullOrEmpty()) {
        sb.append("  ")
        bottomStart = sb.length
        sb.append(bottom)
        sb.append("  ")
    } else {
        sb.append("     ")
    }

    val spannable = SpannableString(sb.toString())

    // 周囲は小さめ
    fun applySideSpan(start: Int, len: Int) {
        if (start >= 0 && len > 0) {
            spannable.setSpan(
                RelativeSizeSpan(sideScale),
                start,
                start + len,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    // 中央は基準サイズ（button.textSize）をそのまま使うので span は不要でも OK

    if (!left.isNullOrEmpty()) applySideSpan(leftStart, left!!.length)
    if (!top.isNullOrEmpty()) applySideSpan(topStart, top!!.length)
    if (!right.isNullOrEmpty()) applySideSpan(rightStart, right!!.length)
    if (!bottom.isNullOrEmpty()) applySideSpan(bottomStart, bottom!!.length)

    return spannable
}


fun AppCompatButton.setTenKeyTextJapanese(
    keyId: Int,
    delta: Int,
    modeTheme: String,
    colorTextInt: Int
) {
    textSize = KEY_JAPANESE_SIZE + delta
    when (modeTheme) {
        "default" -> {
            setTextColor(
                ContextCompat.getColor(
                    context,
                    com.kazumaproject.core.R.color.keyboard_icon_color
                )
            )
        }

        "custom" -> {
            setTextColor(colorTextInt)
        }

        else -> {
            setTextColor(
                ContextCompat.getColor(
                    context,
                    com.kazumaproject.core.R.color.keyboard_icon_color
                )
            )
        }
    }
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

fun AppCompatButton.setTenKeyTextJapaneseWithFlickGuide(
    keyId: Int,
    delta: Int,
    modeTheme: String,
    colorTextInt: Int
) {
    // ベースのテキストサイズ (中心) をセット
    textSize = 11f + delta
    when (modeTheme) {
        "default" -> {
            setTextColor(
                ContextCompat.getColor(
                    context,
                    com.kazumaproject.core.R.color.keyboard_icon_color
                )
            )
        }

        "custom" ->{
            setTextColor(colorTextInt)
        }

        else -> {
            setTextColor(
                ContextCompat.getColor(
                    context,
                    com.kazumaproject.core.R.color.keyboard_icon_color
                )
            )
        }
    }

    // 3行表示＋中央寄せ
    this.isSingleLine = false
    this.maxLines = 3
    this.gravity = Gravity.CENTER
    // 行間を少し詰めたいなら（好みで調整）
    this.setLineSpacing(0f, 0.9f)

    val chars: FlickChars? = when (keyId) {
        R.id.key_1 -> FlickChars(
            center = "あ",
            left = "い",
            top = "う",
            right = "え",
            bottom = "お"
        )

        R.id.key_2 -> FlickChars(
            center = "か",
            left = "き",
            top = "く",
            right = "け",
            bottom = "こ"
        )

        R.id.key_3 -> FlickChars(
            center = "さ",
            left = "し",
            top = "す",
            right = "せ",
            bottom = "そ"
        )

        R.id.key_4 -> FlickChars(
            center = "た",
            left = "ち",
            top = "つ",
            right = "て",
            bottom = "と"
        )

        R.id.key_5 -> FlickChars(
            center = "な",
            left = "に",
            top = "ぬ",
            right = "ね",
            bottom = "の"
        )

        R.id.key_6 -> FlickChars(
            center = "は",
            left = "ひ",
            top = "ふ",
            right = "へ",
            bottom = "ほ"
        )

        R.id.key_7 -> FlickChars(
            center = "ま",
            left = "み",
            top = "む",
            right = "め",
            bottom = "も"
        )

        R.id.key_8 -> FlickChars(
            center = "や",
            left = "(",   // や行は一般的に左/右は無しにすることが多い
            top = "ゆ",
            right = ")",
            bottom = "よ"
        )

        R.id.key_9 -> FlickChars(
            center = "ら",
            left = "り",
            top = "る",
            right = "れ",
            bottom = "ろ"
        )

        R.id.key_11 -> FlickChars(
            center = "わ",
            left = "を",
            top = "ん",
            right = "ー", // 好みで変更
            bottom = "〜"
        )
        // 記号キー(key_12)は従来どおり getSpannableStringForKigouButtonJapanese を使う
        R.id.key_12 -> null
        else -> null
    }

    text = when {
        chars != null -> {
            createFlickSpannable(
                center = chars.center,
                left = chars.left,
                top = chars.top,
                right = chars.right,
                bottom = chars.bottom,
                sideScale = 0.6f
            )
        }

        keyId == R.id.key_12 -> {
            // 記号キーは今までの実装に合わせる
            getSpannableStringForKigouButtonJapanese()
        }

        else -> ""
    }
}

fun AppCompatButton.setTenKeyTextEnglish(
    keyId: Int,
    delta: Int
) {
    textSize = KEY_ENGLISH_SIZE + delta
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
    keyId: Int,
    delta: Int
) {
    textSize = KEY_NUMBER_SIZE + delta
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
