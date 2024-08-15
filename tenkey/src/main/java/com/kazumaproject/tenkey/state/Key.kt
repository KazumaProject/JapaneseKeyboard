package com.kazumaproject.tenkey.state

sealed class Key {

    object NotSelected: Key()
    object KeyA: Key()
    object KeyKA: Key()
    object KeySA: Key()
    object KeyTA: Key()
    object KeyNA: Key()
    object KeyHA: Key()
    object KeyMA: Key()
    object KeyYA: Key()
    object KeyRA: Key()
    object KeyDakutenSmall: Key()
    object KeyWA: Key()
    object KeyKutouten: Key()
    object SideKeyPreviousChar: Key()
    object SideKeySymbol: Key()
    object SideKeyInputMode: Key()
    object SideKeyDelete: Key()
    object SideKeySpace: Key()
    object SideKeyEnter: Key()
    object SideKeyCursorLeft: Key()
    object SideKeyCursorRight: Key()

}