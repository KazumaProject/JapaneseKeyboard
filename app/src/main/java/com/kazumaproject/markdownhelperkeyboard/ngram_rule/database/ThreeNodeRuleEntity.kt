package com.kazumaproject.markdownhelperkeyboard.ngram_rule.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "three_node_rule",
    indices = [
        Index(
            value = [
                "firstWord",
                "firstLeftId",
                "firstRightId",
                "secondWord",
                "secondLeftId",
                "secondRightId",
                "thirdWord",
                "thirdLeftId",
                "thirdRightId",
            ],
            unique = true,
        ),
    ],
)
data class ThreeNodeRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val firstWord: String,
    val firstLeftId: Int,
    val firstRightId: Int,
    val secondWord: String,
    val secondLeftId: Int,
    val secondRightId: Int,
    val thirdWord: String,
    val thirdLeftId: Int,
    val thirdRightId: Int,
    val adjustment: Int,
)

