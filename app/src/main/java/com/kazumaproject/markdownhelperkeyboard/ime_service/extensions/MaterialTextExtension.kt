package com.kazumaproject.markdownhelperkeyboard.ime_service.extensions

import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.markdownhelperkeyboard.R

private val JP_KEYS_A = listOf(
    'あ','い','う','え','お',
)

private val JP_KEYS_KA = listOf(
    'か','き','く','け','こ',
)

private val JP_KEYS_SA = listOf(
    'さ','し','す','せ','そ',
)

private val JP_KEYS_TA = listOf(
    'た','ち','つ','て','と',
)

private val JP_KEYS_NA = listOf(
    'な','に','ぬ','ね','の',
)

private val JP_KEYS_HA = listOf(
    'は','ひ','ふ','へ','ほ',
)

private val JP_KEYS_MA = listOf(
    'ま','み','む','め','も',
)

private val JP_KEYS_YA = listOf(
    'や','(','ゆ',')','よ',
)

private val JP_KEYS_RA = listOf(
    'ら','り','る','れ','ろ',
)

private val JP_KEYS_WA = listOf(
    'わ','を','ん','ー','〜',
)

private val JP_KEYS_KIGOU = listOf(
    '、','。','？','！','…','・'
)

const val EMPTY_STRING = ""

private val ENGLISH_KEY1_CHAR = listOf('@','#','&','_')
private val ENGLISH_KEY11_CHAR = listOf('\'','\"',':',';')
private val ENGLISH_KEY12_CHAR = listOf('.',',','?','!','-')

private val ENGLISH_SMALL_CHAR = listOf(
    'a','b','c','d','e',
    'f','g','h','i','j',
    'k','l','m','n','o',
    'p','q','r','s','t',
    'u','v','w','x','y',
    'z',
)

private val NUMBER_CHAR = listOf(
    '0','1','2','3','4',
    '5', '6','7','8','9',
)

private val NUMBER_KEY1_SYMBOL_CHAR = listOf('☆','♪','→')
private val NUMBER_KEY2_SYMBOL_CHAR = listOf('￥','$','€')
private val NUMBER_KEY3_SYMBOL_CHAR = listOf('%','°','#')
private val NUMBER_KEY4_SYMBOL_CHAR = listOf('○','*','・')
private val NUMBER_KEY5_SYMBOL_CHAR = listOf('+','×','÷','-')
private val NUMBER_KEY6_SYMBOL_CHAR = listOf('<','=','>')
private val NUMBER_KEY7_SYMBOL_CHAR = listOf('「','」',':')
private val NUMBER_KEY8_SYMBOL_CHAR = listOf('〒','々','〆')
private val NUMBER_KEY9_SYMBOL_CHAR = listOf('^','|','\\')
private val NUMBER_KEY11_SYMBOL_CHAR = listOf('~','…','@')
private val NUMBER_KEY12_SYMBOL_CHAR = listOf('.',',','-','/')

fun MaterialTextView.setTextFlickRightJapanese(
    keyId: Int
){
    when(keyId){
        R.id.key_1 ->{
            text = JP_KEYS_A[3].toString()
        }
        R.id.key_2 -> {
            text = JP_KEYS_KA[3].toString()
        }
        R.id.key_3 -> {
            text = JP_KEYS_SA[3].toString()
        }
        R.id.key_4 -> {
            text = JP_KEYS_TA[3].toString()
        }
        R.id.key_5 -> {
            text = JP_KEYS_NA[3].toString()
        }
        R.id.key_6 -> {
            text = JP_KEYS_HA[3].toString()
        }
        R.id.key_7 -> {
            text = JP_KEYS_MA[3].toString()
        }
        R.id.key_8 -> {
            text = JP_KEYS_YA[3].toString()
        }
        R.id.key_9 -> {
            text = JP_KEYS_RA[3].toString()
        }
        R.id.key_11 -> {
            text = JP_KEYS_WA[3].toString()
        }
        R.id.key_12 -> {
            text = JP_KEYS_KIGOU[3].toString()
        }
    }
}

fun MaterialTextView.setTextFlickLeftJapanese(
    keyId: Int
){
    when(keyId){
        R.id.key_1 -> {
            text = JP_KEYS_A[1].toString()
        }
        R.id.key_2 -> {
            text = JP_KEYS_KA[1].toString()
        }
        R.id.key_3 -> {
            text = JP_KEYS_SA[1].toString()
        }
        R.id.key_4 -> {
            text = JP_KEYS_TA[1].toString()
        }
        R.id.key_5 -> {
            text = JP_KEYS_NA[1].toString()
        }
        R.id.key_6 -> {
            text = JP_KEYS_HA[1].toString()
        }
        R.id.key_7 -> {
            text = JP_KEYS_MA[1].toString()
        }
        R.id.key_8 -> {
            text = JP_KEYS_YA[1].toString()
        }
        R.id.key_9 -> {
            text = JP_KEYS_RA[1].toString()
        }
        R.id.key_11 -> {
            text = JP_KEYS_WA[1].toString()
        }
        R.id.key_12 -> {
            text = JP_KEYS_KIGOU[1].toString()
        }
    }
}

