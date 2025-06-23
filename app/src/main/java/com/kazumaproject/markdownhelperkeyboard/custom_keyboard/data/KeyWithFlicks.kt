package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data

import androidx.room.Embedded
import androidx.room.Relation

/**
 * KeyDefinition とそれに関連する FlickMapping をまとめて保持するクラス
 */
data class KeyWithFlicks(
    @Embedded val key: KeyDefinition,
    @Relation(
        parentColumn = "keyId",
        entity = FlickMapping::class,
        entityColumn = "ownerKeyId"
    )
    val flicks: List<FlickMapping>
)
