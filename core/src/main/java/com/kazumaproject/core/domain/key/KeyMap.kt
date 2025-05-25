package com.kazumaproject.core.domain.key

interface KeyMapHolder {
    val keysJapanese: Set<Key>
    val keysEnglish: Set<Key>
    val keysNumber: Set<Key>
    fun getKeyInfoJapanese(key: Key, isTablet: Boolean): KeyInfo
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

    private val listJapaneseTablet: Map<Key, KeyInfo> = mapOf(
        // あ row
        Key.KeyA to KeyInfo.TabletKeyAJapanese,
        Key.KeyI to KeyInfo.KeyIJapanese,
        Key.KeyU to KeyInfo.KeyUJapanese,
        Key.KeyE to KeyInfo.KeyEJapanese,
        Key.KeyO to KeyInfo.KeyOJapanese,

        // か row
        Key.KeyKA to KeyInfo.TabletKeyKAJapanese,
        Key.KeyKI to KeyInfo.KeyKIJapanese,
        Key.KeyKU to KeyInfo.KeyKUJapanese,
        Key.KeyKE to KeyInfo.KeyKEJapanese,
        Key.KeyKO to KeyInfo.KeyKOJapanese,

        // さ row
        Key.KeySA to KeyInfo.TabletKeySAJapanese,
        Key.KeySHI to KeyInfo.KeySHIJapanese,
        Key.KeySU to KeyInfo.KeySUJapanese,
        Key.KeySE to KeyInfo.KeySEJapanese,
        Key.KeySO to KeyInfo.KeySOJapanese,

        // た row
        Key.KeyTA to KeyInfo.TabletKeyTAJapanese,
        Key.KeyCHI to KeyInfo.KeyCHIJapanese,
        Key.KeyTSU to KeyInfo.KeyTSUJapanese,
        Key.KeyTE to KeyInfo.KeyTEJapanese,
        Key.KeyTO to KeyInfo.KeyTOJapanese,

        // な row
        Key.KeyNA to KeyInfo.TabletKeyNAJapanese,
        Key.KeyNI to KeyInfo.KeyNIJapanese,
        Key.KeyNU to KeyInfo.KeyNUJapanese,
        Key.KeyNE to KeyInfo.KeyNEJapanese,
        Key.KeyNO to KeyInfo.KeyNOJapanese,

        // は row
        Key.KeyHA to KeyInfo.TabletKeyHAJapanese,
        Key.KeyHI to KeyInfo.KeyHIJapanese,
        Key.KeyFU to KeyInfo.KeyFUJapanese,
        Key.KeyHE to KeyInfo.KeyHEJapanese,
        Key.KeyHO to KeyInfo.KeyHOJapanese,

        // ま row
        Key.KeyMA to KeyInfo.TabletKeyMAJapanese,
        Key.KeyMI to KeyInfo.KeyMIJapanese,
        Key.KeyMU to KeyInfo.KeyMUJapanese,
        Key.KeyME to KeyInfo.KeyMEJapanese,
        Key.KeyMO to KeyInfo.KeyMOJapanese,

        // や row
        Key.KeyYA to KeyInfo.TabletKeyYAJapanese,
        Key.KeyYU to KeyInfo.KeyYUJapanese,
        Key.KeyYO to KeyInfo.KeyYOJapanese,

        // ら row
        Key.KeyRA to KeyInfo.TabletKeyRAJapanese,
        Key.KeyRI to KeyInfo.KeyRIJapanese,
        Key.KeyRU to KeyInfo.KeyRUJapanese,
        Key.KeyRE to KeyInfo.KeyREJapanese,
        Key.KeyRO to KeyInfo.KeyROJapanese,

        // わ row
        Key.KeyWA to KeyInfo.TabletKeyWAJapanese,
        Key.KeyWO to KeyInfo.KeyWOJapanese,
        Key.KeyN to KeyInfo.KeyNNJapanese,

        // 記号等
        Key.KeyKutouten to KeyInfo.KeyKUTENJapanese, // 、
        Key.KeyTouten to KeyInfo.KeyTOUTENJapanese,  // 。
        Key.KeyMinus to KeyInfo.KeyMINUSJapanese,    // ー
        Key.KeyDakutenSmall to KeyInfo.KeyDAKUTENJapanese, // (No char)
        Key.KeyKagikakko to KeyInfo.KeyKAGIKAKKOJapanese, // 「
        Key.KeyQuestion to KeyInfo.KeyQUESTIONJapanese, // ？
        Key.KeyCaution to KeyInfo.KeyCAUTIONJapanese,   // ！
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

    override fun getKeyInfoJapanese(key: Key, isTablet: Boolean): KeyInfo {
        return if (isTablet) {
            listJapaneseTablet.getOrDefault(key, KeyInfo.Null)
        } else {
            listJapanese.getOrDefault(key, KeyInfo.Null)
        }
    }

    override fun getKeyInfoEnglish(key: Key): KeyInfo {
        return listEnglish.getOrDefault(key, KeyInfo.Null)
    }

    override fun getKeyInfoNumber(key: Key): KeyInfo {
        return listNumber.getOrDefault(key, KeyInfo.Null)
    }

}