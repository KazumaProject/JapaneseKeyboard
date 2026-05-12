package com.kazumaproject.custom_keyboard.data

data class SumireSpecialKeyRuntimeDispatchResult(
    val sumireDirection: SumireSpecialKeyDirection?,
    val resolved: ResolvedSumireSpecialKeyAction,
    val handled: Boolean,
    val dispatchedAction: KeyAction?
)

fun dispatchSumireSpecialKeyRuntimeAction(
    keyData: KeyData,
    flickDirection: FlickDirection,
    fallbackAction: KeyAction?,
    isFlick: Boolean,
    resolve: (KeyData, SumireSpecialKeyDirection) -> ResolvedSumireSpecialKeyAction,
    dispatch: (KeyAction, Boolean) -> Unit
): SumireSpecialKeyRuntimeDispatchResult {
    val sumireDirection = flickDirection.toSumireSpecialKeyDirectionOrNull()
    if (sumireDirection == null) {
        fallbackAction?.let { dispatch(it, isFlick) }
        return SumireSpecialKeyRuntimeDispatchResult(
            sumireDirection = null,
            resolved = ResolvedSumireSpecialKeyAction.Default,
            handled = false,
            dispatchedAction = fallbackAction
        )
    }

    if (!keyData.isSpecialKey || keyData.keyId.isNullOrBlank()) {
        fallbackAction?.let { dispatch(it, isFlick) }
        return SumireSpecialKeyRuntimeDispatchResult(
            sumireDirection = sumireDirection,
            resolved = ResolvedSumireSpecialKeyAction.Default,
            handled = false,
            dispatchedAction = fallbackAction
        )
    }

    val resolved = resolve(keyData, sumireDirection)
    var dispatchedAction: KeyAction? = null
    val handled = dispatchResolvedSumireSpecialKeyAction(resolved, isFlick) { action, actionIsFlick ->
        dispatchedAction = action
        dispatch(action, actionIsFlick)
    }

    if (!handled) {
        fallbackAction?.let {
            dispatchedAction = it
            dispatch(it, isFlick)
        }
    }

    return SumireSpecialKeyRuntimeDispatchResult(
        sumireDirection = sumireDirection,
        resolved = resolved,
        handled = handled,
        dispatchedAction = dispatchedAction
    )
}
