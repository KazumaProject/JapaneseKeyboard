package com.kazumaproject.data.symbol

enum class SymbolCategory {

    /** Brackets and Quotes: All forms of parentheses, brackets, and quotation marks. */
    BRACKETS_AND_QUOTES,

    /** Punctuation and Diacritics: Punctuation, separators, and standalone diacritical marks. */
    PUNCTUATION_AND_DIACRITICS,
    
    Hankaku,

    /** General Symbols: Miscellaneous symbols and iteration marks that don't fit other categories. */
    GENERAL,

    /** Arrows: All arrow symbols indicating direction. */
    ARROWS,

    /** Math and Units: Mathematical operators, currency symbols, and units of measurement. */
    MATH_AND_UNITS,

    /** Geometric Shapes: Geometric shapes like circles, squares, and triangles. */
    GEOMETRIC_SHAPES,

    /** Latin Alphabet: Latin alphabet characters, including full-width and accented versions. */
    ALPHABET_LATIN,

    /** Greek Alphabet: Characters from the Greek alphabet. */
    ALPHABET_GREEK,

    /** Cyrillic Alphabet: Characters from the Cyrillic alphabet. */
    ALPHABET_CYRILLIC,

    /** Box Drawing: Characters used for drawing boxes and tables. */
    BOX_DRAWING,

    /** Pictographs and Icons: Emoji, weather symbols, game pieces, and other ideograms. */
    PICTOGRAPHS_AND_ICONS,

    /** Roman Numerals: Symbols representing Roman numerals. */
    ROMAN_NUMERALS,

    /** Enclosed Characters: Numbers or letters enclosed in circles or parentheses. */
    ENCLOSED_CHARACTERS,

    /** Phonetic Symbols: Symbols representing phonetic sounds, such as from the IPA. */
    PHONETIC_SYMBOLS,

    /** Japanese Kana and Variants: Special kana forms (small, hentaigana, Ainu) and related symbols. */
    JAPANESE_KANA_AND_VARIANTS,

    /** CJK and Radicals: Kanji characters, radicals, and CJK compatibility characters. */
    CJK_AND_RADICALS,

    /** Control Characters: Pictorial representations of control characters. */
    CONTROL_CHARACTERS
}

