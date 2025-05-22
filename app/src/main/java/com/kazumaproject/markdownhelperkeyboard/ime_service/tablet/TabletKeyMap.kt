package com.kazumaproject.markdownhelperkeyboard.ime_service.tablet

interface TabletKeyMapHolder {
    val keysJapanese: Set<TabletKey>
    fun getTenKeyInfoJapanese(tabletKey: TabletKey): TabletKeyInfo
}

class TabletKeyMap : TabletKeyMapHolder {

    private val listJapanese: Map<TabletKey, TabletKeyInfo> = mapOf(
        // あ-row
        TabletKey.KeyA to TabletKeyInfo.KeyAJapanese,
        TabletKey.KeyI to TabletKeyInfo.KeyIJapanese,
        TabletKey.KeyU to TabletKeyInfo.KeyUJapanese,
        TabletKey.KeyE to TabletKeyInfo.KeyEJapanese,
        TabletKey.KeyO to TabletKeyInfo.KeyOJapanese,

        // か-row
        TabletKey.KeyKA to TabletKeyInfo.KeyKAJapanese,
        TabletKey.KeyKI to TabletKeyInfo.KeyKIJapanese,
        TabletKey.KeyKU to TabletKeyInfo.KeyKUJapanese,
        TabletKey.KeyKE to TabletKeyInfo.KeyKEJapanese,
        TabletKey.KeyKO to TabletKeyInfo.KeyKOJapanese,

        // さ-row
        TabletKey.KeySA to TabletKeyInfo.KeySAJapanese,
        TabletKey.KeySHI to TabletKeyInfo.KeySHIJapanese,
        TabletKey.KeySU to TabletKeyInfo.KeySUJapanese,
        TabletKey.KeySE to TabletKeyInfo.KeySEJapanese,
        TabletKey.KeySO to TabletKeyInfo.KeySOJapanese,

        // た-row
        TabletKey.KeyTA to TabletKeyInfo.KeyTAJapanese,
        TabletKey.KeyCHI to TabletKeyInfo.KeyCHIJapanese,
        TabletKey.KeyTSU to TabletKeyInfo.KeyTSUJapanese,
        TabletKey.KeyTE to TabletKeyInfo.KeyTEJapanese,
        TabletKey.KeyTO to TabletKeyInfo.KeyTOJapanese,

        // な-row
        TabletKey.KeyNA to TabletKeyInfo.KeyNAJapanese,
        TabletKey.KeyNI to TabletKeyInfo.KeyNIJapanese,
        TabletKey.KeyNU to TabletKeyInfo.KeyNUJapanese,
        TabletKey.KeyNE to TabletKeyInfo.KeyNEJapanese,
        TabletKey.KeyNO to TabletKeyInfo.KeyNOJapanese,

        // は-row
        TabletKey.KeyHA to TabletKeyInfo.KeyHAJapanese,
        TabletKey.KeyHI to TabletKeyInfo.KeyHIJapanese,
        TabletKey.KeyFU to TabletKeyInfo.KeyFUJapanese,
        TabletKey.KeyHE to TabletKeyInfo.KeyHEJapanese,
        TabletKey.KeyHO to TabletKeyInfo.KeyHOJapanese,

        // ま-row
        TabletKey.KeyMA to TabletKeyInfo.KeyMAJapanese,
        TabletKey.KeyMI to TabletKeyInfo.KeyMIJapanese,
        TabletKey.KeyMU to TabletKeyInfo.KeyMUJapanese,
        TabletKey.KeyME to TabletKeyInfo.KeyMEJapanese,
        TabletKey.KeyMO to TabletKeyInfo.KeyMOJapanese,

        // や-row
        TabletKey.KeyYA to TabletKeyInfo.KeyYAJapanese,
        TabletKey.KeyYU to TabletKeyInfo.KeyYUJapanese,
        TabletKey.KeyYO to TabletKeyInfo.KeyYOJapanese,

        // ら-row
        TabletKey.KeyRA to TabletKeyInfo.KeyRAJapanese,
        TabletKey.KeyRI to TabletKeyInfo.KeyRIJapanese,
        TabletKey.KeyRU to TabletKeyInfo.KeyRUJapanese,
        TabletKey.KeyRE to TabletKeyInfo.KeyREJapanese,
        TabletKey.KeyRO to TabletKeyInfo.KeyROJapanese,

        // わ-row + ん
        TabletKey.KeyWA to TabletKeyInfo.KeyWAJapanese,
        TabletKey.KeyWO to TabletKeyInfo.KeyWOJapanese,
        TabletKey.KeyN to TabletKeyInfo.KeyNNJapanese,

        // 長音符・記号
        TabletKey.KeyMinus to TabletKeyInfo.KeyMINUSJapanese,
        TabletKey.KeyDakuten to TabletKeyInfo.KeyDAKUTENJapanese,
        TabletKey.KeyKagikakko to TabletKeyInfo.KeyKAGIKAKKOJapanese,
        TabletKey.KeyQuestion to TabletKeyInfo.KeyQUESTIONJapanese,
        TabletKey.KeyCaution to TabletKeyInfo.KeyCAUTIONJapanese,
        TabletKey.KeyKuten to TabletKeyInfo.KeyKUTENJapanese,
        TabletKey.KeyTouten to TabletKeyInfo.KeyTOUTENJapanese,

        // Side (non-character) keys
        TabletKey.SideKeySymbol to TabletKeyInfo.Null,
        TabletKey.SideKeyEnglish to TabletKeyInfo.Null,
        TabletKey.SideKeyJapanese to TabletKeyInfo.Null,
        TabletKey.SideKeyDelete to TabletKeyInfo.Null,
        TabletKey.SideKeySpace to TabletKeyInfo.Null,
        TabletKey.SideKeyEnter to TabletKeyInfo.Null,
        TabletKey.SideKeyCursorLeft to TabletKeyInfo.Null,
        TabletKey.SideKeyCursorRight to TabletKeyInfo.Null,
    )
    override val keysJapanese: Set<TabletKey>
        get() = listJapanese.keys

    override fun getTenKeyInfoJapanese(tabletKey: TabletKey): TabletKeyInfo {
        return listJapanese.getOrDefault(tabletKey, TabletKeyInfo.Null)
    }

}