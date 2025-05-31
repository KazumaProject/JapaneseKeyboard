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
        Key.KeyKuten to KeyInfo.KeyKUTENJapanese, // 、
        Key.KeyTouten to KeyInfo.KeyTOUTENJapanese,  // 。
        Key.KeyMinus to KeyInfo.KeyMINUSJapanese,    // ー
        Key.KeyDakutenSmall to KeyInfo.KeyDAKUTENJapanese, // (No char)
        Key.KeyKagikakko to KeyInfo.KeyKAGIKAKKOJapanese, // 「
        Key.KeyQuestion to KeyInfo.KeyQUESTIONJapanese, // ？
        Key.KeyCaution to KeyInfo.KeyCAUTIONJapanese,   // ！
    )

    private val listEnglish: Map<Key, KeyInfo> = mapOf(
        Key.KeyKagikakko to KeyInfo.KeyAEnglish,
        Key.KeyQuestion to KeyInfo.KeyKEnglish,
        Key.KeyCaution to KeyInfo.KeyUEnglish,
        Key.KeyTouten to KeyInfo.KeyMinus,
        Key.KeyKuten to KeyInfo.KeyShiftEnglish,
        Key.KeyWA to KeyInfo.KeyBEnglish,
        Key.KeyWO to KeyInfo.KeyLEnglish,
        Key.KeyN to KeyInfo.KeyVEnglish,
        Key.KeyMinus to KeyInfo.KeyUnderBar,
        Key.KeyYA to KeyInfo.KeyCEnglish,
        Key.KeySPACE1 to KeyInfo.KeyMEnglish,
        Key.KeyYU to KeyInfo.KeyWEnglish,
        Key.KeySPACE2 to KeyInfo.KeySlash,
        Key.KeyYO to KeyInfo.KeySmallEnglish,
        Key.KeyMA to KeyInfo.KeyDEnglish,
        Key.KeyMI to KeyInfo.KeyNEnglish,
        Key.KeyMU to KeyInfo.KeyXEnglish,
        Key.KeyME to KeyInfo.KeyColon,
        Key.KeyHA to KeyInfo.KeyEEnglish,
        Key.KeyHI to KeyInfo.KeyOEnglish,
        Key.KeyFU to KeyInfo.KeyYEnglish,
        Key.KeyHE to KeyInfo.KeyAnd,
        Key.KeyHO to KeyInfo.KeyComma,
        Key.KeyNA to KeyInfo.KeyFEnglish,
        Key.KeyNI to KeyInfo.KeyPEnglish,
        Key.KeyNU to KeyInfo.KeyZEnglish,
        Key.KeyNE to KeyInfo.KeyAtMark,
        Key.KeyNO to KeyInfo.KeyPeriod,
        Key.KeyTA to KeyInfo.KeyGEnglish,
        Key.KeyCHI to KeyInfo.KeyQEnglish,
        Key.KeyTSU to KeyInfo.KeyLeftBracket,
        Key.KeyTE to KeyInfo.KeySharp,
        Key.KeyTO to KeyInfo.KeyCaution,

        Key.KeySA to KeyInfo.KeyHEnglish,
        Key.KeySHI to KeyInfo.KeyREnglish,
        Key.KeySU to KeyInfo.KeyRightBracket,
        Key.KeySE to KeyInfo.KeyAsterisk,
        Key.KeySO to KeyInfo.KeyQuestion,

        Key.KeyKA to KeyInfo.KeyIEnglish,
        Key.KeyKI to KeyInfo.KeySEnglish,
        Key.KeyKU to KeyInfo.KeySquareLeftBracket,
        Key.KeyKE to KeyInfo.KeyCaret,

        Key.KeyA to KeyInfo.KeyJEnglish,
        Key.KeyI to KeyInfo.KeyTEnglish,
        Key.KeyU to KeyInfo.KeySquareRightBracket,
        Key.KeyE to KeyInfo.KeyBackQuote,
        Key.KeyO to KeyInfo.KeyZenkakuEnglish,

        )

    private val listNumber: Map<Key, KeyInfo> = mapOf(
        Key.KeyKagikakko to KeyInfo.KeyYearNumber,
        Key.KeyQuestion to KeyInfo.KeyMultipleNumber,
        Key.KeyCaution to KeyInfo.KeyMusicNoteNumber,
        Key.KeyTouten to KeyInfo.KeyRightArrowNumber,
        Key.KeyKuten to KeyInfo.KeyCommand,


        Key.KeyRA to KeyInfo.KeyMonthNumber,
        Key.KeyRI to KeyInfo.KeyDivideNumber,
        Key.KeyRU to KeyInfo.KeyStarNumber,
        Key.KeyRE to KeyInfo.KeyTildaNumber,

        Key.KeyHA to KeyInfo.KeyDayNumber,
        Key.KeyHI to KeyInfo.KeyPlusNumber,
        Key.KeyFU to KeyInfo.KeyPercentNumber,
        Key.KeyHE to KeyInfo.KeyMiddleDotNumber,
        Key.KeyHO to KeyInfo.KeySlashNumber,

        Key.KeyNA to KeyInfo.KeyHourNumber,
        Key.KeyNI to KeyInfo.KeyMinusNumber,
        Key.KeyNU to KeyInfo.KeyYenNumber,
        Key.KeyNE to KeyInfo.KeyEllipsisNumber,
        Key.KeyNO to KeyInfo.KeyLeftParenNumber,

        Key.KeyTA to KeyInfo.KeyMinuteNumber,
        Key.KeyCHI to KeyInfo.KeyEqualNumber,
        Key.KeyTSU to KeyInfo.KeyPostMarkNumber,
        Key.KeyTE to KeyInfo.KeyCircleNumber,
        Key.KeyTO to KeyInfo.KeyRightParenNumber,

        Key.KeySA to KeyInfo.Key1NumberTablet,
        Key.KeySHI to KeyInfo.Key4NumberTablet,
        Key.KeySU to KeyInfo.Key7NumberTablet,
        Key.KeySE to KeyInfo.KeyCommaNumber,
        Key.KeySO to KeyInfo.KeyColonNumber,

        Key.KeyKA to KeyInfo.Key2NumberTablet,
        Key.KeyKI to KeyInfo.Key5NumberTablet,
        Key.KeyKU to KeyInfo.Key8NumberTablet,
        Key.KeyKE to KeyInfo.Key0NumberTablet,

        Key.KeyA to KeyInfo.Key3NumberTablet,
        Key.KeyI to KeyInfo.Key6NumberTablet,
        Key.KeyU to KeyInfo.Key9NumberTablet,
        Key.KeyE to KeyInfo.KeyPeriodNumber,
        Key.KeyO to KeyInfo.KeyZenkakuNumber,
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