package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection

@Entity(
    tableName = "two_step_long_press_mappings",
    primaryKeys = ["ownerKeyId", "firstDirection", "secondDirection"],
    foreignKeys = [ForeignKey(
        entity = KeyDefinition::class,
        parentColumns = ["keyId"],
        childColumns = ["ownerKeyId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["ownerKeyId"])
    ]
)
data class TwoStepLongPressMappingEntity(
    val ownerKeyId: Long,
    val firstDirection: TfbiFlickDirection,
    val secondDirection: TfbiFlickDirection,
    val output: String
)
