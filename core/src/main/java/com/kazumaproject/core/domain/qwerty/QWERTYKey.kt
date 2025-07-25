package com.kazumaproject.core.domain.qwerty

sealed class QWERTYKey {
    data object QWERTYKeyNotSelect : QWERTYKey()
    data object QWERTYKeyA : QWERTYKey()
    data object QWERTYKeyB : QWERTYKey()
    data object QWERTYKeyC : QWERTYKey()
    data object QWERTYKeyD : QWERTYKey()
    data object QWERTYKeyE : QWERTYKey()
    data object QWERTYKeyF : QWERTYKey()
    data object QWERTYKeyG : QWERTYKey()
    data object QWERTYKeyH : QWERTYKey()
    data object QWERTYKeyI : QWERTYKey()
    data object QWERTYKeyJ : QWERTYKey()
    data object QWERTYKeyK : QWERTYKey()
    data object QWERTYKeyL : QWERTYKey()
    data object QWERTYKeyM : QWERTYKey()
    data object QWERTYKeyN : QWERTYKey()
    data object QWERTYKeyO : QWERTYKey()
    data object QWERTYKeyP : QWERTYKey()
    data object QWERTYKeyQ : QWERTYKey()
    data object QWERTYKeyR : QWERTYKey()
    data object QWERTYKeyS : QWERTYKey()
    data object QWERTYKeyT : QWERTYKey()
    data object QWERTYKeyU : QWERTYKey()
    data object QWERTYKeyV : QWERTYKey()
    data object QWERTYKeyW : QWERTYKey()
    data object QWERTYKeyX : QWERTYKey()
    data object QWERTYKeyY : QWERTYKey()
    data object QWERTYKeyZ : QWERTYKey()
    data object QWERTYKeyAtMark : QWERTYKey()
    data object QWERTYKeyShift : QWERTYKey()
    data object QWERTYKeyDelete : QWERTYKey()
    data object QWERTYKeySwitchDefaultLayout : QWERTYKey()
    data object QWERTYKeySwitchMode : QWERTYKey()
    data object QWERTYKeySpace : QWERTYKey()
    data object QWERTYKeyReturn : QWERTYKey()

    data object QWERTYKeyCursorLeft : QWERTYKey()
    data object QWERTYKeyCursorRight : QWERTYKey()
    data object QWERTYKeyCursorUp : QWERTYKey()
    data object QWERTYKeyCursorDown : QWERTYKey()
}
