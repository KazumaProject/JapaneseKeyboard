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
        val flickDirection = sumireDirection.toDisplayFlickDirection()
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

internal val DYNAMIC_SUMIRE_SPECIAL_KEY_TAP_OVERRIDE_DISPLAY_KEY_IDS: Set<String> = setOf(
    "enter_key",
    "space_convert_key",
    "katakana_toggle_key"
)

fun KeyData.applyTapOverrideDisplayForDynamicSumireSpecialKey(
    displayActions: List<DisplayAction>,
    resolve: (KeyData, SumireSpecialKeyDirection) -> ResolvedSumireSpecialKeyAction
): KeyData {
    if (!isSpecialKey) return this
    val keyId = keyId?.takeIf { it.isNotBlank() } ?: return this
    if (keyId !in DYNAMIC_SUMIRE_SPECIAL_KEY_TAP_OVERRIDE_DISPLAY_KEY_IDS) return this

    val resolved = resolve(this, SumireSpecialKeyDirection.TAP)
    val action = (resolved as? ResolvedSumireSpecialKeyAction.Action)?.action ?: return this
    val displayAction = displayActions.firstOrNull { it.action == action }

    return copy(
        action = action,
        label = displayAction?.displayName ?: label,
        drawableResId = displayAction?.iconResId ?: drawableResId
    )
}

private fun SumireSpecialKeyDirection.toDisplayFlickDirection(): FlickDirection {
    return when (this) {
        SumireSpecialKeyDirection.TAP -> FlickDirection.TAP
        SumireSpecialKeyDirection.UP -> FlickDirection.UP
        SumireSpecialKeyDirection.RIGHT -> FlickDirection.UP_RIGHT_FAR
        SumireSpecialKeyDirection.DOWN -> FlickDirection.DOWN
        SumireSpecialKeyDirection.LEFT -> FlickDirection.UP_LEFT_FAR
    }
}

/**
 * Sumire 特殊キー (isSpecialKey=true && keyId が非空) で、layout 構築時の static な base flick
 * map を attach 時の現在の [KeyData.action] / [KeyData.label] / [KeyData.drawableResId] に
 * 合わせて refresh するためのヘルパー。
 *
 * `updateDynamicKey` などで keyData.action が更新されると、layout.flickKeyMaps[keyId] の
 * 内容は古いままになりうる。base flick map の TAP entry を keyData.action に揃えることで、
 * dynamicStates が変わっても CROSS_FLICK 経路の Tap fallback が正しい action を返す。
 *
 * 対象外のキー (isSpecialKey=false / keyId 空) はそのまま返す。
 */
fun Map<FlickDirection, FlickAction>.refreshSumireSpecialKeyTap(
    keyData: KeyData
): Map<FlickDirection, FlickAction> {
    if (!keyData.isSpecialKey) return this
    if (keyData.keyId.isNullOrBlank()) return this
    val tapAction = keyData.action ?: return this

    val existingTap = this[FlickDirection.TAP]
    val existingTapAction = (existingTap as? FlickAction.Action)?.action
    val existingLabel = (existingTap as? FlickAction.Action)?.label
    val existingDrawable = (existingTap as? FlickAction.Action)?.drawableResId

    val resolvedLabel = keyData.label.takeIf { it.isNotBlank() } ?: existingLabel
    val resolvedDrawable = keyData.drawableResId ?: existingDrawable

    if (existingTapAction == tapAction &&
        existingLabel == resolvedLabel &&
        existingDrawable == resolvedDrawable
    ) {
        return this
    }

    return this.toMutableMap().apply {
        this[FlickDirection.TAP] = FlickAction.Action(
            action = tapAction,
            label = resolvedLabel,
            drawableResId = resolvedDrawable
        )
    }
}
