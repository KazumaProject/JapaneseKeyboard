package com.kazumaproject.gojuon_keyboard.extenstions

import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.core.domain.flick.FlickDirection
import com.kazumaproject.gojuon_keyboard.R

// Flick character mapping for each key. List order: [Top, Left, Tap, Right, Bottom]
private val GOJUON_KEYS_A = listOf('あ', null, 'ぁ', null, null)
private val GOJUON_KEYS_I = listOf('い', null, 'ぃ', null, null)
private val GOJUON_KEYS_U = listOf('う', null, 'ぅ', null, null)
private val GOJUON_KEYS_E = listOf('え', null, 'ぇ', null, null)
private val GOJUON_KEYS_O = listOf('お', null, 'ぉ', null, null)

private val GOJUON_KEYS_KA = listOf('か', 'が', null, null, null)
private val GOJUON_KEYS_KI = listOf('き', 'ぎ', null, null, null)
private val GOJUON_KEYS_KU = listOf('く', 'ぐ', null, null, null)
private val GOJUON_KEYS_KE = listOf('け', 'げ', null, null, null)
private val GOJUON_KEYS_KO = listOf('こ', 'ご', null, null, null)

private val GOJUON_KEYS_SA = listOf('さ', 'ざ', null, null, null)
private val GOJUON_KEYS_SHI = listOf('し', 'じ', null, null, null)
private val GOJUON_KEYS_SU = listOf('す', 'ず', null, null, null)
private val GOJUON_KEYS_SE = listOf('せ', 'ぜ', null, null, null)
private val GOJUON_KEYS_SO = listOf('そ', 'ぞ', null, null, null)

private val GOJUON_KEYS_TA = listOf('た', 'だ', null, null, null)
private val GOJUON_KEYS_CHI = listOf('ち', 'ぢ', null, null, null)
private val GOJUON_KEYS_TSU = listOf('つ', 'づ', 'っ', null, null)
private val GOJUON_KEYS_TE = listOf('て', 'で', null, null, null)
private val GOJUON_KEYS_TO = listOf('と', 'ど', null, null, null)

private val GOJUON_KEYS_NA = listOf('な', null, null, null, null)
private val GOJUON_KEYS_NI = listOf('に', null, null, null, null)
private val GOJUON_KEYS_NU = listOf('ぬ', null, null, null, null)
private val GOJUON_KEYS_NE = listOf('ね', null, null, null, null)
private val GOJUON_KEYS_NO = listOf('の', null, null, null, null)

private val GOJUON_KEYS_HA = listOf('は', 'ば', null, 'ぱ', null)
private val GOJUON_KEYS_HI = listOf('ひ', 'び', null, 'ぴ', null)
private val GOJUON_KEYS_FU = listOf('ふ', 'ぶ', null, 'ぷ', null)
private val GOJUON_KEYS_HE = listOf('へ', 'べ', null, 'ぺ', null)
private val GOJUON_KEYS_HO = listOf('ほ', 'ぼ', null, 'ぽ', null)

private val GOJUON_KEYS_MA = listOf('ま', null, null, null, null)
private val GOJUON_KEYS_MI = listOf('み', null, null, null, null)
private val GOJUON_KEYS_MU = listOf('む', null, null, null, null)
private val GOJUON_KEYS_ME = listOf('め', null, null, null, null)
private val GOJUON_KEYS_MO = listOf('も', null, null, null, null)

private val GOJUON_KEYS_YA = listOf('や', null, 'ゃ', null, null)
private val GOJUON_KEYS_YU = listOf('ゆ', null, 'ゅ', null, null)
private val GOJUON_KEYS_YO = listOf('よ', null, 'ょ', null, null)

private val GOJUON_KEYS_RA = listOf('ら', null, null, null, null)
private val GOJUON_KEYS_RI = listOf('り', null, null, null, null)
private val GOJUON_KEYS_RU = listOf('る', null, null, null, null)
private val GOJUON_KEYS_RE = listOf('れ', null, null, null, null)
private val GOJUON_KEYS_RO = listOf('ろ', null, null, null, null)

