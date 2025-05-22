package com.kazumaproject.tenkey.state

sealed class Key {

    data object NotSelected: Key()
    data object KeyA: Key()
    data object KeyKA: Key()
    data object KeySA: Key()
    data object KeyTA: Key()
    data object KeyNA: Key()
    data object KeyHA: Key()
    data object KeyMA: Key()
    data object KeyYA: Key()
    data object KeyRA: Key()
    data object KeyDakutenSmall: Key()
    data object KeyWA: Key()
    data object KeyKutouten: Key()
    data object SideKeyPreviousChar: Key()
    data object SideKeySymbol: Key()
    data object SideKeyInputMode: Key()
    data object SideKeyDelete: Key()
    data object SideKeySpace: Key()
    data object SideKeyEnter: Key()
    data object SideKeyCursorLeft: Key()
    data object SideKeyCursorRight: Key()

}