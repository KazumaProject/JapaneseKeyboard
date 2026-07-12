package com.kazumaproject.markdownhelperkeyboard.ngram_rule.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ngram_rule",
    indices = [
        Index(value = ["nodeCount", "id"]),
        Index(
            value = [
                "nodeCount",
                "node1Word", "node1LeftId", "node1RightId",
                "node2Word", "node2LeftId", "node2RightId",
                "node3Word", "node3LeftId", "node3RightId",
                "node4Word", "node4LeftId", "node4RightId",
                "node5Word", "node5LeftId", "node5RightId",
            ],
            unique = true,
        ),
    ],
)
data class NgramRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nodeCount: Int,
    val node1Word: String,
    val node1LeftId: Int,
    val node1RightId: Int,
    val node2Word: String,
    val node2LeftId: Int,
    val node2RightId: Int,
    val node3Word: String = "",
    val node3LeftId: Int = -1,
    val node3RightId: Int = -1,
    val node4Word: String = "",
    val node4LeftId: Int = -1,
    val node4RightId: Int = -1,
    val node5Word: String = "",
    val node5LeftId: Int = -1,
    val node5RightId: Int = -1,
    val adjustment: Int,
)