fun MaterialTextView.setTextFlickBottomJapanese(
    keyId: Int
){
    when(keyId){
        R.id.key_1 -> {
            text = JP_KEYS_A[4].toString()
        }
        R.id.key_2 -> {
            text = JP_KEYS_KA[4].toString()
        }
        R.id.key_3 -> {
            text = JP_KEYS_SA[4].toString()
        }
        R.id.key_4 -> {
            text = JP_KEYS_TA[4].toString()
        }
        R.id.key_5 -> {
            text = JP_KEYS_NA[4].toString()
        }
        R.id.key_6 -> {
            text = JP_KEYS_HA[4].toString()
        }
        R.id.key_7 -> {
            text = JP_KEYS_MA[4].toString()
        }
        R.id.key_8 -> {
            text = JP_KEYS_YA[4].toString()
        }
        R.id.key_9 -> {
            text = JP_KEYS_RA[4].toString()
        }
        R.id.key_11 -> {
            text = JP_KEYS_WA[4].toString()
        }
        R.id.key_12 ->{
            text = JP_KEYS_KIGOU[4].toString()
        }
    }
}

fun MaterialTextView.setTextFlickTopJapanese(
    keyId: Int
){
    when(keyId){
        R.id.key_1 -> {
            text = JP_KEYS_A[2].toString()
        }
        R.id.key_2 -> {
            text = JP_KEYS_KA[2].toString()
        }
        R.id.key_3 -> {
            text = JP_KEYS_SA[2].toString()
        }
        R.id.key_4 -> {
            text = JP_KEYS_TA[2].toString()
        }
        R.id.key_5 -> {
            text = JP_KEYS_NA[2].toString()
        }
        R.id.key_6 -> {
            text = JP_KEYS_HA[2].toString()
        }
        R.id.key_7 -> {
            text = JP_KEYS_MA[2].toString()
        }
        R.id.key_8 -> {
            text = JP_KEYS_YA[2].toString()
        }
        R.id.key_9 -> {
            text = JP_KEYS_RA[2].toString()
        }
        R.id.key_11 -> {
            text = JP_KEYS_WA[2].toString()
        }
        R.id.key_12 -> {
            text = JP_KEYS_KIGOU[2].toString()
        }
    }
}

fun MaterialTextView.setTextTapJapanese(
    keyId: Int
){
    when(keyId){
        R.id.key_1 -> {
            text = JP_KEYS_A[0].toString()
        }
        R.id.key_2 -> {
            text = JP_KEYS_KA[0].toString()
        }
        R.id.key_3 -> {
            text = JP_KEYS_SA[0].toString()
        }
        R.id.key_4 -> {
            text = JP_KEYS_TA[0].toString()
        }
        R.id.key_5 -> {
            text = JP_KEYS_NA[0].toString()
        }
        R.id.key_6 -> {
            text = JP_KEYS_HA[0].toString()
        }
        R.id.key_7 -> {
            text = JP_KEYS_MA[0].toString()
        }
        R.id.key_8 -> {
            text = JP_KEYS_YA[0].toString()
        }
        R.id.key_9 -> {
            text = JP_KEYS_RA[0].toString()
        }
        R.id.key_11 -> {
            text = JP_KEYS_WA[0].toString()
        }
        R.id.key_12 -> {
            text = JP_KEYS_KIGOU[0].toString()
        }
    }
}

fun MaterialTextView.setTextFlickRightEnglish(
    keyId: Int
){
    when(keyId){
        R.id.key_1 -> {
            text = ENGLISH_KEY1_CHAR[3].toString()
        }
        R.id.key_2 -> {
            text = EMPTY_STRING
        }
        R.id.key_3 -> {
            text = EMPTY_STRING
        }
        R.id.key_4 -> {
            text = EMPTY_STRING
        }
        R.id.key_5 -> {
            text = EMPTY_STRING
        }
        R.id.key_6 -> {
            text = EMPTY_STRING
        }
        R.id.key_7 -> {
            text = ENGLISH_SMALL_CHAR[18].toString()
        }
        R.id.key_8 -> {
            text = EMPTY_STRING
        }
        R.id.key_9 -> {
            text = ENGLISH_SMALL_CHAR[25].toString()
        }
        R.id.key_11 -> {
            text = ENGLISH_KEY11_CHAR[3].toString()
        }
        R.id.key_12 -> {
            text = ENGLISH_KEY12_CHAR[3].toString()
        }
    }
}

