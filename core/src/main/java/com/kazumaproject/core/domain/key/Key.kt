package com.kazumaproject.core.domain.key

sealed class Key {
    // Original keys from TenKey (keep as is)
    data object NotSelected : Key()
    data object KeyA : Key()
    data object KeyKA : Key()
    data object KeySA : Key()
    data object KeyTA : Key()
    data object KeyNA : Key()
    data object KeyHA : Key()
    data object KeyMA : Key()
    data object KeyYA : Key()
    data object KeyRA : Key()
    data object KeyDakutenSmall : Key()
    data object KeyWA : Key()
    data object KeyKutouten : Key()
    data object SideKeyPreviousChar : Key()
    data object SideKeySymbol : Key()
    data object SideKeyInputMode : Key()
    data object SideKeyDelete : Key()
    data object SideKeySpace : Key()
    data object SideKeyEnter : Key()
    data object SideKeyCursorLeft : Key()
    data object SideKeyCursorRight : Key()

    // Additional keys from TabletKey (not duplicated)
    data object KeyI : Key()
    data object KeyU : Key()
    data object KeyE : Key()
    data object KeyO : Key()

    data object KeyKI : Key()
    data object KeyKU : Key()
    data object KeyKE : Key()
    data object KeyKO : Key()

    data object KeySHI : Key()
    data object KeySU : Key()
    data object KeySE : Key()
    data object KeySO : Key()

    data object KeyCHI : Key()
    data object KeyTSU : Key()
    data object KeyTE : Key()
    data object KeyTO : Key()

    data object KeyNI : Key()
    data object KeyNU : Key()
    data object KeyNE : Key()
    data object KeyNO : Key()

    data object KeyHI : Key()
    data object KeyFU : Key()
    data object KeyHE : Key()
    data object KeyHO : Key()

    data object KeyMI : Key()
    data object KeyMU : Key()
    data object KeyME : Key()
    data object KeyMO : Key()

    data object KeyYU : Key()
    data object KeyYO : Key()

    data object KeyRI : Key()
    data object KeyRU : Key()
    data object KeyRE : Key()
    data object KeyRO : Key()

    data object KeyWO : Key()
    data object KeyN : Key()

    data object KeySPACE1 : Key()
    data object KeySPACE2 : Key()

    data object KeyMinus : Key()
    data object KeyTouten : Key()
    data object KeyKuten : Key()
    data object KeyKagikakko : Key()
    data object KeyQuestion : Key()
    data object KeyCaution : Key()
}