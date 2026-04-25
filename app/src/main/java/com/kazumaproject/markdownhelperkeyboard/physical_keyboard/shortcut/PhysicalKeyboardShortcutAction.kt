package com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut

enum class PhysicalKeyboardShortcutAction(
    val id: String,
    val label: String,
    val allowedContexts: Set<PhysicalKeyboardShortcutContext>
) {
    COPY("copy", "Copy", setOf(PhysicalKeyboardShortcutContext.ANY)),
    PASTE("paste", "Paste", setOf(PhysicalKeyboardShortcutContext.ANY)),
    CUT("cut", "Cut", setOf(PhysicalKeyboardShortcutContext.ANY)),
    SELECT_ALL("select_all", "Select all", setOf(PhysicalKeyboardShortcutContext.ANY)),
    SWITCH_TO_JAPANESE("switch_to_japanese", "Switch to Japanese", setOf(PhysicalKeyboardShortcutContext.ANY)),
    SWITCH_TO_ENGLISH(
        "switch_to_english",
        "Switch to English",
        setOf(PhysicalKeyboardShortcutContext.ANY, PhysicalKeyboardShortcutContext.COMPOSITION)
    ),
    CYCLE_INPUT_MODE("cycle_input_mode", "Cycle input mode", setOf(PhysicalKeyboardShortcutContext.ANY, PhysicalKeyboardShortcutContext.COMPOSITION)),
    CONVERT("convert", "Convert", setOf(PhysicalKeyboardShortcutContext.COMPOSITION)),
    COMMIT(
        "commit",
        "Commit",
        setOf(PhysicalKeyboardShortcutContext.COMPOSITION, PhysicalKeyboardShortcutContext.CONVERSION, PhysicalKeyboardShortcutContext.BUNSETSU_CONVERSION)
    ),
    CANCEL(
        "cancel",
        "Cancel",
        setOf(PhysicalKeyboardShortcutContext.COMPOSITION, PhysicalKeyboardShortcutContext.CONVERSION, PhysicalKeyboardShortcutContext.BUNSETSU_CONVERSION)
    ),
    CONVERT_TO_HIRAGANA("convert_to_hiragana", "Convert to hiragana", setOf(PhysicalKeyboardShortcutContext.COMPOSITION)),
    CONVERT_TO_FULL_KATAKANA("convert_to_full_katakana", "Convert to full katakana", setOf(PhysicalKeyboardShortcutContext.COMPOSITION)),
    CONVERT_TO_HALF_WIDTH("convert_to_half_width", "Convert to half width", setOf(PhysicalKeyboardShortcutContext.COMPOSITION)),
    CONVERT_TO_FULL_ALPHANUMERIC("convert_to_full_alphanumeric", "Convert to full alphanumeric", setOf(PhysicalKeyboardShortcutContext.COMPOSITION)),
    CONVERT_TO_HALF_ALPHANUMERIC("convert_to_half_alphanumeric", "Convert to half alphanumeric", setOf(PhysicalKeyboardShortcutContext.COMPOSITION)),
    CONVERT_NEXT("convert_next", "Next candidate", setOf(PhysicalKeyboardShortcutContext.CONVERSION, PhysicalKeyboardShortcutContext.BUNSETSU_CONVERSION)),
    CONVERT_PREV("convert_prev", "Previous candidate", setOf(PhysicalKeyboardShortcutContext.CONVERSION, PhysicalKeyboardShortcutContext.BUNSETSU_CONVERSION)),
    SEGMENT_FOCUS_LEFT("segment_focus_left", "Move segment left", setOf(PhysicalKeyboardShortcutContext.BUNSETSU_CONVERSION)),
    SEGMENT_FOCUS_RIGHT("segment_focus_right", "Move segment right", setOf(PhysicalKeyboardShortcutContext.BUNSETSU_CONVERSION)),
    SEGMENT_WIDTH_SHRINK("segment_width_shrink", "Shrink segment", setOf(PhysicalKeyboardShortcutContext.BUNSETSU_CONVERSION)),
    SEGMENT_WIDTH_EXPAND("segment_width_expand", "Expand segment", setOf(PhysicalKeyboardShortcutContext.BUNSETSU_CONVERSION));

    fun isAllowedIn(context: PhysicalKeyboardShortcutContext): Boolean {
        return allowedContexts.contains(context)
    }

    companion object {
        fun fromId(id: String?): PhysicalKeyboardShortcutAction? {
            return entries.firstOrNull { it.id == id }
        }

        fun availableFor(context: PhysicalKeyboardShortcutContext): List<PhysicalKeyboardShortcutAction> {
            return entries.filter { it.isAllowedIn(context) }
        }
    }
}
