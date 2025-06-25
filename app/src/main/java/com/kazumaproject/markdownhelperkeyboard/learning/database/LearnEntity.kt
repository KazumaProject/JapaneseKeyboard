package com.kazumaproject.markdownhelperkeyboard.learning.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "learn_table",
    indices = [Index(value = ["input"], unique = false)]
)
data class LearnEntity(
    val input: String,
    val out: String,
    val score: Short = 3000,
    val leftId: Short? = null,
    val rightId: Short? = null,
    @PrimaryKey(autoGenerate = true)
    val id: Int? = null
)
