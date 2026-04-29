package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data

import androidx.room.Entity
import androidx.room.ForeignKey
import com.kazumaproject.custom_keyboard.data.CircularFlickDirection

@Entity(
    tableName = "circular_flick_mappings",
    primaryKeys = ["ownerKeyId", "stateIndex", "circularDirection"],
    foreignKeys = [ForeignKey(
        entity = KeyDefinition::class,
        parentColumns = ["keyId"],
        childColumns = ["ownerKeyId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class CircularFlickMapping(
    val ownerKeyId: Long,
    val stateIndex: Int = 0,
    val circularDirection: CircularFlickDirection,
    val actionType: String,
    val actionValue: String?
)
