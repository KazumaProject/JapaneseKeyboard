package com.kazumaproject.core.domain.qwerty

interface QWERTYKeyMapHolder {
    val keysDefault: Set<QWERTYKey>
    fun getKeyInfoDefault(key: QWERTYKey): QWERTYKeyInfo

    val keysNumber: Set<QWERTYKey>
    fun getKeyInfoNumber(key: QWERTYKey): QWERTYKeyInfo

    val keysSymbol: Set<QWERTYKey>
    fun getKeyInfoSymbol(key: QWERTYKey): QWERTYKeyInfo
}

class QWERTYKeyMap : QWERTYKeyMapHolder {

    private val listDefault: Map<QWERTYKey, QWERTYKeyInfo> = mapOf(
        QWERTYKey.QWERTYKeyA to QWERTYKeyInfo.KeyA,
        QWERTYKey.QWERTYKeyB to QWERTYKeyInfo.KeyB,
        QWERTYKey.QWERTYKeyC to QWERTYKeyInfo.KeyC,
        QWERTYKey.QWERTYKeyD to QWERTYKeyInfo.KeyD,
        QWERTYKey.QWERTYKeyE to QWERTYKeyInfo.KeyE,
        QWERTYKey.QWERTYKeyF to QWERTYKeyInfo.KeyF,
        QWERTYKey.QWERTYKeyG to QWERTYKeyInfo.KeyG,
        QWERTYKey.QWERTYKeyH to QWERTYKeyInfo.KeyH,
        QWERTYKey.QWERTYKeyI to QWERTYKeyInfo.KeyI,
        QWERTYKey.QWERTYKeyJ to QWERTYKeyInfo.KeyJ,
        QWERTYKey.QWERTYKeyK to QWERTYKeyInfo.KeyK,
        QWERTYKey.QWERTYKeyL to QWERTYKeyInfo.KeyL,
        QWERTYKey.QWERTYKeyM to QWERTYKeyInfo.KeyM,
        QWERTYKey.QWERTYKeyN to QWERTYKeyInfo.KeyN,
        QWERTYKey.QWERTYKeyO to QWERTYKeyInfo.KeyO,
        QWERTYKey.QWERTYKeyP to QWERTYKeyInfo.KeyP,
        QWERTYKey.QWERTYKeyQ to QWERTYKeyInfo.KeyQ,
        QWERTYKey.QWERTYKeyR to QWERTYKeyInfo.KeyR,
        QWERTYKey.QWERTYKeyS to QWERTYKeyInfo.KeyS,
        QWERTYKey.QWERTYKeyT to QWERTYKeyInfo.KeyT,
        QWERTYKey.QWERTYKeyU to QWERTYKeyInfo.KeyU,
        QWERTYKey.QWERTYKeyV to QWERTYKeyInfo.KeyV,
        QWERTYKey.QWERTYKeyW to QWERTYKeyInfo.KeyW,
        QWERTYKey.QWERTYKeyX to QWERTYKeyInfo.KeyX,
        QWERTYKey.QWERTYKeyY to QWERTYKeyInfo.KeyY,
        QWERTYKey.QWERTYKeyZ to QWERTYKeyInfo.KeyZ,
        QWERTYKey.QWERTYKeyShift to QWERTYKeyInfo.KeyShift,
        QWERTYKey.QWERTYKeyDelete to QWERTYKeyInfo.KeyDelete,
        QWERTYKey.QWERTYKeySwitchDefaultLayout to QWERTYKeyInfo.KeySwitchDefaultLayout,
        QWERTYKey.QWERTYKeySwitchMode to QWERTYKeyInfo.KeySwitchMode,
        QWERTYKey.QWERTYKeySpace to QWERTYKeyInfo.KeySpace,
        QWERTYKey.QWERTYKeyReturn to QWERTYKeyInfo.KeyReturn
    )

