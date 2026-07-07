package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.kazumaproject.custom_keyboard.data.FlickDirection

@Entity(
    tableName = "long_press_flick_mappings",
    primaryKeys = ["ownerKeyId", "flickDirection"],
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
data class LongPressFlickMapping(
    val ownerKeyId: Long,
    val flickDirection: FlickDirection,
    val output: String
)
