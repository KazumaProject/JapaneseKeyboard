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
    val keysWithFlicks: List<KeyWithFlicks>
)
