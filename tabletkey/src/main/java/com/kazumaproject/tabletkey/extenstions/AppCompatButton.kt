package com.kazumaproject.tabletkey.extenstions

import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import com.kazumaproject.core.domain.extensions.KEY_TABLET_SIZE
import com.kazumaproject.tabletkey.R

fun AppCompatButton.setTabletKeyTextJapanese(keyId: Int) {
    textSize = KEY_TABLET_SIZE
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

fun AppCompatButton.setTabletKeyTextEnglish(keyId: Int) {
    textSize = KEY_TABLET_SIZE
    setTextColor(
        ContextCompat.getColor(
            context,
            com.kazumaproject.core.R.color.keyboard_icon_color
        )
    )
    text = when (keyId) {
        R.id.key_1 -> context.getString(com.kazumaproject.core.R.string.a)
        R.id.key_2 -> context.getString(com.kazumaproject.core.R.string.k)
        R.id.key_3 -> context.getString(com.kazumaproject.core.R.string.u)
        R.id.key_4 -> context.getString(com.kazumaproject.core.R.string.horizontalbar)
        R.id.key_5 -> context.getString(com.kazumaproject.core.R.string.shift_symbol)
        R.id.key_11 -> context.getString(com.kazumaproject.core.R.string.b)
        R.id.key_12 -> context.getString(com.kazumaproject.core.R.string.l)
        R.id.key_13 -> context.getString(com.kazumaproject.core.R.string.v)
        R.id.key_14 -> context.getString(com.kazumaproject.core.R.string.under_bar)
        R.id.key_16 -> context.getString(com.kazumaproject.core.R.string.c)
        R.id.key_17 -> context.getString(com.kazumaproject.core.R.string.m)
        R.id.key_18 -> context.getString(com.kazumaproject.core.R.string.w)
        R.id.key_19 -> context.getString(com.kazumaproject.core.R.string.slash)
        R.id.key_20 -> context.getString(com.kazumaproject.core.R.string.undo_symbol)
        R.id.key_21 -> context.getString(com.kazumaproject.core.R.string.d)
        R.id.key_22 -> context.getString(com.kazumaproject.core.R.string.n)
        R.id.key_23 -> context.getString(com.kazumaproject.core.R.string.x)
        R.id.key_24 -> context.getString(com.kazumaproject.core.R.string.colon)
        R.id.key_26 -> context.getString(com.kazumaproject.core.R.string.e)
        R.id.key_27 -> context.getString(com.kazumaproject.core.R.string.o)
        R.id.key_28 -> context.getString(com.kazumaproject.core.R.string.y)
        R.id.key_29 -> context.getString(com.kazumaproject.core.R.string.ampersand_symbol)
        R.id.key_30 -> context.getString(com.kazumaproject.core.R.string.comma)
        R.id.key_31 -> context.getString(com.kazumaproject.core.R.string.f)
        R.id.key_32 -> context.getString(com.kazumaproject.core.R.string.p)
        R.id.key_33 -> context.getString(com.kazumaproject.core.R.string.z)
        R.id.key_34 -> context.getString(com.kazumaproject.core.R.string.at_mark)
        R.id.key_35 -> context.getString(com.kazumaproject.core.R.string.period)
        R.id.key_36 -> context.getString(com.kazumaproject.core.R.string.g)
        R.id.key_37 -> context.getString(com.kazumaproject.core.R.string.q)
        R.id.key_38 -> context.getString(com.kazumaproject.core.R.string.bracket_left)
        R.id.key_39 -> context.getString(com.kazumaproject.core.R.string.sharp_symbol)
        R.id.key_40 -> context.getString(com.kazumaproject.core.R.string.mark)
        R.id.key_41 -> context.getString(com.kazumaproject.core.R.string.h)
        R.id.key_42 -> context.getString(com.kazumaproject.core.R.string.r)
        R.id.key_43 -> context.getString(com.kazumaproject.core.R.string.bracket_right)
        R.id.key_44 -> context.getString(com.kazumaproject.core.R.string.asterisk_symbol)
        R.id.key_45 -> context.getString(com.kazumaproject.core.R.string.question)
        R.id.key_46 -> context.getString(com.kazumaproject.core.R.string.i)
        R.id.key_47 -> context.getString(com.kazumaproject.core.R.string.s)
        R.id.key_48 -> context.getString(com.kazumaproject.core.R.string.square_bracket_left)
        R.id.key_49 -> context.getString(com.kazumaproject.core.R.string.caret)
        R.id.key_51 -> context.getString(com.kazumaproject.core.R.string.j)
        R.id.key_52 -> context.getString(com.kazumaproject.core.R.string.t)
        R.id.key_53 -> context.getString(com.kazumaproject.core.R.string.square_bracket_right)
        R.id.key_54 -> context.getString(com.kazumaproject.core.R.string.apostrophe)
        R.id.key_55 -> context.getString(com.kazumaproject.core.R.string.zenkaku)
        else -> ""
    }
}

fun AppCompatButton.setTabletKeyTextNumber(keyId: Int) {
    textSize = KEY_TABLET_SIZE
    setTextColor(
        ContextCompat.getColor(
            context,
            com.kazumaproject.core.R.color.keyboard_icon_color
        )
    )
    text = when (keyId) {
        R.id.key_1 -> context.getString(com.kazumaproject.core.R.string.tablet_number_year)
        R.id.key_2 -> context.getString(com.kazumaproject.core.R.string.tablet_number_multiplication)
        R.id.key_3 -> context.getString(com.kazumaproject.core.R.string.tablet_number_music_note)
        R.id.key_4 -> context.getString(com.kazumaproject.core.R.string.tablet_number_arrow_right)
        R.id.key_5 -> context.getString(com.kazumaproject.core.R.string.tablet_number_command)
        R.id.key_11 -> context.getString(com.kazumaproject.core.R.string.tablet_number_month)
        R.id.key_12 -> context.getString(com.kazumaproject.core.R.string.tablet_number_divide)
        R.id.key_13 -> context.getString(com.kazumaproject.core.R.string.tablet_number_star)
        R.id.key_14 -> context.getString(com.kazumaproject.core.R.string.tablet_number_tilde)
        R.id.key_26 -> context.getString(com.kazumaproject.core.R.string.tablet_number_day)
        R.id.key_27 -> context.getString(com.kazumaproject.core.R.string.tablet_number_plus)
        R.id.key_28 -> context.getString(com.kazumaproject.core.R.string.tablet_number_percent)
        R.id.key_29 -> context.getString(com.kazumaproject.core.R.string.tablet_number_middle_dot)
        R.id.key_30 -> context.getString(com.kazumaproject.core.R.string.slash)
        R.id.key_31 -> context.getString(com.kazumaproject.core.R.string.tablet_number_hour)
        R.id.key_32 -> context.getString(com.kazumaproject.core.R.string.string_minus)
        R.id.key_33 -> context.getString(com.kazumaproject.core.R.string.tablet_number_yen)
        R.id.key_34 -> context.getString(com.kazumaproject.core.R.string.tablet_number_ellipsis)
        R.id.key_35 -> context.getString(com.kazumaproject.core.R.string.bracket_left)
        R.id.key_36 -> context.getString(com.kazumaproject.core.R.string.tablet_number_minute)
        R.id.key_37 -> context.getString(com.kazumaproject.core.R.string.tablet_number_equal)
        R.id.key_38 -> context.getString(com.kazumaproject.core.R.string.tablet_number_post_mark)
        R.id.key_39 -> context.getString(com.kazumaproject.core.R.string.tablet_number_circle)
        R.id.key_40 -> context.getString(com.kazumaproject.core.R.string.bracket_right)
        R.id.key_41 -> context.getString(com.kazumaproject.core.R.string._1)
        R.id.key_42 -> context.getString(com.kazumaproject.core.R.string._4)
        R.id.key_43 -> context.getString(com.kazumaproject.core.R.string._7)
        R.id.key_44 -> context.getString(com.kazumaproject.core.R.string.comma)
        R.id.key_45 -> context.getString(com.kazumaproject.core.R.string.colon)
        R.id.key_46 -> context.getString(com.kazumaproject.core.R.string._2)
        R.id.key_47 -> context.getString(com.kazumaproject.core.R.string._5)
        R.id.key_48 -> context.getString(com.kazumaproject.core.R.string._8)
        R.id.key_49 -> context.getString(com.kazumaproject.core.R.string._0)
        R.id.key_51 -> context.getString(com.kazumaproject.core.R.string._3)
        R.id.key_52 -> context.getString(com.kazumaproject.core.R.string._6)
        R.id.key_53 -> context.getString(com.kazumaproject.core.R.string._9)
        R.id.key_54 -> context.getString(com.kazumaproject.core.R.string.period)
        R.id.key_55 -> context.getString(com.kazumaproject.core.R.string.zenkaku)
        else -> ""
    }
}