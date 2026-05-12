package com.kazumaproject.custom_keyboard.data

fun dispatchResolvedSumireSpecialKeyAction(
    resolved: ResolvedSumireSpecialKeyAction,
    isFlick: Boolean,
    dispatch: (KeyAction, Boolean) -> Unit
): Boolean {
    return when (resolved) {
        ResolvedSumireSpecialKeyAction.Default -> false
        ResolvedSumireSpecialKeyAction.None -> true
        is ResolvedSumireSpecialKeyAction.Action -> {
            dispatch(resolved.action, isFlick)
            true
        }

        is ResolvedSumireSpecialKeyAction.InputText -> {
            dispatch(KeyAction.Text(resolved.text), isFlick)
            true
        }
    }
}
