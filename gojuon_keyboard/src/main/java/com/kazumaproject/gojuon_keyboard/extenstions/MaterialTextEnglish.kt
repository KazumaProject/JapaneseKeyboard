package com.kazumaproject.gojuon_keyboard.extenstions

import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.gojuon_keyboard.R

// Flick character mapping for each key. List order: [Default, Shift ON, Zenkaku Default, Zenkaku Shift ON,]
private val GOJUON_KEYS_A = listOf('a', 'A', 'ａ', 'Ａ')
private val GOJUON_KEYS_B = listOf('b', 'B', 'ｂ', 'Ｂ')
private val GOJUON_KEYS_C = listOf('c', 'C', 'ｃ', 'Ｃ')
private val GOJUON_KEYS_D = listOf('d', 'D', 'ｄ', 'Ｄ')
private val GOJUON_KEYS_E = listOf('e', 'E', 'ｅ', 'Ｅ')
private val GOJUON_KEYS_F = listOf('f', 'F', 'ｆ', 'Ｆ')
private val GOJUON_KEYS_G = listOf('g', 'G', 'ｇ', 'Ｇ')
private val GOJUON_KEYS_H = listOf('h', 'H', 'ｈ', 'Ｈ')
private val GOJUON_KEYS_I = listOf('i', 'I', 'ｉ', 'Ｉ')
private val GOJUON_KEYS_J = listOf('j', 'J', 'ｊ', 'Ｊ')
private val GOJUON_KEYS_K = listOf('k', 'K', 'ｋ', 'Ｋ')
private val GOJUON_KEYS_L = listOf('l', 'L', 'ｌ', 'Ｌ')
private val GOJUON_KEYS_M = listOf('m', 'M', 'ｍ', 'Ｍ')
private val GOJUON_KEYS_N = listOf('n', 'N', 'ｎ', 'Ｎ')
private val GOJUON_KEYS_O = listOf('o', 'O', 'ｏ', 'Ｏ')
private val GOJUON_KEYS_P = listOf('p', 'P', 'ｐ', 'Ｐ')
private val GOJUON_KEYS_Q = listOf('q', 'Q', 'ｑ', 'Ｑ')
private val GOJUON_KEYS_R = listOf('r', 'R', 'ｒ', 'Ｒ')
private val GOJUON_KEYS_S = listOf('s', 'S', 'ｓ', 'Ｓ')
private val GOJUON_KEYS_T = listOf('t', 'T', 'ｔ', 'Ｔ')
private val GOJUON_KEYS_U = listOf('u', 'U', 'ｕ', 'Ｕ')
private val GOJUON_KEYS_V = listOf('v', 'V', 'ｖ', 'Ｖ')
private val GOJUON_KEYS_W = listOf('w', 'W', 'ｗ', 'Ｗ')
private val GOJUON_KEYS_X = listOf('x', 'X', 'ｘ', 'Ｘ')
private val GOJUON_KEYS_Y = listOf('y', 'Y', 'ｙ', 'Ｙ')
private val GOJUON_KEYS_Z = listOf('z', 'Z', 'ｚ', 'Ｚ')

private val GOJUON_KEYS_LEFT_BRACKET = listOf('(', '<', '（', '〈')
private val GOJUON_KEYS_RIGHT_BRACKET = listOf(')', '>', '）', '〉')
private val GOJUON_KEYS_SQUARE_LEFT_BRACKET = listOf('[', '{', '［', '｛')
private val GOJUON_KEYS_SQUARE_RIGHT_BRACKET = listOf(']', '}', '］', '｝')

private val GOJUON_KEYS_MINUS = listOf('-', '+', '－', '＋')
private val GOJUON_KEYS_UNDER_BAR = listOf('_', '~', '＿', '〜')
private val GOJUON_KEYS_SLASH = listOf('/', '\\', '／', '＼')
private val GOJUON_KEYS_COLON = listOf(':', ';', '：', '；')
private val GOJUON_KEYS_AND = listOf('&', '%', '＆', '％')
private val GOJUON_KEYS_AT_MARK = listOf('@', '|', '＠', '｜')
private val GOJUON_KEYS_SHARP = listOf('#', '=', '＃', '＝')
private val GOJUON_KEYS_ASTERISK = listOf('*', '$', '＊', '＄')
private val GOJUON_KEYS_CARET = listOf('^', '\'', '＾', '＇')
private val GOJUON_KEYS_BACK_QUOTE = listOf('`', '"', '｀', '＂')
private val GOJUON_KEYS_COMMA = listOf(',', '、', '，', null)
private val GOJUON_KEYS_PERIOD = listOf('.', '。', '．', null)
private val GOJUON_KEYS_CAUTION = listOf('!', null, '！', null)
private val GOJUON_KEYS_QUESTION = listOf('?', null, '？', null)

