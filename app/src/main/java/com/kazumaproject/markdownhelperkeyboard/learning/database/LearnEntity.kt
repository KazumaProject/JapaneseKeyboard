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
    @PrimaryKey(autoGenerate = true)
    val id: Int? = null
)
