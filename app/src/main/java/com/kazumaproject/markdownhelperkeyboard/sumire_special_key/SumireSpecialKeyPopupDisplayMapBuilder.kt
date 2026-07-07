package com.kazumaproject.markdownhelperkeyboard.sumire_special_key

import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.SumireSpecialKeyDirection
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyActionOverrideEntity

object SumireSpecialKeyPopupDisplayMapBuilder {
    fun build(
        baseMap: Map<FlickDirection, FlickAction>,
        layoutType: String,
        inputMode: String,
        keyData: KeyData,
        overrides: List<SumireSpecialKeyActionOverrideEntity>,
        displayMetadata: List<SumireSpecialKeyActionDisplayMetadata>
    ): Map<FlickDirection, FlickAction> {
        if (!keyData.isSpecialKey) return baseMap
        val keyId = keyData.keyId?.takeIf { it.isNotBlank() } ?: return baseMap
        val metadataByActionString = displayMetadata.associateBy {
            KeyActionMapper.fromKeyAction(it.action)
        }
        val applicable = overrides.filter {
            it.layoutType == layoutType &&
                    it.inputMode == inputMode &&
                    it.keyId == keyId &&
                    it.overrideType == SumireSpecialKeyOverrideType.KEY_ACTION.name
        }
        if (applicable.isEmpty()) return baseMap

        val result = baseMap.toMutableMap()
        applicable.forEach { override ->
            val direction = runCatching {
                SumireSpecialKeyDirection.valueOf(override.direction)
            }.getOrNull() ?: return@forEach
            val action = KeyActionMapper.toKeyAction(override.actionString) ?: return@forEach
            val metadata = metadataByActionString[override.actionString]
            direction.flickDirectionTargets(result.keys).forEach { flickDirection ->
                result[flickDirection] = FlickAction.Action(
                    action = action,
                    label = metadata?.displayName,
                    drawableResId = metadata?.iconResId
                )
            }
        }
        return result
    }

    private fun SumireSpecialKeyDirection.flickDirectionTargets(
        existingKeys: Set<FlickDirection>
    ): List<FlickDirection> {
        return when (this) {
            SumireSpecialKeyDirection.TAP -> listOf(FlickDirection.TAP)
            SumireSpecialKeyDirection.UP -> listOf(FlickDirection.UP)
            SumireSpecialKeyDirection.RIGHT -> buildList {
                add(FlickDirection.UP_RIGHT_FAR)
                if (FlickDirection.UP_RIGHT in existingKeys) add(FlickDirection.UP_RIGHT)
            }

            SumireSpecialKeyDirection.DOWN -> listOf(FlickDirection.DOWN)
            SumireSpecialKeyDirection.LEFT -> buildList {
                add(FlickDirection.UP_LEFT_FAR)
                if (FlickDirection.UP_LEFT in existingKeys) add(FlickDirection.UP_LEFT)
            }
        }
    }
}
