package com.kazumaproject.markdownhelperkeyboard.ngram_rule.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "two_node_rule",
    indices = [
        Index(
            value = [
                "prevWord",
                "prevLeftId",
                "prevRightId",
                "currentWord",
                "currentLeftId",
                "currentRightId",
            ],
            unique = true,
        ),
    ],
)
data class TwoNodeRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val prevWord: String,
    val prevLeftId: Int,
    val prevRightId: Int,
    val currentWord: String,
    val currentLeftId: Int,
    val currentRightId: Int,
    val adjustment: Int,
)

