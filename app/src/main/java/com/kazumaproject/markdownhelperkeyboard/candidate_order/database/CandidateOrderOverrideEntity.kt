package com.kazumaproject.markdownhelperkeyboard.candidate_order.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "candidate_order_override",
    indices = [
        Index(value = ["input"]),
        Index(value = ["input", "scope"]),
        Index(value = ["input", "scope", "candidate"], unique = true)
    ]
)
data class CandidateOrderOverrideEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val input: String,
    val scope: String = "EXACT_INPUT",
    val candidate: String,
    val rank: Int,
    val createdAt: Long,
    val updatedAt: Long
)
