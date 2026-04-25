package com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut

enum class PhysicalKeyboardShortcutContext(val id: String, val label: String) {
    ANY("any", "Any"),
    COMPOSITION("composition", "Composition"),
    CONVERSION("conversion", "Conversion"),
    BUNSETSU_CONVERSION("bunsetsu_conversion", "Bunsetsu conversion");

    companion object {
        fun fromId(id: String?): PhysicalKeyboardShortcutContext {
            return entries.firstOrNull { it.id == id } ?: ANY
        }
    }
}
