package com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "physical_keyboard_shortcut_items",
    indices = [
        Index(
            value = ["context", "keyCode", "scanCode", "ctrl", "shift", "alt", "meta"],
            unique = true
        )
    ]
)
data class PhysicalKeyboardShortcutItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val context: String = "any",
    val keyCode: Int,
    val scanCode: Int? = null,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val meta: Boolean = false,
    val actionId: String,
    val enabled: Boolean = true,
    val sortOrder: Int = 0
)
