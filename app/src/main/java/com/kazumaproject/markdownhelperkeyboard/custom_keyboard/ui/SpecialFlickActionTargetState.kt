package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.DisplayActionUi
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.SpecialFlickMappingItem

internal fun DisplayActionUi.matchesAction(action: KeyAction): Boolean {
    return when (action) {
        is KeyAction.MoveToCustomKeyboard -> this.action is KeyAction.MoveToCustomKeyboard
        else -> this.action == action
    }
}

internal fun List<DisplayActionUi>.displayActionFor(action: KeyAction): DisplayActionUi? =
    firstOrNull { it.matchesAction(action) }

internal fun resolveSpecialFlickSelectedAction(
    selectedAction: KeyAction?,
    currentAction: KeyAction?,
    selectedTargetStableId: String?,
    validTargetStableIds: Set<String>
): KeyAction? {
    if (selectedAction !is KeyAction.MoveToCustomKeyboard) return selectedAction

    val stableId = (currentAction as? KeyAction.MoveToCustomKeyboard)
        ?.stableId
        ?.takeIf { it in validTargetStableIds }
        ?: selectedTargetStableId
            ?.takeIf { it in validTargetStableIds }
        ?: validTargetStableIds.firstOrNull()

    return KeyAction.MoveToCustomKeyboard(stableId.orEmpty())
}

internal fun List<SpecialFlickMappingItem>.withActionForDirection(
    direction: FlickDirection,
    action: KeyAction?
): List<SpecialFlickMappingItem> =
    map { item ->
        if (item.direction == direction) item.copy(action = action) else item
    }

internal fun List<SpecialFlickMappingItem>.withMoveToCustomKeyboardTargetForDirection(
    direction: FlickDirection,
    stableId: String,
    validTargetStableIds: Set<String>
): List<SpecialFlickMappingItem> {
    if (stableId !in validTargetStableIds) return this

    return map { item ->
        if (item.direction == direction && item.action is KeyAction.MoveToCustomKeyboard) {
            item.copy(action = KeyAction.MoveToCustomKeyboard(stableId))
        } else {
            item
        }
    }
}

internal fun KeyAction?.isValidMoveToCustomKeyboardTarget(validTargetStableIds: Set<String>): Boolean {
    return this !is KeyAction.MoveToCustomKeyboard ||
            stableId.isNotBlank() && stableId in validTargetStableIds
}

internal fun List<SpecialFlickMappingItem>.hasOnlyValidMoveToCustomKeyboardTargets(
    validTargetStableIds: Set<String>
): Boolean =
    all { it.action.isValidMoveToCustomKeyboardTarget(validTargetStableIds) }

internal fun List<SpecialFlickMappingItem>.moveToCustomKeyboardStableIdForDirection(
    direction: FlickDirection
): String? =
    (firstOrNull { it.direction == direction }?.action as? KeyAction.MoveToCustomKeyboard)?.stableId