private val GOJUON_KEYS_WA = listOf('わ', null, 'ゎ', null, null)
private val GOJUON_KEYS_WO = listOf('を', null, null, null, null)
private val GOJUON_KEYS_N = listOf('ん', null, null, null, null)
private val GOJUON_KEYS_HYPHEN = listOf('ー', null, '-', '〜', null)
private val GOJUON_KEYS_KAGGIKAKKO = listOf('「', '「', '」', '(', ')')
private val GOJUON_KEYS_QUESTION = listOf('？', null, '?', null, null)
private val GOJUON_KEYS_CAUTION = listOf('！', null, '!', null, null)
private val GOJUON_KEYS_KUTEN = listOf('、', null, null, null, null)
private val GOJUON_KEYS_TOUTEN = listOf('。', null, null, null, null)
fun MaterialTextView.setGojuonFlickTextJapanese(keyId: Int, direction: FlickDirection) {
    val char = when (keyId) {
        // A GYO
        R.id.key_51 -> GOJUON_KEYS_A.getOrNull(direction.index)
        R.id.key_52 -> GOJUON_KEYS_I.getOrNull(direction.index)
        R.id.key_53 -> GOJUON_KEYS_U.getOrNull(direction.index)
        R.id.key_54 -> GOJUON_KEYS_E.getOrNull(direction.index)
        R.id.key_55 -> GOJUON_KEYS_O.getOrNull(direction.index)
        // KA GYO
        R.id.key_46 -> GOJUON_KEYS_KA.getOrNull(direction.index)
        R.id.key_47 -> GOJUON_KEYS_KI.getOrNull(direction.index)
        R.id.key_48 -> GOJUON_KEYS_KU.getOrNull(direction.index)
        R.id.key_49 -> GOJUON_KEYS_KE.getOrNull(direction.index)
        R.id.key_50 -> GOJUON_KEYS_KO.getOrNull(direction.index)
        // SA GYO
        R.id.key_41 -> GOJUON_KEYS_SA.getOrNull(direction.index)
        R.id.key_42 -> GOJUON_KEYS_SHI.getOrNull(direction.index)
        R.id.key_43 -> GOJUON_KEYS_SU.getOrNull(direction.index)
        R.id.key_44 -> GOJUON_KEYS_SE.getOrNull(direction.index)
        R.id.key_45 -> GOJUON_KEYS_SO.getOrNull(direction.index)
        // TA GYO
        R.id.key_36 -> GOJUON_KEYS_TA.getOrNull(direction.index)
        R.id.key_37 -> GOJUON_KEYS_CHI.getOrNull(direction.index)
        R.id.key_38 -> GOJUON_KEYS_TSU.getOrNull(direction.index)
        R.id.key_39 -> GOJUON_KEYS_TE.getOrNull(direction.index)
        R.id.key_40 -> GOJUON_KEYS_TO.getOrNull(direction.index)
        // NA GYO
        R.id.key_31 -> GOJUON_KEYS_NA.getOrNull(direction.index)
        R.id.key_32 -> GOJUON_KEYS_NI.getOrNull(direction.index)
        R.id.key_33 -> GOJUON_KEYS_NU.getOrNull(direction.index)
        R.id.key_34 -> GOJUON_KEYS_NE.getOrNull(direction.index)
        R.id.key_35 -> GOJUON_KEYS_NO.getOrNull(direction.index)
        // HA GYO
        R.id.key_26 -> GOJUON_KEYS_HA.getOrNull(direction.index)
        R.id.key_27 -> GOJUON_KEYS_HI.getOrNull(direction.index)
        R.id.key_28 -> GOJUON_KEYS_FU.getOrNull(direction.index)
        R.id.key_29 -> GOJUON_KEYS_HE.getOrNull(direction.index)
        R.id.key_30 -> GOJUON_KEYS_HO.getOrNull(direction.index)
        // MA GYO
        R.id.key_21 -> GOJUON_KEYS_MA.getOrNull(direction.index)
        R.id.key_22 -> GOJUON_KEYS_MI.getOrNull(direction.index)
        R.id.key_23 -> GOJUON_KEYS_MU.getOrNull(direction.index)
        R.id.key_24 -> GOJUON_KEYS_ME.getOrNull(direction.index)
        R.id.key_25 -> GOJUON_KEYS_MO.getOrNull(direction.index)
        // YA GYO
        R.id.key_16 -> GOJUON_KEYS_YA.getOrNull(direction.index)
        R.id.key_17 -> null
        R.id.key_18 -> GOJUON_KEYS_YU.getOrNull(direction.index)
        R.id.key_19 -> null
        R.id.key_20 -> GOJUON_KEYS_YO.getOrNull(direction.index)
        // RA GYO
        R.id.key_11 -> GOJUON_KEYS_RA.getOrNull(direction.index)
        R.id.key_12 -> GOJUON_KEYS_RI.getOrNull(direction.index)
        R.id.key_13 -> GOJUON_KEYS_RU.getOrNull(direction.index)
        R.id.key_14 -> GOJUON_KEYS_RE.getOrNull(direction.index)
        R.id.key_15 -> GOJUON_KEYS_RO.getOrNull(direction.index)
        // WA/N GYO
        R.id.key_6 -> GOJUON_KEYS_WA.getOrNull(direction.index)
        R.id.key_7 -> GOJUON_KEYS_WO.getOrNull(direction.index)
        R.id.key_8 -> GOJUON_KEYS_N.getOrNull(direction.index)
        R.id.key_9 -> GOJUON_KEYS_HYPHEN.getOrNull(direction.index)
        R.id.key_10 -> null

        R.id.key_1 -> GOJUON_KEYS_KAGGIKAKKO.getOrNull(direction.index)
        R.id.key_2 -> GOJUON_KEYS_QUESTION.getOrNull(direction.index)
        R.id.key_3 -> GOJUON_KEYS_CAUTION.getOrNull(direction.index)
        R.id.key_4 -> GOJUON_KEYS_KUTEN.getOrNull(direction.index)
        R.id.key_5 -> GOJUON_KEYS_TOUTEN.getOrNull(direction.index)
        else -> null
    }
    text = char?.toString() ?: ""
}

fun MaterialTextView.setGojuonTextTapJapanese(keyId: Int) =
    setGojuonFlickTextJapanese(keyId, FlickDirection.Tap)

fun MaterialTextView.setGojuonTextFlickLeftJapanese(keyId: Int) =
    setGojuonFlickTextJapanese(keyId, FlickDirection.Left)

fun MaterialTextView.setGojuonTextFlickTopJapanese(keyId: Int) =
    setGojuonFlickTextJapanese(keyId, FlickDirection.Top)

fun MaterialTextView.setGojuonTextFlickRightJapanese(keyId: Int) =
    setGojuonFlickTextJapanese(keyId, FlickDirection.Right)

fun MaterialTextView.setGojuonTextFlickBottomJapanese(keyId: Int) =
    setGojuonFlickTextJapanese(keyId, FlickDirection.Bottom)