fun MaterialTextView.setTextFlickLeftEnglish(
    keyId: Int
){
    when(keyId){
        R.id.key_1 -> {
            text = ENGLISH_KEY1_CHAR[1].toString()
        }
        R.id.key_2 -> {
            text = ENGLISH_SMALL_CHAR[1].toString()
        }
        R.id.key_3 -> {
            text = ENGLISH_SMALL_CHAR[4].toString()
        }
        R.id.key_4 -> {
            text = ENGLISH_SMALL_CHAR[7].toString()
        }
        R.id.key_5 -> {
            text = ENGLISH_SMALL_CHAR[10].toString()
        }
        R.id.key_6 -> {
            text = ENGLISH_SMALL_CHAR[13].toString()
        }
        R.id.key_7 -> {
            text = ENGLISH_SMALL_CHAR[16].toString()
        }
        R.id.key_8 -> {
            text = ENGLISH_SMALL_CHAR[20].toString()
        }
        R.id.key_9 -> {
            text = ENGLISH_SMALL_CHAR[23].toString()
        }
        R.id.key_11 -> {
            text = ENGLISH_KEY11_CHAR[1].toString()
        }
        R.id.key_12 -> {
            text = ENGLISH_KEY12_CHAR[1].toString()
        }
    }
}

fun MaterialTextView.setTextFlickBottomEnglish(
    keyId: Int
){
    when(keyId){
        R.id.key_1 -> {
            text = NUMBER_CHAR[1].toString()
        }
        R.id.key_2 -> {
            text = NUMBER_CHAR[2].toString()
        }
        R.id.key_3 -> {
            text = NUMBER_CHAR[3].toString()
        }
        R.id.key_4 -> {
            text = NUMBER_CHAR[4].toString()
        }
        R.id.key_5 -> {
            text = NUMBER_CHAR[5].toString()
        }
        R.id.key_6 -> {
            text = NUMBER_CHAR[6].toString()
        }
        R.id.key_7 -> {
            text = NUMBER_CHAR[7].toString()
        }
        R.id.key_8 -> {
            text = NUMBER_CHAR[8].toString()
        }
        R.id.key_9 -> {
            text = NUMBER_CHAR[9].toString()
        }
        R.id.key_11 -> {
            text = NUMBER_CHAR[0].toString()
        }
        R.id.key_12 ->{
            text = EMPTY_STRING
        }
    }
}

fun MaterialTextView.setTextFlickTopEnglish(
    keyId: Int
){
    when(keyId){
        R.id.key_1 -> {
            text = ENGLISH_KEY1_CHAR[2].toString()
        }
        R.id.key_2 -> {
            text = ENGLISH_SMALL_CHAR[2].toString()
        }
        R.id.key_3 -> {
            text = ENGLISH_SMALL_CHAR[5].toString()
        }
        R.id.key_4 -> {
            text = ENGLISH_SMALL_CHAR[8].toString()
        }
        R.id.key_5 -> {
            text = ENGLISH_SMALL_CHAR[11].toString()
        }
        R.id.key_6 -> {
            text = ENGLISH_SMALL_CHAR[15].toString()
        }
        R.id.key_7 -> {
            text = ENGLISH_SMALL_CHAR[17].toString()
        }
        R.id.key_8 -> {
            text = ENGLISH_SMALL_CHAR[21].toString()
        }
        R.id.key_9 -> {
            text = ENGLISH_SMALL_CHAR[24].toString()
        }
        R.id.key_11 -> {
            text = ENGLISH_KEY11_CHAR[2].toString()
        }
        R.id.key_12 -> {
            text = ENGLISH_KEY12_CHAR[2].toString()
        }
    }
}