    private val listNumber: Map<QWERTYKey, QWERTYKeyInfo> = mapOf(
        // ─── Top row (Q W E R T Y U I O P) ───
        QWERTYKey.QWERTYKeyQ to QWERTYKeyInfo.Key1,
        QWERTYKey.QWERTYKeyW to QWERTYKeyInfo.Key2,
        QWERTYKey.QWERTYKeyE to QWERTYKeyInfo.Key3,
        QWERTYKey.QWERTYKeyR to QWERTYKeyInfo.Key4,
        QWERTYKey.QWERTYKeyT to QWERTYKeyInfo.Key5,
        QWERTYKey.QWERTYKeyY to QWERTYKeyInfo.Key6,
        QWERTYKey.QWERTYKeyU to QWERTYKeyInfo.Key7,
        QWERTYKey.QWERTYKeyI to QWERTYKeyInfo.Key8,
        QWERTYKey.QWERTYKeyO to QWERTYKeyInfo.Key9,
        QWERTYKey.QWERTYKeyP to QWERTYKeyInfo.Key0,

        // ─── Middle row (A S D F G H J K L) ───
        QWERTYKey.QWERTYKeyA to QWERTYKeyInfo.KeyMinus,
        QWERTYKey.QWERTYKeyS to QWERTYKeyInfo.KeySlash,
        QWERTYKey.QWERTYKeyD to QWERTYKeyInfo.KeyColon,
        QWERTYKey.QWERTYKeyF to QWERTYKeyInfo.KeySemicolon,
        QWERTYKey.QWERTYKeyG to QWERTYKeyInfo.KeyParenOpen,
        QWERTYKey.QWERTYKeyH to QWERTYKeyInfo.KeyParenClose,
        QWERTYKey.QWERTYKeyJ to QWERTYKeyInfo.KeyYen,
        QWERTYKey.QWERTYKeyK to QWERTYKeyInfo.KeyAmpersand,
        QWERTYKey.QWERTYKeyAtMark to QWERTYKeyInfo.KeyAtMark,
        QWERTYKey.QWERTYKeyL to QWERTYKeyInfo.KeyQuote,

        // ─── Bottom row (Z X C N M) ───
        QWERTYKey.QWERTYKeyZ to QWERTYKeyInfo.KeyDot,
        QWERTYKey.QWERTYKeyX to QWERTYKeyInfo.KeyComma,
        QWERTYKey.QWERTYKeyC to QWERTYKeyInfo.KeyQuestion,
        QWERTYKey.QWERTYKeyN to QWERTYKeyInfo.KeyExclamation,
        QWERTYKey.QWERTYKeyM to QWERTYKeyInfo.KeyApostrophe,

        // ─── Other function keys ───
        QWERTYKey.QWERTYKeyShift to QWERTYKeyInfo.KeyShift,
        QWERTYKey.QWERTYKeyDelete to QWERTYKeyInfo.KeyDelete,
        QWERTYKey.QWERTYKeySwitchDefaultLayout to QWERTYKeyInfo.KeySwitchDefaultLayout,
        QWERTYKey.QWERTYKeySwitchMode to QWERTYKeyInfo.KeySwitchMode,
        QWERTYKey.QWERTYKeySpace to QWERTYKeyInfo.KeySpace,
        QWERTYKey.QWERTYKeyReturn to QWERTYKeyInfo.KeyReturn
    )

