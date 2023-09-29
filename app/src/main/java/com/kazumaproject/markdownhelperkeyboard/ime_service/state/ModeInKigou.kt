package com.kazumaproject.markdownhelperkeyboard.ime_service.state

sealed class ModeInKigou{

    object Null: ModeInKigou()
    object Emoji: ModeInKigou()
    object Kaomoji: ModeInKigou()
}
