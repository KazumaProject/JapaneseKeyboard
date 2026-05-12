package com.kazumaproject.markdownhelperkeyboard.sumire_special_key

import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.SumireSpecialKeyDirection

object SumireSpecialKeyDefaultActionResolver {
    fun resolve(
        layout: KeyboardLayout,
        keyId: String,
        direction: SumireSpecialKeyDirection
    ): KeyAction? {
        val keyItem = layout.items
            .filterIsInstance<KeyItem>()
            .firstOrNull { it.keyData.keyId == keyId || it.id == keyId }
        val keyData = keyItem?.keyData ?: layout.keys.firstOrNull { it.keyId == keyId }
        if (keyData == null || !keyData.isSpecialKey) return null

        val flickActionMap = keyData.keyId
            ?.takeIf { it.isNotBlank() }
            ?.let { layout.flickKeyMaps[it]?.firstOrNull() }
            ?: layout.flickKeyMaps[keyData.label]?.firstOrNull()

        if (keyData.keyType == KeyType.CROSS_FLICK || flickActionMap != null) {
            val flickAction = flickActionMap?.resolve(direction)
            if (flickAction is FlickAction.Action) return flickAction.action
            if (direction == SumireSpecialKeyDirection.TAP) return keyData.action
            return null
        }

        return if (direction == SumireSpecialKeyDirection.TAP) keyData.action else null
    }

    private fun Map<FlickDirection, FlickAction>.resolve(
        direction: SumireSpecialKeyDirection
    ): FlickAction? {
        return direction.flickDirectionCandidates().firstNotNullOfOrNull { this[it] }
    }

    private fun SumireSpecialKeyDirection.flickDirectionCandidates(): List<FlickDirection> {
        return when (this) {
            SumireSpecialKeyDirection.TAP -> listOf(FlickDirection.TAP)
            SumireSpecialKeyDirection.UP -> listOf(FlickDirection.UP)
            SumireSpecialKeyDirection.RIGHT -> listOf(
                FlickDirection.UP_RIGHT_FAR,
                FlickDirection.UP_RIGHT
            )

            SumireSpecialKeyDirection.DOWN -> listOf(FlickDirection.DOWN)
            SumireSpecialKeyDirection.LEFT -> listOf(
                FlickDirection.UP_LEFT_FAR,
                FlickDirection.UP_LEFT
            )
        }
    }
}

