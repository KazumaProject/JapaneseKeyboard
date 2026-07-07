package com.kazumaproject.custom_keyboard.data

enum class SumireSpecialKeyDirection {
    TAP,
    UP,
    RIGHT,
    DOWN,
    LEFT
}

sealed class ResolvedSumireSpecialKeyAction {
    data object Default : ResolvedSumireSpecialKeyAction()
    data object None : ResolvedSumireSpecialKeyAction()
    data class Action(val action: KeyAction) : ResolvedSumireSpecialKeyAction()
    data class InputText(val text: String) : ResolvedSumireSpecialKeyAction()
}

