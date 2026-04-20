package com.kazumaproject.markdownhelperkeyboard.delete_key_flick.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "delete_key_flick_delete_targets",
    indices = [Index(value = ["symbol"], unique = true)]
)
data class DeleteKeyFlickDeleteTarget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val symbol: String,
    val sortOrder: Int
)
