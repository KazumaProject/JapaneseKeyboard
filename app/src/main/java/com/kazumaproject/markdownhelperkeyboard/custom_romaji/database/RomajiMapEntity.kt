package com.kazumaproject.markdownhelperkeyboard.custom_romaji.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "romaji_maps")
data class RomajiMapEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val mapData: Map<String, Pair<String, Int>>,
    val isActive: Boolean = false,
    val isDeletable: Boolean = true
)
