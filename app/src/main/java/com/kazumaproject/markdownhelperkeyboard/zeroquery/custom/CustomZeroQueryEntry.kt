package com.kazumaproject.markdownhelperkeyboard.zeroquery.custom

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "custom_zero_query_entries",
    indices = [
        Index(value = ["lookupKey", "enabled"]),
        Index(value = ["lookupKey", "candidate"], unique = true),
    ]
)
data class CustomZeroQueryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val lookupKey: String,
    val displayKey: String,
    val candidate: String,
    val rank: Int,
    val enabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)
