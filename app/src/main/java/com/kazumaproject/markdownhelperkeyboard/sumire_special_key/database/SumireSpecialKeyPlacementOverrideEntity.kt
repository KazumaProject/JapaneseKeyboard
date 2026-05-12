package com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "sumire_special_key_placement_overrides",
    primaryKeys = ["layout_type", "input_mode", "key_id"]
)
data class SumireSpecialKeyPlacementOverrideEntity(
    @ColumnInfo(name = "layout_type") val layoutType: String,
    @ColumnInfo(name = "input_mode") val inputMode: String,
    @ColumnInfo(name = "key_id") val keyId: String,
    @ColumnInfo(name = "row_units") val rowUnits: Int,
    @ColumnInfo(name = "column_units") val columnUnits: Int,
    @ColumnInfo(name = "row_span_units") val rowSpanUnits: Int,
    @ColumnInfo(name = "column_span_units") val columnSpanUnits: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

