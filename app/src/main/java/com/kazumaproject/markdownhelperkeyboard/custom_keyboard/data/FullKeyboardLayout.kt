package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data

import androidx.room.Embedded
import androidx.room.Relation

/**
 * CustomKeyboardLayout とそれに関連するすべての KeyWithFlicks をまとめて保持するクラス
 * これが、一つの完成したキーボードレイアウトのデータ構造となる
 */
data class FullKeyboardLayout(
    @Embedded val layout: CustomKeyboardLayout,
    @Relation(
        parentColumn = "layoutId",
        entity = KeyDefinition::class,
        entityColumn = "ownerLayoutId"
    )
    val keysWithFlicks: List<KeyWithFlicks>,
    /**
     * Layout-level SpacerItems (visual gaps that occupy grid cells but
     * don't render content). Stored in `spacer_definitions` and joined by
     * Room via the [SpacerDefinition.ownerLayoutId] foreign key.
     */
    @Relation(
        parentColumn = "layoutId",
        entityColumn = "ownerLayoutId"
    )
    val spacers: List<SpacerDefinition> = emptyList()
)
