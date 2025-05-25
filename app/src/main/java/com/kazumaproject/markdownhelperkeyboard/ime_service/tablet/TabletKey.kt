package com.kazumaproject.markdownhelperkeyboard.ime_service.tablet

sealed class TabletKey {
    data object NotSelected : TabletKey()

    // あ row
    data object KeyA : TabletKey()
    data object KeyI : TabletKey()
    data object KeyU : TabletKey()
    data object KeyE : TabletKey()
    data object KeyO : TabletKey()

    // か row
    data object KeyKA : TabletKey()
    data object KeyKI : TabletKey()
    data object KeyKU : TabletKey()
    data object KeyKE : TabletKey()
    data object KeyKO : TabletKey()

    // さ row
    data object KeySA : TabletKey()
    data object KeySHI : TabletKey()
    data object KeySU : TabletKey()
    data object KeySE : TabletKey()
    data object KeySO : TabletKey()

    // た row
    data object KeyTA : TabletKey()
    data object KeyCHI : TabletKey()
    data object KeyTSU : TabletKey()
    data object KeyTE : TabletKey()
    data object KeyTO : TabletKey()

    // な row
    data object KeyNA : TabletKey()
    data object KeyNI : TabletKey()
    data object KeyNU : TabletKey()
    data object KeyNE : TabletKey()
    data object KeyNO : TabletKey()

    // は row
    data object KeyHA : TabletKey()
    data object KeyHI : TabletKey()
    data object KeyFU : TabletKey()
    data object KeyHE : TabletKey()
    data object KeyHO : TabletKey()

    // ま row
    data object KeyMA : TabletKey()
    data object KeyMI : TabletKey()
    data object KeyMU : TabletKey()
    data object KeyME : TabletKey()
    data object KeyMO : TabletKey()

    // や row
    data object KeyYA : TabletKey()
    data object KeyYU : TabletKey()
    data object KeyYO : TabletKey()

    data object KeySPACE1 : TabletKey()
    data object KeySPACE2 : TabletKey()

    // ら row
    data object KeyRA : TabletKey()
    data object KeyRI : TabletKey()
    data object KeyRU : TabletKey()
    data object KeyRE : TabletKey()
    data object KeyRO : TabletKey()

    // わ row + ん
    data object KeyWA : TabletKey()
    data object KeyWO : TabletKey()
    data object KeyN : TabletKey()

    data object KeyMinus : TabletKey()

    // Modifiers & punctuation
    data object KeyDakuten : TabletKey() // ゛゜小文字
    data object KeyTouten : TabletKey() // 、
    data object KeyKuten : TabletKey() // 。
    data object KeyKagikakko : TabletKey()
    data object KeyQuestion : TabletKey()
    data object KeyCaution : TabletKey()

    // Side-row keys
    data object SideKeySymbol : TabletKey()
    data object SideKeyEnglish : TabletKey()
    data object SideKeyJapanese : TabletKey()
    data object SideKeyDelete : TabletKey()
    data object SideKeySpace : TabletKey()
    data object SideKeyEnter : TabletKey()
    data object SideKeyCursorLeft : TabletKey()
    data object SideKeyCursorRight : TabletKey()
}