    private val listSymbol: Map<QWERTYKey, QWERTYKeyInfo> = mapOf(
        // ─── Top row (Q W E R T Y U I O P) ───
        QWERTYKey.QWERTYKeyQ to QWERTYKeyInfo.KeyBracketOpen,    // '['
        QWERTYKey.QWERTYKeyW to QWERTYKeyInfo.KeyBracketClose,   // ']'
        QWERTYKey.QWERTYKeyE to QWERTYKeyInfo.KeyBraceOpen,      // '{'
        QWERTYKey.QWERTYKeyR to QWERTYKeyInfo.KeyBraceClose,     // '}'
        QWERTYKey.QWERTYKeyT to QWERTYKeyInfo.KeyHash,           // '#'
        QWERTYKey.QWERTYKeyY to QWERTYKeyInfo.KeyPercent,        // '%'
        QWERTYKey.QWERTYKeyU to QWERTYKeyInfo.KeyCaret,          // '^'
        QWERTYKey.QWERTYKeyI to QWERTYKeyInfo.KeyAsterisk,       // '*'
        QWERTYKey.QWERTYKeyO to QWERTYKeyInfo.KeyPlus,
        QWERTYKey.QWERTYKeyP to QWERTYKeyInfo.KeyEqual,          // '='

        // ─── Middle row (A S D F G H J K L) ───
        QWERTYKey.QWERTYKeyA to QWERTYKeyInfo.KeyUnderscore,     // '_'
        QWERTYKey.QWERTYKeyS to QWERTYKeyInfo.KeySlash,          // '/'
        QWERTYKey.QWERTYKeyD to QWERTYKeyInfo.KeyBackslash,      // '\'
        QWERTYKey.QWERTYKeyF to QWERTYKeyInfo.KeyTilde,          // '~'
        QWERTYKey.QWERTYKeyG to QWERTYKeyInfo.KeyLessThan,       // '<'
        QWERTYKey.QWERTYKeyH to QWERTYKeyInfo.KeyGreaterThan,    // '>'
        QWERTYKey.QWERTYKeyJ to QWERTYKeyInfo.KeyDollar,         // '$'
        QWERTYKey.QWERTYKeyK to QWERTYKeyInfo.KeyEuro,           // '€'
        QWERTYKey.QWERTYKeyAtMark to QWERTYKeyInfo.KeyPound,          // '£'
        QWERTYKey.QWERTYKeyL to QWERTYKeyInfo.KeyMiddleDot,

        // ─── Bottom row (Z X C N M) ───
        QWERTYKey.QWERTYKeyZ to QWERTYKeyInfo.KeyDot,
        QWERTYKey.QWERTYKeyX to QWERTYKeyInfo.KeyComma,
        QWERTYKey.QWERTYKeyC to QWERTYKeyInfo.KeyQuestion,
        QWERTYKey.QWERTYKeyN to QWERTYKeyInfo.KeyExclamation,
        QWERTYKey.QWERTYKeyM to QWERTYKeyInfo.KeyApostrophe,

        // ─── Other function keys ───
        QWERTYKey.QWERTYKeyShift to QWERTYKeyInfo.KeyShift,
        QWERTYKey.QWERTYKeyDelete to QWERTYKeyInfo.KeyDelete,
        QWERTYKey.QWERTYKeySwitchDefaultLayout to QWERTYKeyInfo.KeySwitchDefaultLayout,
        QWERTYKey.QWERTYKeySwitchMode to QWERTYKeyInfo.KeySwitchMode,
        QWERTYKey.QWERTYKeySpace to QWERTYKeyInfo.KeySpace,
        QWERTYKey.QWERTYKeyReturn to QWERTYKeyInfo.KeyReturn
    )

    override val keysDefault: Set<QWERTYKey>
        get() = listDefault.keys

    override fun getKeyInfoDefault(key: QWERTYKey): QWERTYKeyInfo {
        return listDefault.getOrDefault(key, QWERTYKeyInfo.Null)
    }

    override val keysNumber: Set<QWERTYKey>
        get() = listNumber.keys

    override fun getKeyInfoNumber(key: QWERTYKey): QWERTYKeyInfo {
        return listNumber.getOrDefault(key, QWERTYKeyInfo.Null)
    }

    override val keysSymbol: Set<QWERTYKey>
        get() = listSymbol.keys

    override fun getKeyInfoSymbol(key: QWERTYKey): QWERTYKeyInfo {
        return listSymbol.getOrDefault(key, QWERTYKeyInfo.Null)
    }

}
