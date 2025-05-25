package com.kazumaproject.core.key

interface KeyMapHolder {
    val keysJapanese: Set<Key>
    val keysEnglish: Set<Key>
    val keysNumber: Set<Key>
    fun getKeyInfoJapanese(key: Key): KeyInfo
    fun getKeyInfoEnglish(key: Key): KeyInfo
    fun getKeyInfoNumber(key: Key): KeyInfo
}


class KeyMap : KeyMapHolder {
    private val listJapanese: Map<Key, KeyInfo> = mapOf(
        Key.KeyA to KeyInfo.KeyAJapanese,
        Key.KeyKA to KeyInfo.KeyKAJapanese,
        Key.KeySA to KeyInfo.KeySAJapanese,
        Key.KeyTA to KeyInfo.KeyTAJapanese,
        Key.KeyNA to KeyInfo.KeyNAJapanese,
        Key.KeyHA to KeyInfo.KeyHAJapanese,
        Key.KeyMA to KeyInfo.KeyMAJapanese,
        Key.KeyYA to KeyInfo.KeyYAJapanese,
        Key.KeyRA to KeyInfo.KeyRAJapanese,
        Key.KeyWA to KeyInfo.KeyWAJapanese,
        Key.KeyKutouten to KeyInfo.KeyKigouJapanese,
    )

    private val listEnglish: Map<Key, KeyInfo> = mapOf(
        Key.KeyA to KeyInfo.Key1English,
        Key.KeyKA to KeyInfo.Key2English,
        Key.KeySA to KeyInfo.Key3English,
        Key.KeyTA to KeyInfo.Key4English,
        Key.KeyNA to KeyInfo.Key5English,
        Key.KeyHA to KeyInfo.Key6English,
        Key.KeyMA to KeyInfo.Key7English,
        Key.KeyYA to KeyInfo.Key8English,
        Key.KeyRA to KeyInfo.Key9English,
        Key.KeyWA to KeyInfo.Key0English,
        Key.KeyKutouten to KeyInfo.KeyKigouEnglish,
    )

    private val listNumber: Map<Key, KeyInfo> = mapOf(
        Key.KeyA to KeyInfo.Key1Number,
        Key.KeyKA to KeyInfo.Key2Number,
        Key.KeySA to KeyInfo.Key3Number,
        Key.KeyTA to KeyInfo.Key4Number,
        Key.KeyNA to KeyInfo.Key5Number,
        Key.KeyHA to KeyInfo.Key6Number,
        Key.KeyMA to KeyInfo.Key7Number,
        Key.KeyYA to KeyInfo.Key8Number,
        Key.KeyRA to KeyInfo.Key9Number,
        Key.KeyWA to KeyInfo.Key0Number,
        Key.KeyKutouten to KeyInfo.KeyKigouNumber,
        Key.KeyDakutenSmall to KeyInfo.KeyDakutenSmallNumber
    )

    override val keysJapanese: Set<Key>
        get() = listJapanese.keys

    override val keysEnglish: Set<Key>
        get() = listEnglish.keys

    override val keysNumber: Set<Key>
        get() = listNumber.keys

    override fun getKeyInfoJapanese(key: Key): KeyInfo {
        return listJapanese.getOrDefault(key, KeyInfo.Null)
    }

    override fun getKeyInfoEnglish(key: Key): KeyInfo {
        return listEnglish.getOrDefault(key, KeyInfo.Null)
    }

    override fun getKeyInfoNumber(key: Key): KeyInfo {
        return listNumber.getOrDefault(key, KeyInfo.Null)
    }

}