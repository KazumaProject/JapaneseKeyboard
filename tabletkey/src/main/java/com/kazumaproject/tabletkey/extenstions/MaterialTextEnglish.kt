package com.kazumaproject.tabletkey.extenstions

import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.tabletkey.R

// Flick character mapping for each key. List order: [Default, Shift ON, Zenkaku Default, Zenkaku Shift ON,]
private val TABLET_KEYS_A = listOf('a', 'A', 'ａ', 'Ａ')
private val TABLET_KEYS_B = listOf('b', 'B', 'ｂ', 'Ｂ')
private val TABLET_KEYS_C = listOf('c', 'C', 'ｃ', 'Ｃ')
private val TABLET_KEYS_D = listOf('d', 'D', 'ｄ', 'Ｄ')
private val TABLET_KEYS_E = listOf('e', 'E', 'ｅ', 'Ｅ')
private val TABLET_KEYS_F = listOf('f', 'F', 'ｆ', 'Ｆ')
private val TABLET_KEYS_G = listOf('g', 'G', 'ｇ', 'Ｇ')
private val TABLET_KEYS_H = listOf('h', 'H', 'ｈ', 'Ｈ')
private val TABLET_KEYS_I = listOf('i', 'I', 'ｉ', 'Ｉ')
private val TABLET_KEYS_J = listOf('j', 'J', 'ｊ', 'Ｊ')
private val TABLET_KEYS_K = listOf('k', 'K', 'ｋ', 'Ｋ')
private val TABLET_KEYS_L = listOf('l', 'L', 'ｌ', 'Ｌ')
private val TABLET_KEYS_M = listOf('m', 'M', 'ｍ', 'Ｍ')
private val TABLET_KEYS_N = listOf('n', 'N', 'ｎ', 'Ｎ')
private val TABLET_KEYS_O = listOf('o', 'O', 'ｏ', 'Ｏ')
private val TABLET_KEYS_P = listOf('p', 'P', 'ｐ', 'Ｐ')
private val TABLET_KEYS_Q = listOf('q', 'Q', 'ｑ', 'Ｑ')
private val TABLET_KEYS_R = listOf('r', 'R', 'ｒ', 'Ｒ')
private val TABLET_KEYS_S = listOf('s', 'S', 'ｓ', 'Ｓ')
private val TABLET_KEYS_T = listOf('t', 'T', 'ｔ', 'Ｔ')
private val TABLET_KEYS_U = listOf('u', 'U', 'ｕ', 'Ｕ')
private val TABLET_KEYS_V = listOf('v', 'V', 'ｖ', 'Ｖ')
private val TABLET_KEYS_W = listOf('w', 'W', 'ｗ', 'Ｗ')
private val TABLET_KEYS_X = listOf('x', 'X', 'ｘ', 'Ｘ')
private val TABLET_KEYS_Y = listOf('y', 'Y', 'ｙ', 'Ｙ')
private val TABLET_KEYS_Z = listOf('z', 'Z', 'ｚ', 'Ｚ')

private val TABLET_KEYS_LEFT_BRACKET = listOf('(', '<', '（', '〈')
private val TABLET_KEYS_RIGHT_BRACKET = listOf(')', '>', '）', '〉')
private val TABLET_KEYS_SQUARE_LEFT_BRACKET = listOf('[', '{', '［', '｛')
private val TABLET_KEYS_SQUARE_RIGHT_BRACKET = listOf(']', '}', '］', '｝')

private val TABLET_KEYS_MINUS = listOf('-', '+', '－', '＋')
private val TABLET_KEYS_UNDER_BAR = listOf('_', '~', '＿', '〜')
private val TABLET_KEYS_SLASH = listOf('/', '\\', '／', '＼')
private val TABLET_KEYS_COLON = listOf(':', ';', '：', '；')
private val TABLET_KEYS_AND = listOf('&', '%', '＆', '％')
private val TABLET_KEYS_AT_MARK = listOf('@', '|', '＠', '｜')
private val TABLET_KEYS_SHARP = listOf('#', '=', '＃', '＝')
private val TABLET_KEYS_ASTERISK = listOf('*', '$', '＊', '＄')
private val TABLET_KEYS_CARET = listOf('^', '\'', '＾', '＇')
private val TABLET_KEYS_BACK_QUOTE = listOf('`', '"', '｀', '＂')
private val TABLET_KEYS_COMMA = listOf(',', '、', '，', null)
private val TABLET_KEYS_PERIOD = listOf('.', '。', '．', null)
private val TABLET_KEYS_CAUTION = listOf('!', null, '！', null)
private val TABLET_KEYS_QUESTION = listOf('?', null, '？', null)

