package com.kazumaproject.markdownhelperkeyboard.system_user_dictionary

data class IdDefEntry(
    val id: Int,
    val label: String,
) {
    val displayText: String = "$id $label"
}