fun MaterialTextView.setTextFlickRightNumber(
    keyId: Int
){
    when(keyId){
        R.id.key_1 -> {
            text = NUMBER_KEY1_SYMBOL_CHAR[2].toString()
        }
        R.id.key_2 -> {
            text = NUMBER_KEY2_SYMBOL_CHAR[2].toString()
        }
        R.id.key_3 -> {
            text = NUMBER_KEY3_SYMBOL_CHAR[2].toString()
        }
        R.id.key_4 -> {
            text = NUMBER_KEY4_SYMBOL_CHAR[2].toString()
        }
        R.id.key_5 -> {
            text = NUMBER_KEY5_SYMBOL_CHAR[2].toString()
        }
        R.id.key_6 -> {
            text = NUMBER_KEY6_SYMBOL_CHAR[2].toString()
        }
        R.id.key_7 -> {
            text = NUMBER_KEY7_SYMBOL_CHAR[2].toString()
        }
        R.id.key_8 -> {
            text = NUMBER_KEY8_SYMBOL_CHAR[2].toString()
        }
        R.id.key_9 -> {
            text = NUMBER_KEY9_SYMBOL_CHAR[2].toString()
        }
        R.id.key_11 -> {
            text = NUMBER_KEY11_SYMBOL_CHAR[2].toString()
        }
        R.id.key_12 -> {
            text = NUMBER_KEY12_SYMBOL_CHAR[3].toString()
        }
    }
}

fun MaterialTextView.setTextFlickLeftNumber(
    keyId: Int
){
    when(keyId){
        R.id.key_1 -> {
            text = NUMBER_KEY1_SYMBOL_CHAR[0].toString()
        }
        R.id.key_2 -> {
            text = NUMBER_KEY2_SYMBOL_CHAR[0].toString()
        }
        R.id.key_3 -> {
            text = NUMBER_KEY3_SYMBOL_CHAR[0].toString()
        }
        R.id.key_4 -> {
            text = NUMBER_KEY4_SYMBOL_CHAR[0].toString()
        }
        R.id.key_5 -> {
            text = NUMBER_KEY5_SYMBOL_CHAR[0].toString()
        }
        R.id.key_6 -> {
            text = NUMBER_KEY6_SYMBOL_CHAR[0].toString()
        }
        R.id.key_7 -> {
            text = NUMBER_KEY7_SYMBOL_CHAR[0].toString()
        }
        R.id.key_8 -> {
            text = NUMBER_KEY8_SYMBOL_CHAR[0].toString()
        }
        R.id.key_9 -> {
            text = NUMBER_KEY9_SYMBOL_CHAR[0].toString()
        }
        R.id.key_11 -> {
            text = NUMBER_KEY11_SYMBOL_CHAR[0].toString()
        }
        R.id.key_12 -> {
            text = NUMBER_KEY12_SYMBOL_CHAR[1].toString()
        }
    }
}

fun MaterialTextView.setTextFlickBottomNumber(
    keyId: Int
){
    when(keyId){
        R.id.key_1 -> {
            text = EMPTY_STRING
        }
        R.id.key_2 -> {
            text = EMPTY_STRING
        }
        R.id.key_3 -> {
            text = EMPTY_STRING
        }
        R.id.key_4 -> {
            text = EMPTY_STRING
        }
        R.id.key_5 -> {
            text = EMPTY_STRING
        }
        R.id.key_6 -> {
            text = EMPTY_STRING
        }
        R.id.key_7 -> {
            text = EMPTY_STRING
        }
        R.id.key_8 -> {
            text = EMPTY_STRING
        }
        R.id.key_9 -> {
            text = EMPTY_STRING
        }
        R.id.key_11 -> {
            text = EMPTY_STRING
        }
        R.id.key_12 ->{
            text = EMPTY_STRING
        }
    }
}

fun MaterialTextView.setTextFlickTopNumber(
    keyId: Int
){
    when(keyId){
        R.id.key_1 -> {
            text = NUMBER_KEY1_SYMBOL_CHAR[1].toString()
        }
        R.id.key_2 -> {
            text = NUMBER_KEY2_SYMBOL_CHAR[1].toString()
        }
        R.id.key_3 -> {
            text = NUMBER_KEY3_SYMBOL_CHAR[1].toString()
        }
        R.id.key_4 -> {
            text = NUMBER_KEY4_SYMBOL_CHAR[1].toString()
        }
        R.id.key_5 -> {
            text = NUMBER_KEY5_SYMBOL_CHAR[1].toString()
        }
        R.id.key_6 -> {
            text = NUMBER_KEY6_SYMBOL_CHAR[1].toString()
        }
        R.id.key_7 -> {
            text = NUMBER_KEY7_SYMBOL_CHAR[1].toString()
        }
        R.id.key_8 -> {
            text = NUMBER_KEY8_SYMBOL_CHAR[1].toString()
        }
        R.id.key_9 -> {
            text = NUMBER_KEY9_SYMBOL_CHAR[1].toString()
        }
        R.id.key_11 -> {
            text = NUMBER_KEY11_SYMBOL_CHAR[1].toString()
        }
        R.id.key_12 -> {
            text = NUMBER_KEY12_SYMBOL_CHAR[2].toString()
        }
    }
}

