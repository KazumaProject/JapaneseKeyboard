package com.kazumaproject.domain

object EmojiSkinToneSupport {
    private const val VARIATION_SELECTOR_16 = 0xFE0F
    private val skinToneModifiers = listOf(
        0x1F3FB,
        0x1F3FC,
        0x1F3FD,
        0x1F3FE,
        0x1F3FF
    )

    fun hasSkinToneVariants(symbol: String): Boolean {
        return countSkinToneBases(removeSkinToneModifiers(symbol)) == 1
    }

    fun skinToneVariants(symbol: String): List<String> {
        val baseSymbol = removeSkinToneModifiers(symbol)
        if (!hasSkinToneVariants(baseSymbol)) return emptyList()

        return buildList {
            add(baseSymbol)
            skinToneModifiers.forEach { tone ->
                add(applySkinTone(baseSymbol, tone))
            }
        }
    }

    fun removeSkinToneModifiers(symbol: String): String {
        val builder = StringBuilder()
        var index = 0
        while (index < symbol.length) {
            val codePoint = symbol.codePointAt(index)
            if (!isSkinToneModifier(codePoint)) {
                builder.appendCodePoint(codePoint)
            }
            index += Character.charCount(codePoint)
        }
        return builder.toString()
    }

    private fun applySkinTone(symbol: String, skinTone: Int): String {
        val builder = StringBuilder()
        var index = 0
        var applied = false

        while (index < symbol.length) {
            val codePoint = symbol.codePointAt(index)
            val nextIndex = index + Character.charCount(codePoint)

            if (!applied && isSkinToneBase(codePoint)) {
                builder.appendCodePoint(codePoint)
                builder.appendCodePoint(skinTone)
                applied = true

                index = if (
                    nextIndex < symbol.length &&
                    symbol.codePointAt(nextIndex) == VARIATION_SELECTOR_16
                ) {
                    nextIndex + Character.charCount(VARIATION_SELECTOR_16)
                } else {
                    nextIndex
                }
                continue
            }

            builder.appendCodePoint(codePoint)
            index = nextIndex
        }

        return builder.toString()
    }

    private fun countSkinToneBases(symbol: String): Int {
        var count = 0
        var index = 0
        while (index < symbol.length) {
            val codePoint = symbol.codePointAt(index)
            if (isSkinToneBase(codePoint)) count += 1
            index += Character.charCount(codePoint)
        }
        return count
    }

    private fun isSkinToneModifier(codePoint: Int): Boolean {
        return codePoint in 0x1F3FB..0x1F3FF
    }

    private fun isSkinToneBase(codePoint: Int): Boolean {
        return when (codePoint) {
            0x261D,
            0x26F9,
            in 0x270A..0x270D,
            0x1F385,
            in 0x1F3C2..0x1F3C4,
            0x1F3C7,
            in 0x1F3CA..0x1F3CC,
            in 0x1F442..0x1F443,
            in 0x1F446..0x1F450,
            in 0x1F466..0x1F469,
            in 0x1F46E..0x1F478,
            0x1F47C,
            in 0x1F481..0x1F483,
            in 0x1F485..0x1F487,
            0x1F4AA,
            0x1F574,
            0x1F575,
            0x1F57A,
            0x1F590,
            in 0x1F595..0x1F596,
            in 0x1F645..0x1F647,
            in 0x1F64B..0x1F64F,
            0x1F6A3,
            in 0x1F6B4..0x1F6B6,
            0x1F6C0,
            0x1F6CC,
            0x1F90C,
            0x1F90F,
            in 0x1F918..0x1F91F,
            0x1F926,
            in 0x1F930..0x1F939,
            in 0x1F93C..0x1F93E,
            0x1F977,
            in 0x1F9B5..0x1F9B6,
            in 0x1F9B8..0x1F9B9,
            0x1F9BB,
            in 0x1F9CD..0x1F9CF,
            in 0x1FAF0..0x1FAF8 -> true

            else -> false
        }
    }
}
