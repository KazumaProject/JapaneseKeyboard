package com.kazumaproject.custom_keyboard.data

fun buildSumireSpecialKeyDisplayActionMap(
    keyData: KeyData,
    baseMap: Map<FlickDirection, FlickAction>,
    resolve: ((KeyData, SumireSpecialKeyDirection) -> ResolvedSumireSpecialKeyAction)?
): Map<FlickDirection, FlickAction> {
    if (!keyData.isSpecialKey || keyData.keyId.isNullOrBlank() || resolve == null) return baseMap

    val displayMap = baseMap.mapValues { (direction, action) ->
        val sumireDirection = direction.toSumireSpecialKeyDirectionOrNull()
            ?: return@mapValues action
        when (val resolved = resolve(keyData, sumireDirection)) {
            is ResolvedSumireSpecialKeyAction.Action -> FlickAction.Action(resolved.action)
            is ResolvedSumireSpecialKeyAction.InputText -> FlickAction.Action(
                KeyAction.Text(resolved.text),
                label = resolved.text
            )

            else -> action
        }
    }.toMutableMap()

    SumireSpecialKeyDirection.entries.forEach { sumireDirection ->
        val flickDirection = sumireDirection.toDisplayFlickDirection(displayMap.keys)
        if (flickDirection in displayMap) return@forEach
        when (val resolved = resolve(keyData, sumireDirection)) {
            is ResolvedSumireSpecialKeyAction.Action -> {
                displayMap[flickDirection] = FlickAction.Action(resolved.action)
            }

            is ResolvedSumireSpecialKeyAction.InputText -> {
                displayMap[flickDirection] = FlickAction.Action(
                    KeyAction.Text(resolved.text),
                    label = resolved.text
                )
            }

            else -> Unit
        }
    }
    return displayMap
}

private fun SumireSpecialKeyDirection.toDisplayFlickDirection(
    existingDirections: Set<FlickDirection>
): FlickDirection {
    return when (this) {
        SumireSpecialKeyDirection.TAP -> FlickDirection.TAP
        SumireSpecialKeyDirection.UP -> FlickDirection.UP
        SumireSpecialKeyDirection.RIGHT ->
            if (FlickDirection.UP_RIGHT in existingDirections &&
                FlickDirection.UP_RIGHT_FAR !in existingDirections
            ) {
                FlickDirection.UP_RIGHT
            } else {
                FlickDirection.UP_RIGHT_FAR
            }

        SumireSpecialKeyDirection.DOWN -> FlickDirection.DOWN
        SumireSpecialKeyDirection.LEFT ->
            if (FlickDirection.UP_LEFT in existingDirections &&
                FlickDirection.UP_LEFT_FAR !in existingDirections
            ) {
                FlickDirection.UP_LEFT
            } else {
                FlickDirection.UP_LEFT_FAR
            }
    }
}

