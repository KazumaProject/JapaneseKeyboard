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
    val flicks: List<FlickMapping>,

    @Relation(
        parentColumn = "keyId",
        entity = TwoStepFlickMapping::class,
        entityColumn = "ownerKeyId"
    )
    val twoStepFlicks: List<TwoStepFlickMapping>,

    @Relation(
        parentColumn = "keyId",
        entity = LongPressFlickMapping::class,
        entityColumn = "ownerKeyId"
    )
    val longPressFlicks: List<LongPressFlickMapping>,

    @Relation(
        parentColumn = "keyId",
        entity = TwoStepLongPressMappingEntity::class,
        entityColumn = "ownerKeyId"
    )
    val twoStepLongPressFlicks: List<TwoStepLongPressMappingEntity>
)