fun MaterialTextView.setTabletTextEnglish(keyId: Int, index: Int) {
    val char = when (keyId) {

        R.id.key_51 -> TABLET_KEYS_J.getOrNull(index)
        R.id.key_52 -> TABLET_KEYS_T.getOrNull(index)
        R.id.key_53 -> TABLET_KEYS_SQUARE_RIGHT_BRACKET.getOrNull(index)
        R.id.key_54 -> TABLET_KEYS_BACK_QUOTE.getOrNull(index)
        R.id.key_55 -> null

        R.id.key_46 -> TABLET_KEYS_I.getOrNull(index)
        R.id.key_47 -> TABLET_KEYS_S.getOrNull(index)
        R.id.key_48 -> TABLET_KEYS_SQUARE_LEFT_BRACKET.getOrNull(index)
        R.id.key_49 -> TABLET_KEYS_CARET.getOrNull(index)
        R.id.key_50 -> null

        R.id.key_41 -> TABLET_KEYS_H.getOrNull(index)
        R.id.key_42 -> TABLET_KEYS_R.getOrNull(index)
        R.id.key_43 -> TABLET_KEYS_RIGHT_BRACKET.getOrNull(index)
        R.id.key_44 -> TABLET_KEYS_ASTERISK.getOrNull(index)
        R.id.key_45 -> TABLET_KEYS_QUESTION.getOrNull(index)

        R.id.key_36 -> TABLET_KEYS_G.getOrNull(index)
        R.id.key_37 -> TABLET_KEYS_Q.getOrNull(index)
        R.id.key_38 -> TABLET_KEYS_LEFT_BRACKET.getOrNull(index)
        R.id.key_39 -> TABLET_KEYS_SHARP.getOrNull(index)
        R.id.key_40 -> TABLET_KEYS_CAUTION.getOrNull(index)

        R.id.key_31 -> TABLET_KEYS_F.getOrNull(index)
        R.id.key_32 -> TABLET_KEYS_P.getOrNull(index)
        R.id.key_33 -> TABLET_KEYS_Z.getOrNull(index)
        R.id.key_34 -> TABLET_KEYS_AT_MARK.getOrNull(index)
        R.id.key_35 -> TABLET_KEYS_PERIOD.getOrNull(index)

        R.id.key_26 -> TABLET_KEYS_E.getOrNull(index)
        R.id.key_27 -> TABLET_KEYS_O.getOrNull(index)
        R.id.key_28 -> TABLET_KEYS_Y.getOrNull(index)
        R.id.key_29 -> TABLET_KEYS_AND.getOrNull(index)
        R.id.key_30 -> TABLET_KEYS_COMMA.getOrNull(index)

        R.id.key_21 -> TABLET_KEYS_D.getOrNull(index)
        R.id.key_22 -> TABLET_KEYS_N.getOrNull(index)
        R.id.key_23 -> TABLET_KEYS_X.getOrNull(index)
        R.id.key_24 -> TABLET_KEYS_COLON.getOrNull(index)

        R.id.key_16 -> TABLET_KEYS_C.getOrNull(index)
        R.id.key_17 -> TABLET_KEYS_M.getOrNull(index)
        R.id.key_18 -> TABLET_KEYS_W.getOrNull(index)
        R.id.key_19 -> TABLET_KEYS_SLASH.getOrNull(index)
        R.id.key_20 -> null

        R.id.key_11 -> TABLET_KEYS_B.getOrNull(index)
        R.id.key_12 -> TABLET_KEYS_L.getOrNull(index)
        R.id.key_13 -> TABLET_KEYS_V.getOrNull(index)
        R.id.key_14 -> TABLET_KEYS_UNDER_BAR.getOrNull(index)

        R.id.key_1 -> TABLET_KEYS_A.getOrNull(index)
        R.id.key_2 -> TABLET_KEYS_K.getOrNull(index)
        R.id.key_3 -> TABLET_KEYS_U.getOrNull(index)
        R.id.key_4 -> TABLET_KEYS_MINUS.getOrNull(index)
        R.id.key_5 -> null
        else -> null
    }
    text = char?.toString() ?: ""
}

fun MaterialTextView.setTabletTextDefaultEnglish(keyId: Int) =
    setTabletTextEnglish(keyId, 0)

fun MaterialTextView.setTabletTextShiftOnEnglish(keyId: Int) =
    setTabletTextEnglish(keyId, 1)

fun MaterialTextView.setTabletTextZenkakuDefaultEnglish(keyId: Int) =
    setTabletTextEnglish(keyId, 2)

fun MaterialTextView.setTabletTextZenkakuShiftOnEnglish(keyId: Int) =
    setTabletTextEnglish(keyId, 3)