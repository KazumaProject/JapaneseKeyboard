package com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "sumire_special_key_action_overrides",
    primaryKeys = ["layout_type", "input_mode", "key_id", "direction"]
)
data class SumireSpecialKeyActionOverrideEntity(
    @ColumnInfo(name = "layout_type") val layoutType: String,
    @ColumnInfo(name = "input_mode") val inputMode: String,
    @ColumnInfo(name = "key_id") val keyId: String,
    @ColumnInfo(name = "direction") val direction: String,
    @ColumnInfo(name = "override_type") val overrideType: String,
    @ColumnInfo(name = "action_string") val actionString: String?,
    @ColumnInfo(name = "input_text") val inputText: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

