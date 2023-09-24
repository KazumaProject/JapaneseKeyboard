package com.kazumaproject.markdownhelperkeyboard.ime_service.components

import android.os.Build
import com.kazumaproject.markdownhelperkeyboard.R

interface TenKeyMapHolder{
    val keysJapanese: Set<Int>
    val keysEnglish: Set<Int>
    val keysNumber: Set<Int>
    fun getTenKeyInfoJapanese(indexId: Int): TenKeyInfo
    fun getTenKeyInfoEnglish(indexId: Int): TenKeyInfo
    fun getTenKeyInfoNumber(indexId: Int): TenKeyInfo
}

class TenKeyMap : TenKeyMapHolder{

    private val listJapanese: Map<Int,TenKeyInfo> = mapOf(
        R.id.key_1 to TenKeyInfo.KeyAJapanese,
        R.id.key_2 to TenKeyInfo.KeyKAJapanese,
        R.id.key_3 to TenKeyInfo.KeySAJapanese,
        R.id.key_4 to TenKeyInfo.KeyTAJapanese,
        R.id.key_5 to TenKeyInfo.KeyNAJapanese,
        R.id.key_6 to TenKeyInfo.KeyHAJapanese,
        R.id.key_7 to TenKeyInfo.KeyMAJapanese,
        R.id.key_8 to TenKeyInfo.KeyYAJapanese,
        R.id.key_9 to TenKeyInfo.KeyRAJapanese,
        R.id.key_11 to TenKeyInfo.KeyWAJapanese,
        R.id.key_12 to TenKeyInfo.KeyKigouJapanese,
    )

    private val listEnglish: Map<Int,TenKeyInfo> = mapOf(
        R.id.key_1 to TenKeyInfo.Key1English,
        R.id.key_2 to TenKeyInfo.Key2English,
        R.id.key_3 to TenKeyInfo.Key3English,
        R.id.key_4 to TenKeyInfo.Key4English,
        R.id.key_5 to TenKeyInfo.Key5English,
        R.id.key_6 to TenKeyInfo.Key6English,
        R.id.key_7 to TenKeyInfo.Key7English,
        R.id.key_8 to TenKeyInfo.Key8English,
        R.id.key_9 to TenKeyInfo.Key9English,
        R.id.key_11 to TenKeyInfo.Key0English,
        R.id.key_12 to TenKeyInfo.KeyKigouEnglish,
    )

    private val listNumber: Map<Int,TenKeyInfo> = mapOf(
        R.id.key_1 to TenKeyInfo.Key1Number,
        R.id.key_2 to TenKeyInfo.Key2Number,
        R.id.key_3 to TenKeyInfo.Key3Number,
        R.id.key_4 to TenKeyInfo.Key4Number,
        R.id.key_5 to TenKeyInfo.Key5Number,
        R.id.key_6 to TenKeyInfo.Key6Number,
        R.id.key_7 to TenKeyInfo.Key7Number,
        R.id.key_8 to TenKeyInfo.Key8Number,
        R.id.key_9 to TenKeyInfo.Key9Number,
        R.id.key_11 to TenKeyInfo.Key0Number,
        R.id.key_12 to TenKeyInfo.KeyKigouNumber,
    )

    override val keysJapanese: Set<Int>
        get() = listJapanese.keys

    override val keysEnglish: Set<Int>
        get() = listEnglish.keys

    override val keysNumber: Set<Int>
        get() = listNumber.keys

    override fun getTenKeyInfoJapanese(indexId: Int): TenKeyInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            listJapanese.getOrDefault(indexId, TenKeyInfo.Null)
        } else {
            listJapanese.getValue(indexId)
        }
    }

    override fun getTenKeyInfoEnglish(indexId: Int): TenKeyInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            listEnglish.getOrDefault(indexId, TenKeyInfo.Null)
        } else {
            listEnglish.getValue(indexId)
        }
    }

    override fun getTenKeyInfoNumber(indexId: Int): TenKeyInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            listNumber.getOrDefault(indexId, TenKeyInfo.Null)
        } else {
            listNumber.getValue(indexId)
        }
    }

}