fun MaterialTextView.setGojuonTextEnglish(keyId: Int, index: Int) {
    val char = when (keyId) {

        R.id.key_51 -> GOJUON_KEYS_J.getOrNull(index)
        R.id.key_52 -> GOJUON_KEYS_T.getOrNull(index)
        R.id.key_53 -> GOJUON_KEYS_SQUARE_RIGHT_BRACKET.getOrNull(index)
        R.id.key_54 -> GOJUON_KEYS_BACK_QUOTE.getOrNull(index)
        R.id.key_55 -> null

        R.id.key_46 -> GOJUON_KEYS_I.getOrNull(index)
        R.id.key_47 -> GOJUON_KEYS_S.getOrNull(index)
        R.id.key_48 -> GOJUON_KEYS_SQUARE_LEFT_BRACKET.getOrNull(index)
        R.id.key_49 -> GOJUON_KEYS_CARET.getOrNull(index)
        R.id.key_50 -> null

        R.id.key_41 -> GOJUON_KEYS_H.getOrNull(index)
        R.id.key_42 -> GOJUON_KEYS_R.getOrNull(index)
        R.id.key_43 -> GOJUON_KEYS_RIGHT_BRACKET.getOrNull(index)
        R.id.key_44 -> GOJUON_KEYS_ASTERISK.getOrNull(index)
        R.id.key_45 -> GOJUON_KEYS_QUESTION.getOrNull(index)

        R.id.key_36 -> GOJUON_KEYS_G.getOrNull(index)
        R.id.key_37 -> GOJUON_KEYS_Q.getOrNull(index)
        R.id.key_38 -> GOJUON_KEYS_LEFT_BRACKET.getOrNull(index)
        R.id.key_39 -> GOJUON_KEYS_SHARP.getOrNull(index)
        R.id.key_40 -> GOJUON_KEYS_CAUTION.getOrNull(index)

        R.id.key_31 -> GOJUON_KEYS_F.getOrNull(index)
        R.id.key_32 -> GOJUON_KEYS_P.getOrNull(index)
        R.id.key_33 -> GOJUON_KEYS_Z.getOrNull(index)
        R.id.key_34 -> GOJUON_KEYS_AT_MARK.getOrNull(index)
        R.id.key_35 -> GOJUON_KEYS_PERIOD.getOrNull(index)

        R.id.key_26 -> GOJUON_KEYS_E.getOrNull(index)
        R.id.key_27 -> GOJUON_KEYS_O.getOrNull(index)
        R.id.key_28 -> GOJUON_KEYS_Y.getOrNull(index)
        R.id.key_29 -> GOJUON_KEYS_AND.getOrNull(index)
        R.id.key_30 -> GOJUON_KEYS_COMMA.getOrNull(index)

        R.id.key_21 -> GOJUON_KEYS_D.getOrNull(index)
        R.id.key_22 -> GOJUON_KEYS_N.getOrNull(index)
        R.id.key_23 -> GOJUON_KEYS_X.getOrNull(index)
        R.id.key_24 -> GOJUON_KEYS_COLON.getOrNull(index)

        R.id.key_16 -> GOJUON_KEYS_C.getOrNull(index)
        R.id.key_17 -> GOJUON_KEYS_M.getOrNull(index)
        R.id.key_18 -> GOJUON_KEYS_W.getOrNull(index)
        R.id.key_19 -> GOJUON_KEYS_SLASH.getOrNull(index)
        R.id.key_20 -> null

        R.id.key_11 -> GOJUON_KEYS_B.getOrNull(index)
        R.id.key_12 -> GOJUON_KEYS_L.getOrNull(index)
        R.id.key_13 -> GOJUON_KEYS_V.getOrNull(index)
        R.id.key_14 -> GOJUON_KEYS_UNDER_BAR.getOrNull(index)

        R.id.key_1 -> GOJUON_KEYS_A.getOrNull(index)
        R.id.key_2 -> GOJUON_KEYS_K.getOrNull(index)
        R.id.key_3 -> GOJUON_KEYS_U.getOrNull(index)
        R.id.key_4 -> GOJUON_KEYS_MINUS.getOrNull(index)
        R.id.key_5 -> null
        else -> null
    }
    text = char?.toString() ?: ""
}

fun MaterialTextView.setGojuonTextDefaultEnglish(keyId: Int) =
    setGojuonTextEnglish(keyId, 0)

fun MaterialTextView.setGojuonTextShiftOnEnglish(keyId: Int) =
    setGojuonTextEnglish(keyId, 1)

fun MaterialTextView.setGojuonTextZenkakuDefaultEnglish(keyId: Int) =
    setGojuonTextEnglish(keyId, 2)

fun MaterialTextView.setGojuonTextZenkakuShiftOnEnglish(keyId: Int) =
    setGojuonTextEnglish(keyId, 3)