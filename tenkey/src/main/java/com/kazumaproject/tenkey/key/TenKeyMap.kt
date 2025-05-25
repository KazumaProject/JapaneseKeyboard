package com.kazumaproject.tenkey.key

import com.kazumaproject.core.key.Key

interface TenKeyMapHolder {
    val keysJapanese: Set<Key>
    val keysEnglish: Set<Key>
    val keysNumber: Set<Key>
    fun getTenKeyInfoJapanese(key: Key): TenKeyInfo
    fun getTenKeyInfoEnglish(key: Key): TenKeyInfo
    fun getTenKeyInfoNumber(key: Key): TenKeyInfo
}

class TenKeyMap : TenKeyMapHolder {

    private val listJapanese: Map<Key, TenKeyInfo> = mapOf(
        Key.KeyA to TenKeyInfo.KeyAJapanese,
        Key.KeyKA to TenKeyInfo.KeyKAJapanese,
        Key.KeySA to TenKeyInfo.KeySAJapanese,
        Key.KeyTA to TenKeyInfo.KeyTAJapanese,
        Key.KeyNA to TenKeyInfo.KeyNAJapanese,
        Key.KeyHA to TenKeyInfo.KeyHAJapanese,
        Key.KeyMA to TenKeyInfo.KeyMAJapanese,
        Key.KeyYA to TenKeyInfo.KeyYAJapanese,
        Key.KeyRA to TenKeyInfo.KeyRAJapanese,
        Key.KeyWA to TenKeyInfo.KeyWAJapanese,
        Key.KeyKutouten to TenKeyInfo.KeyKigouJapanese,
    )

    private val listEnglish: Map<Key, TenKeyInfo> = mapOf(
        Key.KeyA to TenKeyInfo.Key1English,
        Key.KeyKA to TenKeyInfo.Key2English,
        Key.KeySA to TenKeyInfo.Key3English,
        Key.KeyTA to TenKeyInfo.Key4English,
        Key.KeyNA to TenKeyInfo.Key5English,
        Key.KeyHA to TenKeyInfo.Key6English,
        Key.KeyMA to TenKeyInfo.Key7English,
        Key.KeyYA to TenKeyInfo.Key8English,
        Key.KeyRA to TenKeyInfo.Key9English,
        Key.KeyWA to TenKeyInfo.Key0English,
        Key.KeyKutouten to TenKeyInfo.KeyKigouEnglish,
    )

    private val listNumber: Map<Key, TenKeyInfo> = mapOf(
        Key.KeyA to TenKeyInfo.Key1Number,
        Key.KeyKA to TenKeyInfo.Key2Number,
        Key.KeySA to TenKeyInfo.Key3Number,
        Key.KeyTA to TenKeyInfo.Key4Number,
        Key.KeyNA to TenKeyInfo.Key5Number,
        Key.KeyHA to TenKeyInfo.Key6Number,
        Key.KeyMA to TenKeyInfo.Key7Number,
        Key.KeyYA to TenKeyInfo.Key8Number,
        Key.KeyRA to TenKeyInfo.Key9Number,
        Key.KeyWA to TenKeyInfo.Key0Number,
        Key.KeyKutouten to TenKeyInfo.KeyKigouNumber,
        Key.KeyDakutenSmall to TenKeyInfo.KeyDakutenSmallNumber
    )

    override val keysJapanese: Set<Key>
        get() = listJapanese.keys

    override val keysEnglish: Set<Key>
        get() = listEnglish.keys

    override val keysNumber: Set<Key>
        get() = listNumber.keys

    override fun getTenKeyInfoJapanese(key: Key): TenKeyInfo {
        return listJapanese.getOrDefault(key, TenKeyInfo.Null)
    }

    override fun getTenKeyInfoEnglish(key: Key): TenKeyInfo {
        return listEnglish.getOrDefault(key, TenKeyInfo.Null)
    }

    override fun getTenKeyInfoNumber(key: Key): TenKeyInfo {
        return listNumber.getOrDefault(key, TenKeyInfo.Null)
    }

}