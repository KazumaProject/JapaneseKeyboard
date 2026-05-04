package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SpacerItem (KeyboardLayout の中で空き領域を占める「描画されないキー」)
 * を永続化するためのエンティティ。
 *
 * KeyDefinition と並列にレイアウトに紐づく。
 *
 * 行内 Spacer (Shift と文字キーの間など) も先頭 Spacer も、すべてここに保存される。
 */
@Entity(
    tableName = "spacer_definitions",
    foreignKeys = [ForeignKey(
        entity = CustomKeyboardLayout::class,
        parentColumns = ["layoutId"],
        childColumns = ["ownerLayoutId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["ownerLayoutId"])]
)
data class SpacerDefinition(
    @PrimaryKey(autoGenerate = true)
    val spacerId: Long = 0,
    val ownerLayoutId: Long,
    /** stable id used in KeyboardLayoutItem.id (helps debugging / round-trip). */
    val itemIdentifier: String,
    val rowUnits: Int,
    val columnUnits: Int,
    val rowSpanUnits: Int,
    val columnSpanUnits: Int,
    val sortOrder: Int = 0
)
